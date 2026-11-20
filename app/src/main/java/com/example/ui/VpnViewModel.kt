package com.example.ui

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.billing.BillingUiEvent
import com.example.billing.PlayBillingManager
import com.example.data.model.AppPreferences
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SupportTicketRequest
import com.example.data.model.TunnelMetrics
import com.example.data.model.TunnelState
import com.example.data.model.VpnProtocolOption
import com.example.data.model.VpnServer
import com.example.data.repository.BackendConnectionStatus
import com.example.data.repository.VpnRepository
import com.example.vpn.SbrVpnManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SbrUiState(
    val preferences: AppPreferences = AppPreferences(),
    val tunnelState: TunnelState = TunnelState.Disconnected,
    val metrics: TunnelMetrics = TunnelMetrics(),
    val servers: List<VpnServer> = emptyList(),
    val backendStatus: BackendConnectionStatus = BackendConnectionStatus.Unconfigured,
    val publicIp: String? = null,
    val plans: List<SubscriptionPlan> = emptyList(),
    val importedProfileLabel: String? = null,
    val isLoadingServers: Boolean = false,
    val isAuthBusy: Boolean = false,
    val isBillingReady: Boolean = false
) {
    val selectedServer: VpnServer?
        get() = servers.firstOrNull { it.id == preferences.selectedServerId }
            ?: servers.firstOrNull()
}

sealed interface UiOneShotEvent {
    data class RequestVpnPermission(val intent: Intent) : UiOneShotEvent
    data class ShowSnackbar(val message: String) : UiOneShotEvent
}

class VpnViewModel(
    private val repository: VpnRepository,
    private val vpnManager: SbrVpnManager,
    private val billingManager: PlayBillingManager
) : ViewModel() {

    private val isLoadingServers = MutableStateFlow(false)
    private val isAuthBusy = MutableStateFlow(false)

    private val _events = MutableSharedFlow<UiOneShotEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<SbrUiState> = combine(
        repository.preferences,
        repository.tunnelState,
        repository.tunnelMetrics,
        repository.servers,
        repository.backendStatus,
        repository.publicIp,
        repository.plans,
        repository.importedProfileLabel,
        isLoadingServers,
        isAuthBusy,
        billingManager.isBillingReady
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SbrUiState(
            preferences = values[0] as AppPreferences,
            tunnelState = values[1] as TunnelState,
            metrics = values[2] as TunnelMetrics,
            servers = values[3] as List<VpnServer>,
            backendStatus = values[4] as BackendConnectionStatus,
            publicIp = values[5] as String?,
            plans = values[6] as List<SubscriptionPlan>,
            importedProfileLabel = values[7] as String?,
            isLoadingServers = values[8] as Boolean,
            isAuthBusy = values[9] as Boolean,
            isBillingReady = values[10] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SbrUiState()
    )

    init {
        viewModelScope.launch {
            repository.initialize()
            val plansRes = repository.refreshPlans().getOrNull().orEmpty()
            billingManager.startConnection(plansRes.map { it.playProductId })
        }
        viewModelScope.launch {
            billingManager.lastEvent.collect { event ->
                when (event) {
                    is BillingUiEvent.PurchaseVerifiedNeeded -> {
                        repository.verifyPlaySubscription(event.productId, event.purchaseToken)
                            .onSuccess {
                                _events.emit(UiOneShotEvent.ShowSnackbar("Premium subscription activated!"))
                            }
                            .onFailure { err ->
                                _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Verification failed"))
                            }
                        billingManager.clearLastEvent()
                    }
                    is BillingUiEvent.Message -> {
                        _events.emit(UiOneShotEvent.ShowSnackbar(event.text))
                        billingManager.clearLastEvent()
                    }
                    null -> Unit
                }
            }
        }
    }

    fun onToggleVpnConnection() {
        viewModelScope.launch {
            when (uiState.value.tunnelState) {
                is TunnelState.Connected,
                is TunnelState.Connecting,
                is TunnelState.Reconnecting -> {
                    repository.disconnect()
                }
                is TunnelState.Disconnected,
                is TunnelState.Error -> {
                    val permIntent = vpnManager.prepareVpnPermission()
                    if (permIntent != null) {
                        _events.emit(UiOneShotEvent.RequestVpnPermission(permIntent))
                    } else {
                        connectAfterPermissionGranted()
                    }
                }
            }
        }
    }

    fun connectAfterPermissionGranted() {
        viewModelScope.launch {
            repository.connectToSelectedServer().onFailure { err ->
                _events.emit(
                    UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Unable to establish VPN tunnel.")
                )
            }
        }
    }

    fun connectImportedProfile() {
        viewModelScope.launch {
            val permIntent = vpnManager.prepareVpnPermission()
            if (permIntent != null) {
                _events.emit(UiOneShotEvent.RequestVpnPermission(permIntent))
            } else {
                repository.connectImportedConfig().onFailure { err ->
                    _events.emit(
                        UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Failed to start imported WireGuard profile.")
                    )
                }
            }
        }
    }

    fun reconnectVpn() {
        viewModelScope.launch {
            repository.reconnect().onFailure { err ->
                _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Reconnect failed."))
            }
        }
    }

    fun disconnectVpn() {
        viewModelScope.launch { repository.disconnect() }
    }

    fun refreshServers() {
        viewModelScope.launch {
            isLoadingServers.value = true
            repository.refreshServers()
            repository.refreshPublicIp()
            isLoadingServers.value = false
        }
    }

    fun selectServer(server: VpnServer) {
        viewModelScope.launch {
            repository.selectServer(server.id)
            if (uiState.value.tunnelState is TunnelState.Connected) {
                repository.disconnect()
                val permIntent = vpnManager.prepareVpnPermission()
                if (permIntent != null) {
                    _events.emit(UiOneShotEvent.RequestVpnPermission(permIntent))
                } else {
                    connectAfterPermissionGranted()
                }
            }
        }
    }

    fun autoSelectFastest() {
        viewModelScope.launch {
            val fastest = repository.autoSelectFastestServer()
            if (fastest != null) {
                _events.emit(UiOneShotEvent.ShowSnackbar("Selected fastest node: ${fastest.countryName} (${fastest.city})"))
            } else {
                _events.emit(UiOneShotEvent.ShowSnackbar("No reachable servers found. Configure backend URL first."))
            }
        }
    }

    fun toggleFavoriteServer(serverId: String) {
        viewModelScope.launch { repository.toggleFavorite(serverId) }
    }

    fun importWireGuardConfig(label: String, rawText: String) {
        viewModelScope.launch {
            repository.saveImportedWireGuardConfig(label, rawText)
                .onSuccess {
                    _events.emit(UiOneShotEvent.ShowSnackbar("Imported WireGuard profile: $label"))
                }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar("Invalid WireGuard config: ${err.localizedMessage}"))
                }
        }
    }

    fun clearImportedWireGuardConfig() {
        viewModelScope.launch {
            repository.clearImportedWireGuardConfig()
            _events.emit(UiOneShotEvent.ShowSnackbar("Removed imported WireGuard profile"))
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted() }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoConnect(enabled) }
    }

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch { repository.setKillSwitch(enabled) }
    }

    fun setConnectOnWifi(enabled: Boolean) {
        viewModelScope.launch { repository.setConnectOnWifi(enabled) }
    }

    fun setConnectOnMobileData(enabled: Boolean) {
        viewModelScope.launch { repository.setConnectOnMobileData(enabled) }
    }

    fun setPreferredProtocol(protocol: VpnProtocolOption) {
        viewModelScope.launch { repository.setPreferredProtocol(protocol) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    fun setLanguageCode(code: String) {
        viewModelScope.launch { repository.setLanguageCode(code) }
    }

    fun updateApiBaseUrl(url: String) {
        viewModelScope.launch {
            repository.setApiBaseUrl(url)
                .onSuccess {
                    _events.emit(UiOneShotEvent.ShowSnackbar("Backend endpoint updated"))
                }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Invalid API URL"))
                }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            isAuthBusy.value = true
            repository.signInAsGuest()
                .onSuccess { _events.emit(UiOneShotEvent.ShowSnackbar("Signed in as Guest")) }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Guest session failed"))
                }
            isAuthBusy.value = false
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            isAuthBusy.value = true
            repository.register(email, password, displayName)
                .onSuccess { _events.emit(UiOneShotEvent.ShowSnackbar("Account created!")) }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Registration failed"))
                }
            isAuthBusy.value = false
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            isAuthBusy.value = true
            repository.login(email, password)
                .onSuccess { _events.emit(UiOneShotEvent.ShowSnackbar("Welcome back!")) }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Login failed"))
                }
            isAuthBusy.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _events.emit(UiOneShotEvent.ShowSnackbar("Logged out securely"))
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch {
            isAuthBusy.value = true
            repository.requestPasswordReset(email)
                .onSuccess { msg -> _events.emit(UiOneShotEvent.ShowSnackbar(msg)) }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Reset request failed"))
                }
            isAuthBusy.value = false
        }
    }

    fun confirmPasswordReset(email: String, token: String, newPassword: String) {
        viewModelScope.launch {
            isAuthBusy.value = true
            repository.confirmPasswordReset(email, token, newPassword)
                .onSuccess { msg -> _events.emit(UiOneShotEvent.ShowSnackbar(msg)) }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Reset confirmation failed"))
                }
            isAuthBusy.value = false
        }
    }

    fun launchPlaySubscription(activity: Activity, productId: String) {
        billingManager.launchSubscriptionFlow(activity, productId).onFailure { err ->
            viewModelScope.launch {
                _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Play Billing unavailable"))
            }
        }
    }

    fun submitSupportTicket(subject: String, category: String, message: String) {
        viewModelScope.launch {
            val req = SupportTicketRequest(
                subject = subject,
                category = category,
                message = message,
                appVersion = "1.0.0",
                androidVersion = "Android ${android.os.Build.VERSION.RELEASE}"
            )
            repository.submitSupportTicket(req)
                .onSuccess { res ->
                    _events.emit(UiOneShotEvent.ShowSnackbar("Support ticket #${res.ticketId.take(8)} submitted"))
                }
                .onFailure { err ->
                    _events.emit(UiOneShotEvent.ShowSnackbar(err.localizedMessage ?: "Unable to submit ticket"))
                }
        }
    }

    class Factory(
        private val repository: VpnRepository,
        private val vpnManager: SbrVpnManager,
        private val billingManager: PlayBillingManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VpnViewModel(repository, vpnManager, billingManager) as T
        }
    }
}
