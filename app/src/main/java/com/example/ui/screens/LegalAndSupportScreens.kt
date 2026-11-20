package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.SbrUiState
import com.example.ui.theme.SbrOrange

@Composable
fun LegalScreen(
    isPrivacyPolicy: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

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
            IconButton(onClick = onBack, modifier = Modifier.testTag("legal_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (isPrivacyPolicy) "Privacy Policy" else "Terms & Conditions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isPrivacyPolicy) {
                    LegalSection(
                        title = "1. What SBR VPN Never Collects",
                        body = "SBR VPN never inspects, logs, or sells your browsing history, visited domains, DNS query targets, destination IP addresses, or decrypted VPN packet payloads."
                    )
                    LegalSection(
                        title = "2. Transparent Operational Data Retention",
                        body = "To operate WireGuard tunnels, prevent abuse, and enforce device limits, SBR VPN retains strictly necessary operational records:\n" +
                            "• Account Email & Password Hash (Argon2/bcrypt): Retained while your account is active.\n" +
                            "• Device WireGuard Public Key & Ephemeral Tunnel IP (e.g. 10.8.0.x): Held only for the active session and purged within 15 minutes of disconnect.\n" +
                            "• Aggregate Bandwidth Counters (total Rx/Tx bytes per session): Retained up to 30 days for fair-usage enforcement and billing support — never tied to destination websites.\n" +
                            "• Crash & API Error Telemetry: Stripped of payload/IP identifiers, retained 14 days."
                    )
                    LegalSection(
                        title = "3. Device-Side Key Security",
                        body = "Your Curve25519 private key is generated locally inside Android and stored encrypted via Android Keystore AES-256-GCM. It never leaves your device."
                    )
                } else {
                    LegalSection(
                        title = "1. Acceptable Network Use",
                        body = "You agree not to use SBR VPN for unauthorized network intrusion, DDoS attacks, malware distribution, spam, or unlawful activity."
                    )
                    LegalSection(
                        title = "2. Service Limits & Subscriptions",
                        body = "Free accounts are subject to published monthly fair-usage quotas and single-device concurrency. Paid subscriptions are billed and managed transparently through Google Play Billing."
                    )
                    LegalSection(
                        title = "3. Availability & Infrastructure",
                        body = "Server availability depends on active deployed WireGuard VPS nodes. SBR VPN reserves the right to rotate keys, maintain nodes, and enforce rate limits to protect network stability."
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = SbrOrange
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SupportScreen(
    uiState: SbrUiState,
    onSubmitTicket: (subject: String, category: String, message: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var category by remember { mutableStateOf("Connection") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val categories = listOf("Connection", "Billing", "Speed", "Privacy")

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
            IconButton(onClick = onBack, modifier = Modifier.testTag("support_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Support & Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Live Device Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SbrOrange
                )
                Text(
                    text = "Tunnel: ${uiState.tunnelState::class.simpleName} • Servers Loaded: ${uiState.servers.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Protocol: ${uiState.preferences.preferredProtocol.displayName} • Kill Switch: ${if (uiState.preferences.killSwitchEnabled) "On" else "Off"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Contact Support",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) }
                        )
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_subject_input")
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Describe the issue (no sensitive passwords)") },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_message_input")
                )
                Button(
                    onClick = {
                        onSubmitTicket(subject, category, message)
                        subject = ""
                        message = ""
                    },
                    enabled = subject.isNotBlank() && message.length >= 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("support_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
                ) {
                    Text("Submit Ticket")
                }
            }
        }
    }
}
