package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SubscriptionPlan
import com.example.ui.SbrUiState
import com.example.ui.theme.SbrOrange
import com.example.ui.theme.StatusEmerald

@Composable
fun SubscriptionScreen(
    uiState: SbrUiState,
    onPurchasePlan: (Activity, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val activity = LocalContext.current as? Activity
    val currentTier = uiState.preferences.subscriptionTier

    val displayPlans = uiState.plans.ifEmpty {
        listOf(
            SubscriptionPlan(
                id = "free",
                name = "Free Plan",
                playProductId = "",
                priceDisplay = "$0 / month",
                billingPeriod = "Free",
                maxDevices = 1,
                fairUsageGb = 10,
                features = listOf(
                    "Access to Free-tier deployed WireGuard regions",
                    "10 GB monthly fair-usage allowance",
                    "1 active device tunnel",
                    "DNS leak protection & Kill Switch included"
                )
            ),
            SubscriptionPlan(
                id = "premium_monthly",
                name = "SBR Premium",
                playProductId = "sbr_vpn_premium_monthly",
                priceDisplay = "Google Play Pricing",
                billingPeriod = "Monthly",
                maxDevices = 5,
                fairUsageGb = 500,
                features = listOf(
                    "Access to all deployed global WireGuard nodes",
                    "500 GB high-priority monthly allocation",
                    "Up to 5 simultaneous devices",
                    "Priority server routing & bandwidth QoS"
                )
            )
        )
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
            IconButton(onClick = onBack, modifier = Modifier.testTag("subscription_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Plans & Google Play Billing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Current Plan: ${currentTier.uppercase()} • Fair-usage limits reflect real VPS capacity; we never advertise false unlimited speeds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        displayPlans.forEach { plan ->
            val isCurrent = (plan.id == "free" && currentTier.equals("free", true)) ||
                (plan.id != "free" && currentTier.equals("premium", true))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .padding(bottom = 14.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (plan.playProductId.isNotBlank()) 2.dp else 1.dp,
                    color = if (plan.playProductId.isNotBlank()) SbrOrange else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = plan.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = plan.priceDisplay,
                                style = MaterialTheme.typography.labelLarge,
                                color = SbrOrange
                            )
                        }
                        if (isCurrent) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = StatusEmerald.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusEmerald,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    plan.features.forEach { feat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (plan.playProductId.isNotBlank()) SbrOrange else StatusEmerald
                            )
                            Text(text = feat, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (plan.playProductId.isNotBlank() && !isCurrent) {
                        Button(
                            onClick = {
                                if (activity != null) {
                                    onPurchasePlan(activity, plan.playProductId)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("subscribe_play_button_${plan.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
                        ) {
                            Text("Subscribe via Google Play")
                        }
                    }
                }
            }
        }
    }
}
