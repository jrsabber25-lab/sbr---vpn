package com.example.data.repository

import com.example.data.local.SettingsDataStore
import com.example.data.model.AuthRequest
import com.example.data.model.GuestAuthRequest
import com.example.data.model.PasswordResetConfirm
import com.example.data.model.PasswordResetRequest
import com.example.data.model.PlayVerifyRequest
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SupportTicketRequest
import com.example.data.model.SupportTicketResponse
import com.example.data.model.TunnelMetrics
import com.example.data.model.TunnelState
import com.example.data.model.UserProfile
import com.example.data.model.VpnConnectRequest
import com.example.data.model.VpnDisconnectRequest
import com.example.data.model.VpnProtocolOption
import com.example.data.model.VpnServer
import com.example.data.remote.SbrApiClient
import com.example.vpn.SbrVpnManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

sealed interface BackendConnectionStatus {
    data object Unconfigured : BackendConnectionStatus
    data object Checking : BackendConnectionStatus
    data class Reachable(val serverCount: Int) : BackendConnectionStatus
    data class Unreachable(val error: String) : BackendConnectionStatus
}

class VpnRepository(
    private val apiClient: SbrApiClient,
    private val settingsDataStore: SettingsDataStore,
    private val vpnManager: SbrVpnManager
) {
    val preferences = settingsDataStore.preferencesFlow
    val tunnelState: StateFlow<TunnelState> = vpnManager.tunnelState
    val tunnelMetrics: StateFlow<TunnelMetrics> = vpnManager.metrics

    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _backendStatus = MutableStateFlow<BackendConnectionStatus>(BackendConnectionStatus.Unconfigured)
    val backendStatus: StateFlow<BackendConnectionStatus> = _backendStatus.asStateFlow()

    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp: StateFlow<String?> = _publicIp.asStateFlow()

    private val _plans = MutableStateFlow<List<SubscriptionPlan>>(emptyList())
    val plans: StateFlow<List<SubscriptionPlan>> = _plans.asStateFlow()

    private val _importedProfileLabel = MutableStateFlow<String?>(null)
    val importedProfileLabel: StateFlow<String?> = _importedProfileLabel.asStateFlow()

    suspend fun initialize() {
        vpnManager.getOrCreateClientKeyPair()
        val imported = settingsDataStore.getImportedWireGuardConfig()
        _importedProfileLabel.value = imported?.first
        refreshPublicIp()
        refreshServers()
    }

    suspend fun refreshPublicIp() {
        val service = apiClient.getService().getOrNull()
        _publicIp.value = apiClient.resolveRealPublicIp(service)
    }

    suspend fun refreshServers(): Result<List<VpnServer>> {
        val prefs = settingsDataStore.preferencesFlow.first()
        if (prefs.apiBaseUrl.isBlank()) {
            _servers.value = emptyList()
            _backendStatus.value = BackendConnectionStatus.Unconfigured
            return Result.failure(IllegalStateException("Backend URL not configured"))
        }

        _backendStatus.value = BackendConnectionStatus.Checking
        val service = apiClient.getService().getOrElse { err ->
            _servers.value = emptyList()
            _backendStatus.value = BackendConnectionStatus.Unreachable(err.localizedMessage ?: "Invalid backend URL")
            return Result.failure(err)
        }

        return runCatching {
            val remoteServers = service.getServers().filter { it.isOnline }
            val withMeasuredLatency = coroutineScope {
                remoteServers.map { server ->
                    async {
                        val rtt = apiClient.measureTcpLatencyMs(server.hostname, server.healthPort)
                        server.copy(measuredLatencyMs = rtt)
                    }
                }.awaitAll()
            }
            _servers.value = withMeasuredLatency
            _backendStatus.value = BackendConnectionStatus.Reachable(withMeasuredLatency.size)
            withMeasuredLatency
        }.onFailure { err ->
            _servers.value = emptyList()
            _backendStatus.value = BackendConnectionStatus.Unreachable(
                err.localizedMessage ?: "Unable to reach SBR VPN backend API"
            )
        }
    }

    suspend fun autoSelectFastestServer(): VpnServer? {
        val current = _servers.value.ifEmpty {
            refreshServers().getOrNull().orEmpty()
        }
        val prefs = settingsDataStore.preferencesFlow.first()
        val isPremiumUser = prefs.subscriptionTier.equals("premium", ignoreCase = true)
        val eligible = current.filter { it.isOnline && (isPremiumUser || !it.isPremium) }
        val fastest = eligible
            .filter { it.measuredLatencyMs != null }
            .minByOrNull { (it.measuredLatencyMs ?: Int.MAX_VALUE) + (it.loadPercent * 2) }
            ?: eligible.minByOrNull { it.loadPercent }

        if (fastest != null) {
            settingsDataStore.selectServer(fastest.id)
        }
        return fastest
    }

    suspend fun connectToSelectedServer(): Result<Unit> {
        val prefs = settingsDataStore.preferencesFlow.first()
        if (!vpnManager.isCurrentNetworkAllowed(prefs)) {
            return Result.failure(
                IllegalStateException("Current network type is disabled in Settings (Wi-Fi / Mobile Data rules).")
            )
        }

        val serverList = _servers.value
        val selected = serverList.firstOrNull { it.id == prefs.selectedServerId }
            ?: autoSelectFastestServer()

        if (selected == null) {
            // Check if user imported a standalone WireGuard .conf file for direct VPS testing
            val imported = settingsDataStore.getImportedWireGuardConfig()
            if (imported != null) {
                return vpnManager.connectWithRawConfig(imported.first, imported.second).also { res ->
                    if (res.isSuccess) refreshPublicIp()
                }
            }
            return Result.failure(
                IllegalStateException("No deployed VPN server available. Configure your SBR VPN backend URL or import a WireGuard .conf profile.")
            )
        }

        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        ensureAuthenticated(service)

        val (_, publicKey) = vpnManager.getOrCreateClientKeyPair()
        val (privateKey, _) = vpnManager.getOrCreateClientKeyPair()

        val provision = runCatching {
            service.provisionTunnel(
                VpnConnectRequest(
                    serverId = selected.id,
                    clientPublicKey = publicKey,
                    protocol = prefs.preferredProtocol.name.lowercase()
                )
            )
        }.getOrElse { return Result.failure(it) }

        return vpnManager.connectWithProvision(selected, provision, privateKey, prefs).also { res ->
            if (res.isSuccess) {
                settingsDataStore.selectServer(selected.id)
                refreshPublicIp()
            }
        }
    }

    suspend fun connectImportedConfig(): Result<Unit> {
        val imported = settingsDataStore.getImportedWireGuardConfig()
            ?: return Result.failure(IllegalStateException("No WireGuard .conf profile imported."))
        return vpnManager.connectWithRawConfig(imported.first, imported.second).also { res ->
            if (res.isSuccess) refreshPublicIp()
        }
    }

    suspend fun autoConnectIfPossible() {
        val prefs = settingsDataStore.preferencesFlow.first()
        if (!prefs.autoConnect) return
        if (vpnManager.tunnelState.value is TunnelState.Connected) return
        connectToSelectedServer()
    }

    suspend fun reconnect(): Result<Unit> {
        return vpnManager.reconnectLastTunnel().also { res ->
            if (res.isSuccess) refreshPublicIp()
        }
    }

    suspend fun disconnect() {
        val (sessionId, finalMetrics) = vpnManager.disconnect()
        if (sessionId != null) {
            apiClient.getService().getOrNull()?.let { service ->
                runCatching {
                    service.disconnectTunnel(
                        VpnDisconnectRequest(
                            sessionId = sessionId,
                            rxBytes = finalMetrics.totalRxBytes,
                            txBytes = finalMetrics.totalTxBytes
                        )
                    )
                }
            }
        }
        refreshPublicIp()
    }

    private suspend fun ensureAuthenticated(service: com.example.data.remote.SbrVpnApiService) {
        val existingToken = settingsDataStore.getDecryptedAuthToken()
        if (!existingToken.isNullOrBlank()) return
        val deviceId = settingsDataStore.getOrCreateDeviceId()
        val auth = service.guestAuth(GuestAuthRequest(deviceId = deviceId))
        settingsDataStore.saveAuthSession(auth.accessToken, auth.user)
    }

    suspend fun signInAsGuest(): Result<UserProfile> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching {
            val deviceId = settingsDataStore.getOrCreateDeviceId()
            val auth = service.guestAuth(GuestAuthRequest(deviceId))
            settingsDataStore.saveAuthSession(auth.accessToken, auth.user)
            auth.user
        }
    }

    suspend fun register(email: String, password: String, displayName: String): Result<UserProfile> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching {
            val auth = service.register(AuthRequest(email.trim(), password, displayName.trim()))
            settingsDataStore.saveAuthSession(auth.accessToken, auth.user)
            auth.user
        }
    }

    suspend fun login(email: String, password: String): Result<UserProfile> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching {
            val auth = service.login(AuthRequest(email.trim(), password))
            settingsDataStore.saveAuthSession(auth.accessToken, auth.user)
            auth.user
        }
    }

    suspend fun logout(): Result<Unit> {
        if (vpnManager.tunnelState.value !is TunnelState.Disconnected) {
            disconnect()
        }
        apiClient.getService().getOrNull()?.let { service ->
            runCatching { service.logout() }
        }
        settingsDataStore.clearAuthSession()
        return Result.success(Unit)
    }

    suspend fun requestPasswordReset(email: String): Result<String> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching { service.requestPasswordReset(PasswordResetRequest(email.trim())).message }
    }

    suspend fun confirmPasswordReset(email: String, token: String, newPassword: String): Result<String> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching {
            service.confirmPasswordReset(PasswordResetConfirm(email.trim(), token.trim(), newPassword)).message
        }
    }

    suspend fun refreshPlans(): Result<List<SubscriptionPlan>> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching {
            val remote = service.getSubscriptionPlans()
            _plans.value = remote
            remote
        }
    }

    suspend fun verifyPlaySubscription(productId: String, purchaseToken: String): Result<UserProfile> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching {
            val updated = service.verifyPlaySubscription(PlayVerifyRequest(productId, purchaseToken))
            val token = settingsDataStore.getDecryptedAuthToken().orEmpty()
            if (token.isNotBlank()) {
                settingsDataStore.saveAuthSession(token, updated)
            }
            updated
        }
    }

    suspend fun submitSupportTicket(request: SupportTicketRequest): Result<SupportTicketResponse> {
        val service = apiClient.getService().getOrElse { return Result.failure(it) }
        return runCatching { service.submitSupportTicket(request) }
    }

    suspend fun saveImportedWireGuardConfig(label: String, configText: String): Result<Unit> {
        return runCatching {
            com.example.vpn.WireGuardConfigBuilder.parseRawConfig(configText)
            settingsDataStore.saveImportedWireGuardConfig(label, configText)
            _importedProfileLabel.value = label
        }
    }

    suspend fun clearImportedWireGuardConfig() {
        settingsDataStore.clearImportedWireGuardConfig()
        _importedProfileLabel.value = null
    }

    suspend fun selectServer(serverId: String) = settingsDataStore.selectServer(serverId)
    suspend fun toggleFavorite(serverId: String) = settingsDataStore.toggleFavoriteServer(serverId)
    suspend fun setOnboardingCompleted() = settingsDataStore.setOnboardingCompleted(true)
    suspend fun setAutoConnect(enabled: Boolean) = settingsDataStore.setAutoConnect(enabled)
    suspend fun setKillSwitch(enabled: Boolean) = settingsDataStore.setKillSwitch(enabled)
    suspend fun setConnectOnWifi(enabled: Boolean) = settingsDataStore.setConnectOnWifi(enabled)
    suspend fun setConnectOnMobileData(enabled: Boolean) = settingsDataStore.setConnectOnMobileData(enabled)
    suspend fun setPreferredProtocol(protocol: VpnProtocolOption) = settingsDataStore.setPreferredProtocol(protocol)
    suspend fun setDarkMode(enabled: Boolean) = settingsDataStore.setDarkMode(enabled)
    suspend fun setLanguageCode(code: String) = settingsDataStore.setLanguageCode(code)

    suspend fun setApiBaseUrl(url: String): Result<Unit> {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && SbrApiClient.normalizeAndValidateUrl(trimmed) == null) {
            return Result.failure(IllegalArgumentException("API URL must start with https:// (http:// is allowed only for 10.0.2.2 or localhost)."))
        }
        settingsDataStore.setApiBaseUrl(trimmed)
        refreshServers()
        return Result.success(Unit)
    }
}
