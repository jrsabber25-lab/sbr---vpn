package com.example.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import com.example.data.local.SettingsDataStore
import com.example.data.model.AppPreferences
import com.example.data.model.SpeedSample
import com.example.data.model.TunnelMetrics
import com.example.data.model.TunnelState
import com.example.data.model.VpnProvisionResponse
import com.example.data.model.VpnServer
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SbrVpnManager(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val backend: GoBackend by lazy { GoBackend(context.applicationContext) }

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    private val _metrics = MutableStateFlow(TunnelMetrics())
    val metrics: StateFlow<TunnelMetrics> = _metrics.asStateFlow()

    private var activeSessionId: String? = null
    private var lastConfig: Config? = null
    private var lastServerMeta: ActiveServerMeta? = null
    private var connectTimestampMs: Long = 0L
    private var prevRxBytes: Long = 0L
    private var prevTxBytes: Long = 0L
    private var telemetryJob: Job? = null
    private var networkCallbackRegistered = false
    private var userRequestedDisconnect = false

    private data class ActiveServerMeta(
        val name: String,
        val flag: String,
        val endpoint: String,
        val assignedIp: String,
        val dnsServers: List<String>,
        val protocolLabel: String
    )

    private val tunnel = object : Tunnel {
        override fun getName(): String = "sbrwg0"

        override fun onStateChange(newState: Tunnel.State) {
            if (newState == Tunnel.State.DOWN && !userRequestedDisconnect) {
                val current = _tunnelState.value
                if (current is TunnelState.Connected || current is TunnelState.Connecting) {
                    scope.launch {
                        val prefs = settingsDataStore.preferencesFlow.first()
                        _tunnelState.value = TunnelState.Error(
                            message = "VPN tunnel closed unexpectedly.",
                            killSwitchBlocking = prefs.killSwitchEnabled
                        )
                        stopTelemetry()
                        stopMonitorService()
                    }
                }
            }
        }
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch {
                val state = _tunnelState.value
                if (state is TunnelState.Reconnecting || (state is TunnelState.Error && state.killSwitchBlocking)) {
                    val prefs = settingsDataStore.preferencesFlow.first()
                    if (isNetworkAllowedByPreferences(network, prefs)) {
                        reconnectLastTunnel()
                    }
                }
            }
        }

        override fun onLost(network: Network) {
            scope.launch {
                val state = _tunnelState.value
                if (state is TunnelState.Connected) {
                    val prefs = settingsDataStore.preferencesFlow.first()
                    val meta = lastServerMeta
                    if (meta != null) {
                        _tunnelState.value = TunnelState.Reconnecting(
                            serverName = meta.name,
                            reason = if (prefs.killSwitchEnabled) {
                                "Network changed — Kill Switch holding tunnel"
                            } else {
                                "Network interrupted — waiting to reconnect"
                            }
                        )
                        SbrVpnMonitorService.updateStatus(
                            context = context,
                            title = "SBR VPN Reconnecting",
                            subtitle = if (prefs.killSwitchEnabled) {
                                "Network interrupted — Kill Switch active"
                            } else {
                                "Waiting for network availability…"
                            }
                        )
                    }
                }
            }
        }
    }

    fun prepareVpnPermission(): Intent? = VpnService.prepare(context)

    suspend fun getOrCreateClientKeyPair(): Pair<String, String> {
        val existing = settingsDataStore.getWireGuardKeyPair()
        if (existing != null) return existing
        val generated = WireGuardConfigBuilder.generateKeyPair()
        settingsDataStore.saveWireGuardKeyPair(generated.first, generated.second)
        return generated
    }

    fun isCurrentNetworkAllowed(prefs: AppPreferences): Boolean {
        val active = connectivityManager.activeNetwork ?: return true
        return isNetworkAllowedByPreferences(active, prefs)
    }

    private fun isNetworkAllowedByPreferences(network: Network, prefs: AppPreferences): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return true
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return when {
            isWifi && !prefs.connectOnWifi -> false
            isCellular && !prefs.connectOnMobileData -> false
            else -> true
        }
    }

    suspend fun connectWithProvision(
        server: VpnServer,
        provision: VpnProvisionResponse,
        clientPrivateKeyBase64: String,
        prefs: AppPreferences
    ): Result<Unit> {
        val config = runCatching {
            WireGuardConfigBuilder.buildFromProvision(
                provision = provision,
                clientPrivateKeyBase64 = clientPrivateKeyBase64,
                protocolOption = prefs.preferredProtocol
            )
        }.getOrElse { return Result.failure(it) }

        activeSessionId = provision.sessionId
        val meta = ActiveServerMeta(
            name = "${server.countryName} — ${server.city}",
            flag = server.flagEmoji,
            endpoint = provision.endpoint,
            assignedIp = provision.assignedIp,
            dnsServers = provision.dnsServers,
            protocolLabel = prefs.preferredProtocol.displayName
        )
        return bringTunnelUp(config, meta)
    }

    suspend fun connectWithRawConfig(
        label: String,
        rawConfigText: String
    ): Result<Unit> {
        val config = runCatching {
            WireGuardConfigBuilder.parseRawConfig(rawConfigText)
        }.getOrElse { return Result.failure(it) }

        activeSessionId = null
        val firstPeer = config.peers.firstOrNull()
        val endpointStr = firstPeer?.endpoint?.map { it.toString() }?.orElse("Custom Peer") ?: "Custom Peer"
        val addressesStr = config.`interface`.addresses.joinToString(", ") { it.toString() }.ifBlank { "Tunnel IP" }
        val dnsList = config.`interface`.dnsServers.map { it.hostAddress ?: it.toString() }

        val meta = ActiveServerMeta(
            name = label,
            flag = "🛡️",
            endpoint = endpointStr,
            assignedIp = addressesStr,
            dnsServers = dnsList,
            protocolLabel = "WireGuard (.conf)"
        )
        return bringTunnelUp(config, meta)
    }

    private suspend fun bringTunnelUp(config: Config, meta: ActiveServerMeta): Result<Unit> {
        if (VpnService.prepare(context) != null) {
            return Result.failure(IllegalStateException("Android VPN permission not granted."))
        }

        userRequestedDisconnect = false
        lastConfig = config
        lastServerMeta = meta
        _tunnelState.value = TunnelState.Connecting(meta.name)

        return withContext(Dispatchers.IO) {
            runCatching {
                val state = backend.setState(tunnel, Tunnel.State.UP, config)
                if (state != Tunnel.State.UP) {
                    throw IllegalStateException("WireGuard backend did not enter UP state (state=$state).")
                }
            }.onSuccess {
                connectTimestampMs = System.currentTimeMillis()
                prevRxBytes = 0L
                prevTxBytes = 0L
                val hasIpv6Route = config.peers.any { peer ->
                    peer.allowedIps.any { it.toString().contains(":") }
                }
                _metrics.value = TunnelMetrics(
                    dnsLeakProtected = config.`interface`.dnsServers.isNotEmpty(),
                    ipv6LeakBlocked = hasIpv6Route
                )
                _tunnelState.value = TunnelState.Connected(
                    serverName = meta.name,
                    serverFlag = meta.flag,
                    endpoint = meta.endpoint,
                    assignedTunnelIp = meta.assignedIp,
                    dnsServers = meta.dnsServers,
                    protocolLabel = meta.protocolLabel
                )
                registerNetworkCallbackIfNeeded()
                startMonitorService(meta.name, meta.endpoint)
                startTelemetryPolling()
            }.onFailure { err ->
                _tunnelState.value = TunnelState.Error(
                    message = err.localizedMessage ?: "Failed to establish WireGuard tunnel.",
                    killSwitchBlocking = false
                )
            }
        }
    }

    suspend fun reconnectLastTunnel(): Result<Unit> {
        val config = lastConfig ?: return Result.failure(IllegalStateException("No previous tunnel configuration."))
        val meta = lastServerMeta ?: return Result.failure(IllegalStateException("No previous server metadata."))
        _tunnelState.value = TunnelState.Reconnecting(meta.name, "Re-establishing WireGuard handshake…")
        withContext(Dispatchers.IO) {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        }
        return bringTunnelUp(config, meta)
    }

    suspend fun disconnect(): Pair<String?, TunnelMetrics> {
        userRequestedDisconnect = true
        val finalMetrics = _metrics.value
        val sessionId = activeSessionId
        stopTelemetry()
        unregisterNetworkCallback()
        withContext(Dispatchers.IO) {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        }
        activeSessionId = null
        _tunnelState.value = TunnelState.Disconnected
        _metrics.value = TunnelMetrics()
        stopMonitorService()
        return sessionId to finalMetrics
    }

    private fun startTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val currentState = _tunnelState.value
                if (currentState !is TunnelState.Connected) continue

                val stats = withContext(Dispatchers.IO) {
                    runCatching { backend.getStatistics(tunnel) }.getOrNull()
                } ?: continue

                val rx = stats.totalRx()
                val tx = stats.totalTx()
                val dlSpeed = if (prevRxBytes == 0L && rx > 0L) 0L else (rx - prevRxBytes).coerceAtLeast(0L)
                val ulSpeed = if (prevTxBytes == 0L && tx > 0L) 0L else (tx - prevTxBytes).coerceAtLeast(0L)
                prevRxBytes = rx
                prevTxBytes = tx

                val durationSec = ((System.currentTimeMillis() - connectTimestampMs) / 1000L).coerceAtLeast(0L)
                val latestHandshake = stats.peers().maxOfOrNull { peerKey ->
                    stats.peer(peerKey)?.latestHandshakeEpochMillis ?: 0L
                }?.takeIf { it > 0L }

                val currentMetrics = _metrics.value
                val newSample = SpeedSample(
                    timestampMs = System.currentTimeMillis(),
                    downloadBytesPerSec = dlSpeed,
                    uploadBytesPerSec = ulSpeed
                )
                val updatedHistory = (currentMetrics.speedHistory + newSample).takeLast(30)

                _metrics.value = currentMetrics.copy(
                    durationSeconds = durationSec,
                    downloadSpeedBytesPerSec = dlSpeed,
                    uploadSpeedBytesPerSec = ulSpeed,
                    peakDownloadBytesPerSec = maxOf(currentMetrics.peakDownloadBytesPerSec, dlSpeed),
                    peakUploadBytesPerSec = maxOf(currentMetrics.peakUploadBytesPerSec, ulSpeed),
                    totalRxBytes = rx,
                    totalTxBytes = tx,
                    latestHandshakeMs = latestHandshake,
                    speedHistory = updatedHistory
                )
            }
        }
    }

    private fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    private fun registerNetworkCallbackIfNeeded() {
        if (networkCallbackRegistered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun startMonitorService(serverName: String, endpoint: String) {
        SbrVpnMonitorService.start(context, serverName, endpoint)
    }

    private fun stopMonitorService() {
        SbrVpnMonitorService.stop(context)
    }
}
