package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.data.model.AppPreferences
import com.example.data.model.UserProfile
import com.example.data.model.VpnProtocolOption
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sbrDataStore: DataStore<Preferences> by preferencesDataStore(name = "sbr_vpn_prefs")

class SettingsDataStore(
    private val context: Context,
    private val crypto: KeystoreCryptoManager
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val CONNECT_ON_WIFI = booleanPreferencesKey("connect_on_wifi")
        val CONNECT_ON_MOBILE = booleanPreferencesKey("connect_on_mobile")
        val PREFERRED_PROTOCOL = stringPreferencesKey("preferred_protocol")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val FAVORITE_SERVER_IDS = stringSetPreferencesKey("favorite_server_ids")
        val RECENT_SERVER_IDS = stringPreferencesKey("recent_server_ids")
        val AUTH_TOKEN_ENC = stringPreferencesKey("auth_token_enc")
        val WG_PRIVATE_KEY_ENC = stringPreferencesKey("wg_private_key_enc")
        val WG_PUBLIC_KEY = stringPreferencesKey("wg_public_key")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val IS_GUEST = booleanPreferencesKey("is_guest")
        val SUBSCRIPTION_TIER = stringPreferencesKey("subscription_tier")
        val MAX_DEVICES = intPreferencesKey("max_devices")
        val IMPORTED_WG_CONF_ENC = stringPreferencesKey("imported_wg_conf_enc")
        val IMPORTED_WG_LABEL = stringPreferencesKey("imported_wg_label")
    }

    val preferencesFlow: Flow<AppPreferences> = context.sbrDataStore.data.map { prefs ->
        val protocolName = prefs[Keys.PREFERRED_PROTOCOL] ?: VpnProtocolOption.WIREGUARD.name
        val protocol = VpnProtocolOption.entries.firstOrNull { it.name == protocolName }
            ?: VpnProtocolOption.WIREGUARD
        val recentRaw = prefs[Keys.RECENT_SERVER_IDS].orEmpty()
        val recentList = if (recentRaw.isBlank()) emptyList() else recentRaw.split(",").filter { it.isNotBlank() }

        AppPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: false,
            killSwitchEnabled = prefs[Keys.KILL_SWITCH] ?: true,
            connectOnWifi = prefs[Keys.CONNECT_ON_WIFI] ?: true,
            connectOnMobileData = prefs[Keys.CONNECT_ON_MOBILE] ?: true,
            preferredProtocol = protocol,
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            apiBaseUrl = prefs[Keys.API_BASE_URL] ?: BuildConfig.DEFAULT_API_BASE_URL,
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID],
            favoriteServerIds = prefs[Keys.FAVORITE_SERVER_IDS] ?: emptySet(),
            recentServerIds = recentList,
            userEmail = prefs[Keys.USER_EMAIL],
            userDisplayName = prefs[Keys.USER_DISPLAY_NAME] ?: "Not Signed In",
            isGuest = prefs[Keys.IS_GUEST] ?: true,
            subscriptionTier = prefs[Keys.SUBSCRIPTION_TIER] ?: "free",
            maxDevices = prefs[Keys.MAX_DEVICES] ?: 1,
            wgPublicKey = prefs[Keys.WG_PUBLIC_KEY]
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.sbrDataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.sbrDataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun getDecryptedAuthToken(): String? {
        val enc = context.sbrDataStore.data.first()[Keys.AUTH_TOKEN_ENC] ?: return null
        return crypto.decrypt(enc)
    }

    suspend fun saveAuthSession(token: String, profile: UserProfile) {
        val encryptedToken = crypto.encrypt(token)
        context.sbrDataStore.edit { prefs ->
            prefs[Keys.AUTH_TOKEN_ENC] = encryptedToken
            if (profile.email != null) {
                prefs[Keys.USER_EMAIL] = profile.email
            } else {
                prefs.remove(Keys.USER_EMAIL)
            }
            prefs[Keys.USER_DISPLAY_NAME] = profile.displayName
            prefs[Keys.IS_GUEST] = profile.isGuest
            prefs[Keys.SUBSCRIPTION_TIER] = profile.subscriptionTier
            prefs[Keys.MAX_DEVICES] = profile.maxDevices
        }
    }

    suspend fun clearAuthSession() {
        context.sbrDataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN_ENC)
            prefs.remove(Keys.USER_EMAIL)
            prefs[Keys.USER_DISPLAY_NAME] = "Not Signed In"
            prefs[Keys.IS_GUEST] = true
            prefs[Keys.SUBSCRIPTION_TIER] = "free"
            prefs[Keys.MAX_DEVICES] = 1
        }
    }

    suspend fun getWireGuardKeyPair(): Pair<String, String>? {
        val prefs = context.sbrDataStore.data.first()
        val encPriv = prefs[Keys.WG_PRIVATE_KEY_ENC] ?: return null
        val pub = prefs[Keys.WG_PUBLIC_KEY] ?: return null
        val priv = crypto.decrypt(encPriv) ?: return null
        return priv to pub
    }

    suspend fun saveWireGuardKeyPair(privateKeyBase64: String, publicKeyBase64: String) {
        val encPriv = crypto.encrypt(privateKeyBase64)
        context.sbrDataStore.edit { prefs ->
            prefs[Keys.WG_PRIVATE_KEY_ENC] = encPriv
            prefs[Keys.WG_PUBLIC_KEY] = publicKeyBase64
        }
    }

    suspend fun saveImportedWireGuardConfig(label: String, configText: String) {
        val enc = crypto.encrypt(configText)
        context.sbrDataStore.edit { prefs ->
            prefs[Keys.IMPORTED_WG_CONF_ENC] = enc
            prefs[Keys.IMPORTED_WG_LABEL] = label
        }
    }

    suspend fun getImportedWireGuardConfig(): Pair<String, String>? {
        val prefs = context.sbrDataStore.data.first()
        val enc = prefs[Keys.IMPORTED_WG_CONF_ENC] ?: return null
        val label = prefs[Keys.IMPORTED_WG_LABEL] ?: "Imported Tunnel"
        val raw = crypto.decrypt(enc) ?: return null
        return label to raw
    }

    suspend fun clearImportedWireGuardConfig() {
        context.sbrDataStore.edit { prefs ->
            prefs.remove(Keys.IMPORTED_WG_CONF_ENC)
            prefs.remove(Keys.IMPORTED_WG_LABEL)
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.sbrDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.sbrDataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.sbrDataStore.edit { it[Keys.KILL_SWITCH] = enabled }
    }

    suspend fun setConnectOnWifi(enabled: Boolean) {
        context.sbrDataStore.edit { it[Keys.CONNECT_ON_WIFI] = enabled }
    }

    suspend fun setConnectOnMobileData(enabled: Boolean) {
        context.sbrDataStore.edit { it[Keys.CONNECT_ON_MOBILE] = enabled }
    }

    suspend fun setPreferredProtocol(protocol: VpnProtocolOption) {
        context.sbrDataStore.edit { it[Keys.PREFERRED_PROTOCOL] = protocol.name }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.sbrDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setLanguageCode(code: String) {
        context.sbrDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.sbrDataStore.edit { it[Keys.API_BASE_URL] = url.trim().trimEnd('/') }
    }

    suspend fun selectServer(serverId: String) {
        context.sbrDataStore.edit { prefs ->
            prefs[Keys.SELECTED_SERVER_ID] = serverId
            val existing = prefs[Keys.RECENT_SERVER_IDS].orEmpty()
                .split(",")
                .filter { it.isNotBlank() && it != serverId }
            val updated = (listOf(serverId) + existing).take(5)
            prefs[Keys.RECENT_SERVER_IDS] = updated.joinToString(",")
        }
    }

    suspend fun toggleFavoriteServer(serverId: String) {
        context.sbrDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_SERVER_IDS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(serverId)) {
                current.remove(serverId)
            }
            prefs[Keys.FAVORITE_SERVER_IDS] = current
        }
    }
}
