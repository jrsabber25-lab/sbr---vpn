package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.TunnelState
import com.example.ui.theme.SbrOrange
import com.example.ui.theme.SbrOrangeBright
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald

/**
 * Original geometric hexagonal shield logo for SBR VPN (never copied from third parties).
 */
@Composable
fun SbrShieldLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    accentColor: Color = SbrOrange
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "SBR VPN shield logo" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val shieldPath = Path().apply {
            moveTo(w * 0.50f, h * 0.06f)
            lineTo(w * 0.90f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.60f)
            quadraticTo(w * 0.82f, h * 0.82f, w * 0.50f, h * 0.95f)
            quadraticTo(w * 0.18f, h * 0.82f, w * 0.14f, h * 0.60f)
            lineTo(w * 0.10f, h * 0.22f)
            close()
        }

        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(
                colors = listOf(accentColor, SbrOrangeBright),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        val innerPath = Path().apply {
            moveTo(w * 0.50f, h * 0.17f)
            lineTo(w * 0.78f, h * 0.28f)
            lineTo(w * 0.75f, h * 0.57f)
            quadraticTo(w * 0.72f, h * 0.73f, w * 0.50f, h * 0.83f)
            quadraticTo(w * 0.28f, h * 0.73f, w * 0.25f, h * 0.57f)
            lineTo(w * 0.22f, h * 0.28f)
            close()
        }
        drawPath(
            path = innerPath,
            color = Color(0xFF0A1628)
        )

        // Geometric lightning / tunnel chevron inside shield
        val bolt = Path().apply {
            moveTo(w * 0.55f, h * 0.29f)
            lineTo(w * 0.37f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.52f)
            lineTo(w * 0.45f, h * 0.72f)
            lineTo(w * 0.64f, h * 0.47f)
            lineTo(w * 0.51f, h * 0.47f)
            close()
        }
        drawPath(path = bolt, color = Color.White)
    }
}

@Composable
fun ConnectionStatusPill(
    tunnelState: TunnelState,
    modifier: Modifier = Modifier
) {
    val (label, dotColor) = when (tunnelState) {
        is TunnelState.Connected -> "Connected" to StatusEmerald
        is TunnelState.Connecting -> "Connecting…" to StatusAmber
        is TunnelState.Reconnecting -> "Reconnecting…" to StatusAmber
        is TunnelState.Error -> if (tunnelState.killSwitchBlocking) {
            "Kill Switch Active" to StatusCrimson
        } else {
            "Connection Error" to StatusCrimson
        }
        is TunnelState.Disconnected -> "Disconnected" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val animatedDotColor by animateColorAsState(targetValue = dotColor, label = "dot_color")

    Surface(
        modifier = modifier.testTag("connection_status_pill"),
        shape = RoundedCornerShape(50),
        color = animatedDotColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, animatedDotColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(animatedDotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = animatedDotColor
            )
        }
    }
}

@Composable
fun VpnPowerButton(
    tunnelState: TunnelState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = tunnelState is TunnelState.Connected
    val isTransitioning = tunnelState is TunnelState.Connecting || tunnelState is TunnelState.Reconnecting

    val infiniteTransition = rememberInfiniteTransition(label = "vpn_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected || isTransitioning) 1.18f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            isConnected -> StatusEmerald
            isTransitioning -> StatusAmber
            tunnelState is TunnelState.Error -> StatusCrimson
            else -> SbrOrange
        },
        label = "ring_color"
    )

    val actionLabel = when {
        isConnected -> "Disconnect VPN"
        isTransitioning -> "Cancel VPN Connection"
        else -> "Connect VPN"
    }

    Box(
        modifier = modifier.size(216.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected || isTransitioning) {
            Box(
                modifier = Modifier
                    .size(196.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = 0.14f))
            )
        }

        Box(
            modifier = Modifier
                .size(174.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ringColor.copy(alpha = 0.25f),
                            ringColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(2.dp, ringColor.copy(alpha = 0.5f), CircleShape)
        )

        Surface(
            modifier = Modifier
                .size(142.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag("vpn_power_button")
                .semantics { contentDescription = actionLabel },
            shape = CircleShape,
            color = if (isConnected) StatusEmerald else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 12.dp,
            border = BorderStroke(
                width = 3.dp,
                color = if (isConnected) Color.White.copy(alpha = 0.45f) else ringColor
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isConnected) Color.White else ringColor,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        isConnected -> "CONNECTED"
                        isTransitioning -> "WAIT…"
                        else -> "CONNECT"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun TelemetryMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun LiveThroughputChart(
    downloadHistory: List<Long>,
    uploadHistory: List<Long>,
    modifier: Modifier = Modifier
) {
    val dlColor = SbrOrange
    val ulColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val rows = 3
        for (i in 1..rows) {
            val y = h * (i.toFloat() / (rows + 1))
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val maxVal = maxOf(
            downloadHistory.maxOrNull() ?: 1L,
            uploadHistory.maxOrNull() ?: 1L,
            1024L
        ).toFloat()

        fun drawSeries(values: List<Long>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { idx, v ->
                val x = (idx.toFloat() / (values.size - 1)) * w
                val y = h - ((v.toFloat() / maxVal) * (h * 0.85f)) - (h * 0.05f)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawSeries(uploadHistory, ulColor)
        drawSeries(downloadHistory, dlColor)
    }
}
