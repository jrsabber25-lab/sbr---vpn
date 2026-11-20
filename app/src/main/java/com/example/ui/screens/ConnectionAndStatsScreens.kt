package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.TunnelState
import com.example.ui.SbrUiState
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.LiveThroughputChart
import com.example.ui.theme.SbrOrange
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.util.FormatUtils

@Composable
fun ConnectionScreen(
    uiState: SbrUiState,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val tunnel = uiState.tunnelState
    val metrics = uiState.metrics

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
            IconButton(onClick = onBack, modifier = Modifier.testTag("connection_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Tunnel Inspection",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ConnectionStatusPill(tunnelState = tunnel)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = FormatUtils.formatDuration(metrics.durationSeconds),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

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
                when (tunnel) {
                    is TunnelState.Connected -> {
                        DetailRow("Server", "${tunnel.serverFlag} ${tunnel.serverName}")
                        DetailRow("WireGuard Endpoint", tunnel.endpoint)
                        DetailRow("Assigned Tunnel IP", tunnel.assignedTunnelIp)
                        DetailRow("Protected DNS", tunnel.dnsServers.joinToString(", ").ifBlank { "System" })
                        DetailRow("Protocol", tunnel.protocolLabel)
                    }
                    is TunnelState.Connecting -> {
                        DetailRow("Target Node", tunnel.serverName)
                        DetailRow("Stage", "Negotiating Curve25519 WireGuard handshake…")
                    }
                    is TunnelState.Reconnecting -> {
                        DetailRow("Target Node", tunnel.serverName)
                        DetailRow("Status", tunnel.reason)
                    }
                    is TunnelState.Error -> {
                        DetailRow("Tunnel Error", tunnel.message)
                        DetailRow("Kill Switch", if (tunnel.killSwitchBlocking) "Blocking Fallback" else "Standby")
                    }
                    TunnelState.Disconnected -> {
                        DetailRow("Tunnel Interface", "Down (no active WireGuard interface)")
                        DetailRow("Client Public Key", uiState.preferences.wgPublicKey?.take(22)?.plus("…") ?: "Generated on demand")
                    }
                }
                DetailRow("Public IP", uiState.publicIp ?: "Unavailable")
                DetailRow("Kill Switch Guard", if (uiState.preferences.killSwitchEnabled) "Enabled" else "Disabled")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Android System Always-On & Block Connections Without VPN shortcut
        OutlinedButton(
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .testTag("system_vpn_killswitch_button")
        ) {
            Icon(Icons.Rounded.Security, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Android System Always-On / Kill Switch")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tunnel is TunnelState.Connected || tunnel is TunnelState.Reconnecting || tunnel is TunnelState.Error) {
                OutlinedButton(
                    onClick = onReconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("reconnect_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reconnect")
                }
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("disconnect_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson)
                ) {
                    Icon(Icons.Rounded.PowerOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("connection_screen_connect_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Establish WireGuard Tunnel")
                }
            }
        }
    }
}

@Composable
fun StatsScreen(
    uiState: SbrUiState
) {
    val metrics = uiState.metrics
    val handshakeAgeText = metrics.latestHandshakeMs?.let { ts ->
        val ageSec = ((System.currentTimeMillis() - ts) / 1000L).coerceAtLeast(0L)
        "${ageSec}s ago"
    } ?: "No active handshake"

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Speed, contentDescription = null, tint = SbrOrange)
            Text(
                text = "Connection Statistics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Throughput Graph Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Real-Time Tunnel Throughput",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "● DL: ${FormatUtils.formatSpeed(metrics.downloadSpeedBytesPerSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SbrOrange
                    )
                    Text(
                        text = "● UL: ${FormatUtils.formatSpeed(metrics.uploadSpeedBytesPerSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusCyan
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (metrics.speedHistory.size < 2) {
                    Text(
                        text = "Connect to a WireGuard tunnel to stream live rx/tx telemetry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                } else {
                    LiveThroughputChart(
                        downloadHistory = metrics.speedHistory.map { it.downloadBytesPerSec },
                        uploadHistory = metrics.speedHistory.map { it.uploadBytesPerSec },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow("Session Duration", FormatUtils.formatDuration(metrics.durationSeconds))
                DetailRow("Total Downloaded (Rx)", FormatUtils.formatBytes(metrics.totalRxBytes))
                DetailRow("Total Uploaded (Tx)", FormatUtils.formatBytes(metrics.totalTxBytes))
                DetailRow("Peak Download", FormatUtils.formatSpeed(metrics.peakDownloadBytesPerSec))
                DetailRow("Peak Upload", FormatUtils.formatSpeed(metrics.peakUploadBytesPerSec))
                DetailRow("Latest WG Handshake", handshakeAgeText)
                DetailRow(
                    "DNS Leak Protection",
                    if (metrics.dnsLeakProtected) "Active (Tunnel DNS enforced)" else "Standby",
                    valueColor = if (metrics.dnsLeakProtected) StatusEmerald else StatusAmber
                )
                DetailRow(
                    "IPv6 Routing / Leak Guard",
                    if (metrics.ipv6LeakBlocked) "Routed via Tunnel (::/0)" else "IPv4 Tunnel Only",
                    valueColor = if (metrics.ipv6LeakBlocked) StatusEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
