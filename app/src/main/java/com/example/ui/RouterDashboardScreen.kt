package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HotspotBroadcastMode
import com.example.data.HotspotStatus
import com.example.data.SimStateKind
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun RouterDashboardScreen(
    state: RouterUiState,
    onSelectMode: (HotspotBroadcastMode) -> Unit,
    onToggleBroadcast: () -> Unit,
    onOpenSystemHotspotSettings: () -> Unit,
    onCopyText: (label: String, value: String) -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(true) }
    var showQrDialog by remember { mutableStateOf(false) }

    val status = state.hotspotStatus
    val isBroadcasting = status is HotspotStatus.Broadcasting || status is HotspotStatus.SystemHotspotDetected
    val isStarting = status is HotspotStatus.Starting

    val heroAccent by animateColorAsState(
        targetValue = when (status) {
            is HotspotStatus.Broadcasting -> MaterialTheme.colorScheme.primary
            is HotspotStatus.SystemHotspotDetected -> MaterialTheme.colorScheme.secondary
            is HotspotStatus.Starting -> MaterialTheme.colorScheme.tertiary
            is HotspotStatus.Error -> MaterialTheme.colorScheme.error
            is HotspotStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(),
        label = "hero_accent",
    )

    val displayedSsid = when (status) {
        is HotspotStatus.Broadcasting -> status.actualSsid
        else -> state.profile.ssid
    }
    val displayedPassphrase = when (status) {
        is HotspotStatus.Broadcasting -> status.actualPassphrase
        else -> state.profile.password
    }
    val displayedProxy = when (status) {
        is HotspotStatus.Broadcasting -> "${status.gatewayIp}:${status.proxyPort ?: state.profile.proxyPort}"
        is HotspotStatus.SystemHotspotDetected -> "${status.gatewayIp} (NAT)"
        else -> "192.168.49.1:${state.profile.proxyPort}"
    }

    if (showQrDialog) {
        WifiQrCodeDialog(
            ssid = displayedSsid,
            password = displayedPassphrase,
            securityProtocol = state.profile.securityProtocol,
            proxyAddress = if (status is HotspotStatus.Broadcasting && status.proxyPort != null) {
                "${status.gatewayIp}:${status.proxyPort}"
            } else {
                null
            },
            onDismiss = { showQrDialog = false },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top status bar with 4G SIM indicator
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.router_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = if (state.cellularTelemetry.isCellularConnected) {
                            "${state.cellularTelemetry.carrierName} • ${state.cellularTelemetry.networkTypeLabel}"
                        } else {
                            "SIM: ${state.cellularTelemetry.carrierName}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (state.cellularTelemetry.isCellularConnected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        if (state.cellularTelemetry.isCellularConnected) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SimCard,
                            contentDescription = "SIM 4G",
                            tint = if (state.cellularTelemetry.simState == SimStateKind.READY) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = state.cellularTelemetry.networkTypeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        SignalBarsMeter(
                            level = state.cellularTelemetry.signalLevel,
                            activeColor = MaterialTheme.colorScheme.secondary,
                            inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Permission warning banner if needed
        item {
            AnimatedVisibility(
                visible = !state.permissions.nearbyWifiGranted || !state.permissions.phoneStateGranted,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Нужны разрешения Wi-Fi и SIM",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = "Для запуска точки доступа и чтения параметров 4G SIM предоставьте системные разрешения.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        FilledTonalButton(
                            onClick = onRequestPermissions,
                            modifier = Modifier.testTag("grant_permissions_banner_button"),
                        ) {
                            Text("Выдать")
                        }
                    }
                }
            }
        }

        // Hero Hardware Power & Radar Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    1.dp,
                    if (isBroadcasting) heroAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    heroAccent.copy(alpha = if (isBroadcasting) 0.14f else 0.04f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(164.dp),
                        ) {
                            RouterSignalRadarCanvas(
                                isActive = isBroadcasting || isStarting,
                                accentColor = heroAccent,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isBroadcasting) heroAccent else MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = if (isBroadcasting) 12.dp else 2.dp,
                                border = BorderStroke(2.dp, heroAccent.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (state.profile.preferredMode == HotspotBroadcastMode.SYSTEM_TETHER) {
                                            onCopyText("Пароль", state.profile.password)
                                            onOpenSystemHotspotSettings()
                                        } else {
                                            onToggleBroadcast()
                                        }
                                    }
                                    .testTag("power_toggle_button"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PowerSettingsNew,
                                        contentDescription = if (isBroadcasting) "Остановить роутер" else "Включить роутер",
                                        tint = if (isBroadcasting) MaterialTheme.colorScheme.onPrimary else heroAccent,
                                        modifier = Modifier.size(38.dp),
                                    )
                                }
                            }
                        }

                        val statusLabel = when (status) {
                            is HotspotStatus.Broadcasting -> stringResource(R.string.status_broadcasting)
                            is HotspotStatus.SystemHotspotDetected -> stringResource(R.string.status_system_active)
                            is HotspotStatus.Starting -> stringResource(R.string.status_starting)
                            is HotspotStatus.Error -> stringResource(R.string.status_error)
                            is HotspotStatus.Idle -> stringResource(R.string.status_standby)
                        }

                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = heroAccent,
                        )

                        if (status is HotspotStatus.Error) {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                text = when (status) {
                                    is HotspotStatus.Broadcasting ->
                                        "Сеть $displayedSsid активна (${status.securityProtocol}) • 4G Прокси $displayedProxy"
                                    is HotspotStatus.SystemHotspotDetected ->
                                        "Интерфейс ${status.interfaceName} • Шлюз ${status.gatewayIp}"
                                    else ->
                                        "Готов к раздаче 4G интернета • ${state.profile.ssid} (${state.profile.securityProtocol})"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Primary CTA button
                        val isSystemMode = state.profile.preferredMode == HotspotBroadcastMode.SYSTEM_TETHER
                        Button(
                            onClick = {
                                if (isSystemMode) {
                                    onCopyText("Пароль", state.profile.password)
                                    onOpenSystemHotspotSettings()
                                } else {
                                    onToggleBroadcast()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBroadcasting && !isSystemMode) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("primary_router_action_button"),
                        ) {
                            Icon(
                                imageVector = if (isSystemMode) Icons.Rounded.Settings else Icons.Rounded.WifiTethering,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isSystemMode -> stringResource(R.string.action_open_tether_settings)
                                    isBroadcasting -> stringResource(R.string.action_stop_router)
                                    else -> stringResource(R.string.action_start_router)
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        // Quick live telemetry strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MiniMetricChip(
                                icon = Icons.Rounded.CellTower,
                                label = "RX ${FormatUtils.formatSpeed(state.trafficSnapshot.rxBytesPerSec)}",
                                modifier = Modifier.weight(1f),
                            )
                            MiniMetricChip(
                                icon = Icons.Rounded.Devices,
                                label = "Клиенты: ${state.connectedClients.size}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // Mode Selection Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.mode_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                BroadcastModeOptionCard(
                    title = stringResource(R.string.mode_direct_bridge),
                    description = stringResource(R.string.mode_direct_bridge_desc),
                    selected = state.profile.preferredMode == HotspotBroadcastMode.DIRECT_WIFI_BRIDGE,
                    onClick = { onSelectMode(HotspotBroadcastMode.DIRECT_WIFI_BRIDGE) },
                    testTag = "mode_direct_bridge_card",
                )

                BroadcastModeOptionCard(
                    title = stringResource(R.string.mode_system_tether),
                    description = stringResource(R.string.mode_system_tether_desc),
                    selected = state.profile.preferredMode == HotspotBroadcastMode.SYSTEM_TETHER,
                    onClick = { onSelectMode(HotspotBroadcastMode.SYSTEM_TETHER) },
                    testTag = "mode_system_tether_card",
                )

                BroadcastModeOptionCard(
                    title = stringResource(R.string.mode_local_ap),
                    description = stringResource(R.string.mode_local_ap_desc),
                    selected = state.profile.preferredMode == HotspotBroadcastMode.LOCAL_ONLY_HOTSPOT,
                    onClick = { onSelectMode(HotspotBroadcastMode.LOCAL_ONLY_HOTSPOT) },
                    testTag = "mode_local_ap_card",
                )
            }
        }

        // Wi-Fi Credentials & QR Card
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
                                imageVector = Icons.Rounded.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.credentials_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = state.profile.securityProtocol,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }

                    CredentialRow(
                        label = stringResource(R.string.label_ssid),
                        value = displayedSsid,
                        onCopy = { onCopyText("SSID", displayedSsid) },
                        copyTag = "copy_ssid_button",
                    )

                    CredentialRow(
                        label = stringResource(R.string.label_password),
                        value = if (passwordVisible) displayedPassphrase else "•••••••••••••",
                        trailingAction = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Показать/скрыть пароль",
                                )
                            }
                        },
                        onCopy = { onCopyText("Пароль", displayedPassphrase) },
                        copyTag = "copy_password_button",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.label_gateway_proxy),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = displayedProxy,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.label_band),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.profile.band.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("show_qr_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_show_qr))
                        }

                        if (state.profile.preferredMode != HotspotBroadcastMode.SYSTEM_TETHER) {
                            FilledTonalButton(
                                onClick = onOpenSystemHotspotSettings,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_system_tether_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Настройки ОС")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BroadcastModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (selected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = "ВЫБРАН",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CredentialRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    copyTag: String,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailingAction != null) {
                trailingAction()
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.testTag(copyTag),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Копировать $label",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
