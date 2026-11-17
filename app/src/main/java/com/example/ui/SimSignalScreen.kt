package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LatencyCheckState
import com.example.data.SimStateKind
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SimSignalScreen(
    state: RouterUiState,
    onTogglePinCellular: (Boolean) -> Unit,
    onToggleWifiLock: (Boolean) -> Unit,
    onToggleWakeLock: (Boolean) -> Unit,
    onRunLatencyCheck: () -> Unit,
    onOpenMobileSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cell = state.cellularTelemetry
    val traffic = state.trafficSnapshot
    val proxy = state.proxyTelemetry

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.sim_section_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        // SIM & 4G Radio Card
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SimCard,
                                    contentDescription = "SIM-карта",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = cell.carrierName,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = when (cell.simState) {
                                        SimStateKind.READY -> stringResource(R.string.sim_ready)
                                        SimStateKind.ABSENT -> stringResource(R.string.sim_absent)
                                        SimStateKind.PIN_REQUIRED -> "Требуется PIN SIM"
                                        SimStateKind.NO_PERMISSION -> stringResource(R.string.sim_unknown)
                                        else -> "Проверка SIM…"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (cell.simState == SimStateKind.READY) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            SignalBarsMeter(
                                level = cell.signalLevel,
                                activeColor = MaterialTheme.colorScheme.secondary,
                                inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cell.signalDbm?.let { "$it дБм" } ?: "Уровень ${cell.signalLevel}/4",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TelemetryBadge(
                            label = stringResource(R.string.label_network_type),
                            value = cell.networkTypeLabel,
                            modifier = Modifier.weight(1f),
                        )
                        TelemetryBadge(
                            label = "Интерфейс SIM",
                            value = cell.cellularInterfaceName ?: if (cell.isCellularConnected) "rmnet" else "Офлайн",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TelemetryBadge(
                            label = "Канал 4G",
                            value = when {
                                cell.isCellularValidated -> "Проверен"
                                cell.isCellularConnected -> "Подключён"
                                else -> "Не активен"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TelemetryBadge(
                            label = "4G Прокси-мост",
                            value = if (proxy.isRunning) {
                                "Порт ${proxy.port} (${proxy.activeConnections})"
                            } else {
                                "Ожидание"
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (cell.dnsServers.isNotEmpty()) {
                        Text(
                            text = "DNS SIM: ${cell.dnsServers.joinToString(", ")}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Live 4G Speed Waveform Card
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
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Монитор скорости 4G",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = "${FormatUtils.formatDataBytes(traffic.sessionRxBytes + traffic.sessionTxBytes)} всего",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    ThroughputWaveformCanvas(
                        rxHistoryKbps = traffic.rxHistoryKbps,
                        txHistoryKbps = traffic.txHistoryKbps,
                        rxColor = MaterialTheme.colorScheme.primary,
                        txColor = MaterialTheme.colorScheme.secondary,
                        gridColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TelemetryBadge(
                            label = stringResource(R.string.label_download),
                            value = FormatUtils.formatSpeed(traffic.rxBytesPerSec),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TelemetryBadge(
                            label = stringResource(R.string.label_upload),
                            value = FormatUtils.formatSpeed(traffic.txBytesPerSec),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (proxy.totalForwardedBytes > 0L) {
                        Text(
                            text = "Через 4G Прокси передано: ${FormatUtils.formatDataBytes(proxy.totalForwardedBytes)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }

        // 4G Link Optimizer & Latency Test
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.boost_controls_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    BoostToggleRow(
                        title = stringResource(R.string.boost_pin_cellular),
                        subtitle = stringResource(R.string.boost_pin_cellular_desc),
                        checked = state.profile.pinCellularLink,
                        onCheckedChange = onTogglePinCellular,
                        testTag = "toggle_pin_cellular",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    BoostToggleRow(
                        title = stringResource(R.string.boost_wifi_lock),
                        subtitle = stringResource(R.string.boost_wifi_lock_desc),
                        checked = state.profile.highPerfWifiLock,
                        onCheckedChange = onToggleWifiLock,
                        testTag = "toggle_wifi_lock",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    BoostToggleRow(
                        title = stringResource(R.string.boost_wake_lock),
                        subtitle = stringResource(R.string.boost_wake_lock_desc),
                        checked = state.profile.cpuWakeLock,
                        onCheckedChange = onToggleWakeLock,
                        testTag = "toggle_wake_lock",
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Real TCP RTT test over 4G SIM
                    Surface(
                        shape = RoundedCornerShape(14.dp),
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
                                    text = stringResource(R.string.label_latency),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                when (val lat = state.latencyState) {
                                    LatencyCheckState.Idle -> Text(
                                        text = "Нажмите для замера пинга (1.1.1.1)",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    LatencyCheckState.Measuring -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Text("Измерение…", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    is LatencyCheckState.Measured -> Text(
                                        text = "${lat.rttMs} мс (${if (lat.overCellular) "4G SIM" else "сеть ОС"})",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    is LatencyCheckState.Failed -> Text(
                                        text = lat.reason,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onRunLatencyCheck,
                                modifier = Modifier.testTag("latency_check_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Пинг")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenMobileSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_mobile_settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsInputAntenna,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_open_mobile_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
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
                color = accentColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BoostToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
