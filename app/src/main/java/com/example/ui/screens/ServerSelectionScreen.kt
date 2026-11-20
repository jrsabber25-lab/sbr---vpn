package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.example.data.model.VpnServer
import com.example.data.repository.BackendConnectionStatus
import com.example.ui.SbrUiState
import com.example.ui.theme.SbrOrange
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald

private enum class ServerFilterTab(val label: String) {
    ALL("All Deployed"),
    FAVORITES("Favorites"),
    RECENT("Recently Used")
}

@Composable
fun ServerSelectionScreen(
    uiState: SbrUiState,
    onSelectServer: (VpnServer) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAutoSelectFastest: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ServerFilterTab.ALL) }

    val favoriteIds = uiState.preferences.favoriteServerIds
    val recentIds = uiState.preferences.recentServerIds

    val filteredServers = remember(uiState.servers, searchQuery, selectedTab, favoriteIds, recentIds) {
        val base = when (selectedTab) {
            ServerFilterTab.ALL -> uiState.servers
            ServerFilterTab.FAVORITES -> uiState.servers.filter { it.id in favoriteIds }
            ServerFilterTab.RECENT -> recentIds.mapNotNull { id -> uiState.servers.firstOrNull { it.id == id } }
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim().lowercase()
            base.filter {
                it.countryName.lowercase().contains(q) ||
                    it.city.lowercase().contains(q) ||
                    it.countryCode.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VPN Locations",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Only verified, reachable servers are shown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("servers_refresh_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Servers")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .testTag("server_search_input"),
            placeholder = { Text("Search country or city…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAutoSelectFastest,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .height(48.dp)
                .testTag("auto_select_fastest_button"),
            colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
        ) {
            Icon(Icons.Rounded.Bolt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Auto Select Fastest Server", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ServerFilterTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("server_tab_${tab.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.isLoadingServers) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SbrOrange)
            }
        } else if (filteredServers.isEmpty()) {
            EmptyServerRegistryState(
                backendStatus = uiState.backendStatus,
                hasSearchFilter = searchQuery.isNotBlank() || selectedTab != ServerFilterTab.ALL,
                onOpenSettings = onOpenSettings,
                onRefresh = onRefresh
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredServers, key = { it.id }) { server ->
                    val isSelected = uiState.selectedServer?.id == server.id
                    val isFavorite = server.id in favoriteIds
                    ServerRowCard(
                        server = server,
                        isSelected = isSelected,
                        isFavorite = isFavorite,
                        onSelect = { onSelectServer(server) },
                        onToggleFavorite = { onToggleFavorite(server.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ServerRowCard(
    server: VpnServer,
    isSelected: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val latencyColor = when (val ms = server.measuredLatencyMs) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        in 0..80 -> StatusEmerald
        in 81..160 -> StatusAmber
        else -> StatusCrimson
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("server_card_${server.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) SbrOrange else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = server.flagEmoji,
                    style = MaterialTheme.typography.headlineMedium
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = server.countryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (server.isPremium) SbrOrange.copy(alpha = 0.16f) else StatusEmerald.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (server.isPremium) "PREMIUM" else "FREE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (server.isPremium) SbrOrange else StatusEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${server.city} • ${if (server.isOnline) "Online" else "Offline"} • Load ${server.loadPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = server.measuredLatencyMs?.let { "${it} ms" } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = latencyColor,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("favorite_toggle_${server.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (isFavorite) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyServerRegistryState(
    backendStatus: BackendConnectionStatus,
    hasSearchFilter: Boolean,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp)
            .padding(top = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (hasSearchFilter) {
                    "No Matching Deployed Servers"
                } else {
                    "No Active VPN Servers Reachable"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    hasSearchFilter -> "Try clearing your search filter or switching tabs."
                    backendStatus is BackendConnectionStatus.Unconfigured ->
                        "SBR VPN never lists fictional servers. Configure your deployed FastAPI backend URL in Settings to load live WireGuard nodes across supported regions (US, UK, Germany, Netherlands, France, Singapore, Japan, Canada, India)."
                    backendStatus is BackendConnectionStatus.Unreachable ->
                        "Could not reach your backend registry: ${backendStatus.error}"
                    else -> "Your backend returned 0 online WireGuard servers. Register and enable VPS nodes in the Admin Dashboard."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = SbrOrange)
                ) {
                    Text("Configure Backend URL")
                }
                OutlinedButton(onClick = onRefresh) {
                    Text("Retry")
                }
            }
        }
    }
}
