package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class ServerTier {
    FREE,
    PREMIUM
}

enum class VpnProtocolOption(val displayName: String, val description: String) {
    WIREGUARD("WireGuard", "Modern Curve25519 + ChaCha20 kernel/userspace tunnel"),
    WIREGUARD_LOW_MTU("WireGuard (1280 MTU)", "Optimized for restrictive mobile carrier networks")
}

@JsonClass(generateAdapter = true)
data class VpnServer(
    val id: String,
    @Json(name = "country_code") val countryCode: String,
    @Json(name = "country_name") val countryName: String,
    val city: String,
    val hostname: String,
    val port: Int,
    @Json(name = "is_online") val isOnline: Boolean,
    @Json(name = "load_percent") val loadPercent: Int,
    val tier: String,
    @Json(name = "health_port") val healthPort: Int = 443,
    @Json(name = "measured_latency_ms") val measuredLatencyMs: Int? = null
) {
    val isPremium: Boolean get() = tier.equals("premium", ignoreCase = true)
    val flagEmoji: String get() = countryCodeToFlag(countryCode)

    companion object {
        fun countryCodeToFlag(code: String): String {
            val clean = code.trim().uppercase()
            if (clean.length != 2) return "🌐"
            val first = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(first)) + String(Character.toChars(second))
        }
    }
}

@JsonClass(generateAdapter = true)
data class VpnConnectRequest(
    @Json(name = "server_id") val serverId: String,
    @Json(name = "client_public_key") val clientPublicKey: String,
    @Json(name = "protocol") val protocol: String = "wireguard"
)

@JsonClass(generateAdapter = true)
data class VpnProvisionResponse(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "server_id") val serverId: String,
    @Json(name = "server_public_key") val serverPublicKey: String,
    @Json(name = "endpoint") val endpoint: String,
    @Json(name = "assigned_ip") val assignedIp: String,
    @Json(name = "dns_servers") val dnsServers: List<String>,
    @Json(name = "allowed_ips") val allowedIps: List<String>,
    @Json(name = "mtu") val mtu: Int = 1420,
    @Json(name = "persistent_keepalive") val persistentKeepalive: Int = 25
)

@JsonClass(generateAdapter = true)
data class VpnDisconnectRequest(
    @Json(name = "session_id") val sessionId: String? = null,
    @Json(name = "rx_bytes") val rxBytes: Long = 0L,
    @Json(name = "tx_bytes") val txBytes: Long = 0L
)

@JsonClass(generateAdapter = true)
data class AuthRequest(
    val email: String,
    val password: String,
    @Json(name = "display_name") val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class GuestAuthRequest(
    @Json(name = "device_id") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class PasswordResetRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class PasswordResetConfirm(
    val email: String,
    val token: String,
    @Json(name = "new_password") val newPassword: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String = "bearer",
    val user: UserProfile
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String,
    val email: String? = null,
    @Json(name = "display_name") val displayName: String = "Guest User",
    @Json(name = "is_guest") val isGuest: Boolean = true,
    @Json(name = "subscription_tier") val subscriptionTier: String = "free",
    @Json(name = "subscription_expires_at") val subscriptionExpiresAt: String? = null,
    @Json(name = "max_devices") val maxDevices: Int = 1
) {
    val isPremium: Boolean get() = subscriptionTier.equals("premium", ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class SubscriptionPlan(
    val id: String,
    val name: String,
    @Json(name = "play_product_id") val playProductId: String,
    @Json(name = "price_display") val priceDisplay: String,
    @Json(name = "billing_period") val billingPeriod: String,
    @Json(name = "max_devices") val maxDevices: Int,
    @Json(name = "fair_usage_gb") val fairUsageGb: Int?,
    val features: List<String>
)

@JsonClass(generateAdapter = true)
data class PlayVerifyRequest(
    @Json(name = "product_id") val productId: String,
    @Json(name = "purchase_token") val purchaseToken: String
)

@JsonClass(generateAdapter = true)
data class SupportTicketRequest(
    val subject: String,
    val category: String,
    val message: String,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "android_version") val androidVersion: String
)

@JsonClass(generateAdapter = true)
data class SupportTicketResponse(
    @Json(name = "ticket_id") val ticketId: String,
    val status: String,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class PublicIpResponse(
    val ip: String
)

@JsonClass(generateAdapter = true)
data class GenericMessageResponse(
    val message: String
)

sealed interface TunnelState {
    data object Disconnected : TunnelState
    data class Connecting(val serverName: String) : TunnelState
    data class Connected(
        val serverName: String,
        val serverFlag: String,
        val endpoint: String,
        val assignedTunnelIp: String,
        val dnsServers: List<String>,
        val protocolLabel: String
    ) : TunnelState
    data class Reconnecting(val serverName: String, val reason: String) : TunnelState
    data class Error(val message: String, val killSwitchBlocking: Boolean = false) : TunnelState
}

data class SpeedSample(
    val timestampMs: Long,
    val downloadBytesPerSec: Long,
    val uploadBytesPerSec: Long
)

data class TunnelMetrics(
    val durationSeconds: Long = 0L,
    val downloadSpeedBytesPerSec: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val peakDownloadBytesPerSec: Long = 0L,
    val peakUploadBytesPerSec: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val latestHandshakeMs: Long? = null,
    val dnsLeakProtected: Boolean = false,
    val ipv6LeakBlocked: Boolean = false,
    val speedHistory: List<SpeedSample> = emptyList()
)

data class AppPreferences(
    val onboardingCompleted: Boolean = false,
    val autoConnect: Boolean = false,
    val killSwitchEnabled: Boolean = true,
    val connectOnWifi: Boolean = true,
    val connectOnMobileData: Boolean = true,
    val preferredProtocol: VpnProtocolOption = VpnProtocolOption.WIREGUARD,
    val darkMode: Boolean = true,
    val languageCode: String = "en",
    val apiBaseUrl: String = "",
    val selectedServerId: String? = null,
    val favoriteServerIds: Set<String> = emptySet(),
    val recentServerIds: List<String> = emptyList(),
    val userEmail: String? = null,
    val userDisplayName: String = "Not Signed In",
    val isGuest: Boolean = true,
    val subscriptionTier: String = "free",
    val maxDevices: Int = 1,
    val wgPublicKey: String? = null
)
