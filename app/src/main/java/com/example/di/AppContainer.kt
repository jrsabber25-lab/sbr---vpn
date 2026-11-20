package com.example.di

import android.content.Context
import com.example.billing.PlayBillingManager
import com.example.data.local.KeystoreCryptoManager
import com.example.data.local.SettingsDataStore
import com.example.data.remote.SbrApiClient
import com.example.data.repository.VpnRepository
import com.example.vpn.SbrVpnManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val cryptoManager = KeystoreCryptoManager()
    val settingsDataStore = SettingsDataStore(appContext, cryptoManager)
    val apiClient = SbrApiClient(settingsDataStore)
    val vpnManager = SbrVpnManager(appContext, settingsDataStore)
    val vpnRepository = VpnRepository(apiClient, settingsDataStore, vpnManager)
    val playBillingManager = PlayBillingManager(appContext)
}
