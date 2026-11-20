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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.SbrUiState
import com.example.ui.theme.SbrOrange
import com.example.ui.theme.StatusEmerald

private enum class AuthTab(val label: String) {
    LOGIN("Login"),
    SIGN_UP("Sign Up"),
    RESET("Recover")
}

@Composable
fun ProfileAndAuthScreen(
    uiState: SbrUiState,
    onGuestContinue: () -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, displayName: String) -> Unit,
    onRequestReset: (email: String) -> Unit,
    onConfirmReset: (email: String, token: String, newPassword: String) -> Unit,
    onLogout: () -> Unit,
    onOpenSubscription: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableStateOf(AuthTab.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf("") }

    val prefs = uiState.preferences
    val isSignedIn = prefs.userEmail != null || (prefs.userDisplayName != "Not Signed In")

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
            IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Account & Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active Profile Card
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = SbrOrange
                    )
                    Column {
                        Text(
                            text = prefs.userDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = prefs.userEmail ?: if (isSignedIn) "Guest Session" else "No active session",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Plan: ${prefs.subscriptionTier.uppercase()} (${prefs.maxDevices} device${if (prefs.maxDevices == 1) "" else "s"})",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (prefs.subscriptionTier.equals("premium", true)) SbrOrange else StatusEmerald
                    )
                    OutlinedButton(
                        onClick = onOpenSubscription,
                        modifier = Modifier.testTag("profile_upgrade_button")
                    ) {
                        Icon(Icons.Rounded.WorkspacePremium, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Plans")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Device WG PubKey: ${prefs.wgPublicKey?.take(20)?.plus("…") ?: "Pending"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isSignedIn) {
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Auth Forms (Sign Up / Login / Guest / Password Recovery)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("auth_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                if (selectedTab == AuthTab.SIGN_UP) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_display_name_input")
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                if (selectedTab == AuthTab.RESET) {
                    OutlinedTextField(
                        value = resetToken,
                        onValueChange = { resetToken = it },
                        label = { Text("Recovery Token (from email)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_reset_token_input")
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (selectedTab == AuthTab.RESET) "New Password" else "Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )

                when (selectedTab) {
                    AuthTab.LOGIN -> {
                        Button(
                            onClick = { onLogin(email, password) },
                            enabled = !uiState.isAuthBusy && email.isNotBlank() && password.length >= 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("login_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
                        ) {
                            Text("Sign In via HTTPS")
                        }
                    }
                    AuthTab.SIGN_UP -> {
                        Button(
                            onClick = { onRegister(email, password, displayName.ifBlank { "SBR Member" }) },
                            enabled = !uiState.isAuthBusy && email.isNotBlank() && password.length >= 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("signup_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
                        ) {
                            Text("Create Account")
                        }
                    }
                    AuthTab.RESET -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onRequestReset(email) },
                                enabled = !uiState.isAuthBusy && email.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reset_request_button")
                            ) {
                                Text("Send Token")
                            }
                            Button(
                                onClick = { onConfirmReset(email, resetToken, password) },
                                enabled = !uiState.isAuthBusy && email.isNotBlank() && resetToken.isNotBlank() && password.length >= 8,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reset_confirm_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
                            ) {
                                Text("Reset Password")
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onGuestContinue,
                    enabled = !uiState.isAuthBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guest_mode_button")
                ) {
                    Text("Continue in Guest Mode")
                }
            }
        }
    }
}
