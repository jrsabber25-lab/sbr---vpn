package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.VpnProtocolOption
import com.example.ui.SbrUiState
import com.example.ui.theme.SbrOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SbrUiState,
    onAutoConnectChange: (Boolean) -> Unit,
    onKillSwitchChange: (Boolean) -> Unit,
    onWifiChange: (Boolean) -> Unit,
    onMobileChange: (Boolean) -> Unit,
    onProtocolChange: (VpnProtocolOption) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSaveApiBaseUrl: (String) -> Unit,
    onImportWireGuardConf: (label: String, content: String) -> Unit,
    onClearImportedConf: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSubscription: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val prefs = uiState.preferences

    var apiUrlInput by remember(prefs.apiBaseUrl) { mutableStateOf(prefs.apiBaseUrl) }
    var protocolMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val languages = listOf(
        "en" to "English",
        "es" to "Español",
        "de" to "Deutsch",
        "fr" to "Français",
        "ja" to "日本語",
        "bn" to "বাংলা"
    )

    val confPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!text.isNullOrBlank()) {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "WireGuard Profile"
                onImportWireGuardConf(fileName, text)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Backend & Direct WireGuard Provisioning Card
        SettingsSectionCard(title = "Backend Infrastructure & Tunnel Profile") {
            OutlinedTextField(
                value = apiUrlInput,
                onValueChange = { apiUrlInput = it },
                label = { Text("SBR VPN HTTPS Backend URL") },
                placeholder = { Text("https://api.yourdomain.com") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_api_url_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onSaveApiBaseUrl(apiUrlInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_save_api_url_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
            ) {
                Text("Save & Connect Backend")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { confPickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_import_wg_conf_button")
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import WireGuard .conf File")
            }

            if (uiState.importedProfileLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Loaded: ${uiState.importedProfileLabel}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onClearImportedConf) {
                        Text("Remove")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Connection Behavior
        SettingsSectionCard(title = "VPN Connection Rules") {
            SettingsToggleRow(
                title = "Auto Connect",
                subtitle = "Automatically connect on app launch or device boot",
                checked = prefs.autoConnect,
                onCheckedChange = onAutoConnectChange,
                testTag = "switch_auto_connect"
            )
            SettingsToggleRow(
                title = "Kill Switch Guard",
                subtitle = "Hold tunnel & block fallback if connection drops unexpectedly",
                checked = prefs.killSwitchEnabled,
                onCheckedChange = onKillSwitchChange,
                testTag = "switch_kill_switch"
            )
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Android System Always-On VPN")
            }
            SettingsToggleRow(
                title = "Connect on Wi-Fi",
                subtitle = "Allow WireGuard tunnel on Wi-Fi networks",
                checked = prefs.connectOnWifi,
                onCheckedChange = onWifiChange,
                testTag = "switch_wifi"
            )
            SettingsToggleRow(
                title = "Connect on Mobile Data",
                subtitle = "Allow WireGuard tunnel on cellular networks",
                checked = prefs.connectOnMobileData,
                onCheckedChange = onMobileChange,
                testTag = "switch_mobile_data"
            )

            ExposedDropdownMenuBox(
                expanded = protocolMenuExpanded,
                onExpandedChange = { protocolMenuExpanded = !protocolMenuExpanded }
            ) {
                OutlinedTextField(
                    value = prefs.preferredProtocol.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Preferred VPN Protocol") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("settings_protocol_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = protocolMenuExpanded,
                    onDismissRequest = { protocolMenuExpanded = false }
                ) {
                    VpnProtocolOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(option.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            },
                            onClick = {
                                onProtocolChange(option)
                                protocolMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Appearance & Language
        SettingsSectionCard(title = "Appearance & Localization") {
            SettingsToggleRow(
                title = "Dark Mode",
                subtitle = "Deep navy cybersecurity theme",
                checked = prefs.darkMode,
                onCheckedChange = onDarkModeChange,
                testTag = "switch_dark_mode"
            )

            val currentLangLabel = languages.firstOrNull { it.first == prefs.languageCode }?.second ?: "English"
            ExposedDropdownMenuBox(
                expanded = languageMenuExpanded,
                onExpandedChange = { languageMenuExpanded = !languageMenuExpanded }
            ) {
                OutlinedTextField(
                    value = currentLangLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language Selection") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("settings_language_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
                ) {
                    languages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onLanguageChange(code)
                                languageMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Legal, Support & Plan
        SettingsSectionCard(title = "Legal, Support & About") {
            SettingsNavRow("Premium Subscription", onOpenSubscription, "nav_subscription")
            SettingsNavRow("Privacy Policy", onOpenPrivacyPolicy, "nav_privacy_policy")
            SettingsNavRow("Terms and Conditions", onOpenTerms, "nav_terms")
            SettingsNavRow("Contact Support", onOpenSupport, "nav_support")
            SettingsNavRow("About SBR VPN", { showAboutDialog = !showAboutDialog }, "nav_about")

            if (showAboutDialog) {
                Text(
                    text = "SBR VPN v1.0.0 • Powered by official WireGuard GoBackend & Android VpnService. Keys remain strictly on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SbrOrange
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(checkedTrackColor = SbrOrange)
        )
    }
}

@Composable
private fun SettingsNavRow(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
