package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RouterProfile
import com.example.data.WifiBand
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ClientsConfigScreen(
    state: RouterUiState,
    onSaveProfile: (ssid: String, password: String, band: WifiBand, proxyPort: Int) -> Unit,
    onResetDefaults: () -> Unit,
    onRefreshClients: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ssidInput by remember { mutableStateOf(state.profile.ssid) }
    var passwordInput by remember { mutableStateOf(state.profile.password) }
    var bandInput by remember { mutableStateOf(state.profile.band) }
    var portInput by remember { mutableStateOf(state.profile.proxyPort.toString()) }

    LaunchedEffect(state.profile) {
        ssidInput = state.profile.ssid
        passwordInput = state.profile.password
        bandInput = state.profile.band
        portInput = state.profile.proxyPort.toString()
    }

    val isPasswordValid = passwordInput.trim().length >= 8
    val parsedPort = portInput.toIntOrNull()
    val isPortValid = parsedPort != null && parsedPort in 1024..65535
    val isSsidValid = ssidInput.trim().isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Connected Clients Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${stringResource(R.string.clients_title)} (${state.connectedClients.size})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        IconButton(
                            onClick = onRefreshClients,
                            modifier = Modifier.testTag("refresh_clients_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                    }

                    if (state.connectedClients.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Devices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    text = stringResource(R.string.clients_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        state.connectedClients.forEach { client ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = client.name,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = listOfNotNull(client.ipAddress, client.macAddress)
                                                .joinToString(" • ")
                                                .ifEmpty { client.source },
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = client.source,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active OS Interfaces
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lan,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.interfaces_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (state.activeInterfaces.isEmpty()) {
                        Text(
                            text = "Активных IPv4 интерфейсов не обнаружено.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.activeInterfaces.forEach { nif ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = nif.name,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (nif.isHotspotCandidate) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = nif.ipv4Address,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Editable Router Profile Card (defaults to MyHotspot / sw22vmi55xr11 / WPA2-PSK)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.config_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = RouterProfile.DEFAULT_SECURITY,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    OutlinedTextField(
                        value = ssidInput,
                        onValueChange = { ssidInput = it },
                        label = { Text(stringResource(R.string.label_ssid)) },
                        singleLine = true,
                        isError = !isSsidValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ssid_input"),
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("${stringResource(R.string.label_password)} (WPA2-PSK, от 8 симв.)") },
                        singleLine = true,
                        isError = !isPasswordValid,
                        supportingText = if (!isPasswordValid) {
                            { Text("Для WPA2-PSK требуется минимум 8 символов") }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Порт 4G Прокси-шлюза (1024–65535)") },
                        singleLine = true,
                        isError = !isPortValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_port_input"),
                    )

                    Text(
                        text = stringResource(R.string.label_band),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WifiBand.entries.forEach { band ->
                            FilterChip(
                                selected = bandInput == band,
                                onClick = { bandInput = band },
                                label = { Text(band.label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.testTag("band_chip_${band.name.lowercase()}"),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onResetDefaults,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reset_defaults_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_reset_defaults))
                        }

                        Button(
                            onClick = {
                                if (isSsidValid && isPasswordValid && isPortValid) {
                                    onSaveProfile(ssidInput, passwordInput, bandInput, parsedPort!!)
                                }
                            },
                            enabled = isSsidValid && isPasswordValid && isPortValid,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_profile_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_save_profile))
                        }
                    }
                }
            }
        }

        // Permissions Diagnostics Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.permissions_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    PermissionStatusRow(
                        label = "Управление Wi-Fi (Nearby / Direct)",
                        granted = state.permissions.nearbyWifiGranted,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    PermissionStatusRow(
                        label = "Состояние SIM и 4G сети",
                        granted = state.permissions.phoneStateGranted,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    PermissionStatusRow(
                        label = "Уведомления фонового роутера",
                        granted = state.permissions.notificationsGranted,
                    )

                    if (!state.permissions.nearbyWifiGranted ||
                        !state.permissions.phoneStateGranted ||
                        !state.permissions.notificationsGranted
                    ) {
                        Button(
                            onClick = onRequestPermissions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_permissions_config_button"),
                        ) {
                            Text(stringResource(R.string.action_grant_permissions))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = if (granted) "Выдано" else "Не выдано",
                tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (granted) "Разрешено" else "Требуется",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
