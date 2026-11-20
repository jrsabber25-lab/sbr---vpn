package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TunnelState
import com.example.data.repository.BackendConnectionStatus
import com.example.ui.SbrUiState
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.SbrShieldLogo
import com.example.ui.components.TelemetryMetricCard
import com.example.ui.components.VpnPowerButton
import com.example.ui.theme.SbrOrange
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.util.FormatUtils

@Composable
fun HomeScreen(
    uiState: SbrUiState,
    onToggleConnection: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenConnectionDetails: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onRefreshPublicIp: () -> Unit,
    onConnectImported: () -> Unit
) {
    val selected = uiState.selectedServer
    val tunnel = uiState.tunnelState
    val metrics = uiState.metrics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SbrShieldLogo(size = 34.dp)
                Column {
                    Text(
                        text = "SBR VPN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.preferences.preferredProtocol.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.testTag("home_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = "User Profile"
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Honest Infrastructure Status Banner if Backend is Unconfigured or Unreachable
        when (val status = uiState.backendStatus) {
            is BackendConnectionStatus.Unconfigured -> {
                InfrastructureAlertCard(
                    title = "Backend & VPN Nodes Not Configured",
                    message = "Configure your deployed SBR VPN HTTPS API URL in Settings, or import a WireGuard .conf file to test a real tunnel directly.",
                    actionLabel = "Configure Backend",
                    onAction = onOpenSettings,
                    modifier = Modifier.widthIn(max = 620.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            is BackendConnectionStatus.Unreachable -> {
                InfrastructureAlertCard(
                    title = "Backend API Unreachable",
                    message = status.error,
                    actionLabel = "Check Settings",
                    onAction = onOpenSettings,
                    modifier = Modifier.widthIn(max = 620.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            else -> Unit
        }

        // Status Pill + Session Duration
        ConnectionStatusPill(tunnelState = tunnel)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = FormatUtils.formatDuration(metrics.durationSeconds),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("home_duration_text")
        )

        if (tunnel is TunnelState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tunnel.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else if (tunnel is TunnelState.Reconnecting) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tunnel.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = StatusAmber
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Giant Connect / Disconnect Button
        VpnPowerButton(
            tunnelState = tunnel,
            onClick = onToggleConnection
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Real Public IP Address Chip (Only shown when genuinely fetched, never fake)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = "Public IP",
                    tint = if (tunnel is TunnelState.Connected) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Public IP: ${uiState.publicIp ?: "Unavailable"}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("home_public_ip_text")
                )
                IconButton(
                    onClick = onRefreshPublicIp,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh Public IP",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Selected Server Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .clickable(onClick = onOpenServers)
                .testTag("home_server_selector_card"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = selected?.flagEmoji ?: "🛡️",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Column {
                        Text(
                            text = selected?.countryName ?: (uiState.importedProfileLabel ?: "No Server Deployed"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                selected != null -> {
                                    val ping = selected.measuredLatencyMs?.let { "${it} ms" } ?: "Unreachable"
                                    "${selected.city} • Load ${selected.loadPercent}% • $ping"
                                }
                                uiState.importedProfileLabel != null -> "Imported WireGuard .conf ready"
                                else -> "Tap to view server setup & regions"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Select Server",
                    tint = SbrOrange
                )
            }
        }

        if (uiState.importedProfileLabel != null && selected == null && tunnel !is TunnelState.Connected) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onConnectImported,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .testTag("connect_imported_profile_button")
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Imported Profile (${uiState.importedProfileLabel})")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Speed & Data Telemetry Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                title = "Download",
                value = FormatUtils.formatSpeed(metrics.downloadSpeedBytesPerSec),
                icon = Icons.Rounded.ArrowDownward,
                accent = StatusEmerald,
                modifier = Modifier.weight(1f)
            )
            TelemetryMetricCard(
                title = "Upload",
                value = FormatUtils.formatSpeed(metrics.uploadSpeedBytesPerSec),
                icon = Icons.Rounded.ArrowUpward,
                accent = StatusCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                title = "Total Downloaded",
                value = FormatUtils.formatBytes(metrics.totalRxBytes),
                icon = Icons.Rounded.CloudDownload,
                accent = SbrOrange,
                modifier = Modifier.weight(1f)
            )
            TelemetryMetricCard(
                title = "Total Uploaded",
                value = FormatUtils.formatBytes(metrics.totalTxBytes),
                icon = Icons.Rounded.CloudUpload,
                accent = SbrOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onOpenConnectionDetails,
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_tunnel_details_button")
            ) {
                Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tunnel View")
            }
            Button(
                onClick = onOpenStats,
                modifier = Modifier
                    .weight(1f)
                    .testTag("home_stats_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Live Statistics",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun InfrastructureAlertCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StatusAmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Infrastructure notice",
                    tint = StatusAmber
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = SbrOrange, fontWeight = FontWeight.Bold)
            }
        }
    }
}
