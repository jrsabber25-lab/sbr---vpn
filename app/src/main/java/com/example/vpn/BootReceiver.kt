package com.example.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SbrVpnApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? SbrVpnApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.container.settingsDataStore.preferencesFlow.first()
                if (prefs.autoConnect && app.container.vpnManager.prepareVpnPermission() == null) {
                    app.container.vpnRepository.autoConnectIfPossible()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
