package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.network.QrCodeGenerator
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun RouterSignalRadarCanvas(
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_progress",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.48f

        // Subtle radial ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (isActive) 0.22f else 0.08f),
                    Color.Transparent,
                ),
                center = center,
                radius = maxRadius,
            ),
            radius = maxRadius,
            center = center,
        )

        // Static calibration rings
        listOf(0.45f, 0.7f, 0.95f).forEach { factor ->
            drawCircle(
                color = accentColor.copy(alpha = if (isActive) 0.18f else 0.09f),
                radius = maxRadius * factor,
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }

        // Animated expanding waves when broadcasting
        if (isActive) {
            listOf(0f, 0.5f).forEach { phaseOffset ->
                val phase = (pulseProgress + phaseOffset) % 1f
                val waveRadius = maxRadius * (0.28f + phase * 0.72f)
                val alpha = (1f - phase) * 0.55f
                drawCircle(
                    color = accentColor.copy(alpha = alpha),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
fun ThroughputWaveformCanvas(
    rxHistoryKbps: List<Float>,
    txHistoryKbps: List<Float>,
    rxColor: Color,
    txColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxVal = remember(rxHistoryKbps, txHistoryKbps) {
        maxOf(
            rxHistoryKbps.maxOrNull() ?: 0f,
            txHistoryKbps.maxOrNull() ?: 0f,
            64f,
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = 8.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(1f)

        // Horizontal grid lines
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            val y = padY + usableH * (1f - fraction)
            drawLine(
                color = gridColor.copy(alpha = 0.25f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        fun drawSeries(values: List<Float>, color: Color, fillAlpha: Float) {
            if (values.size < 2) return
            val stepX = w / (values.size - 1).coerceAtLeast(1)
            val linePath = Path()
            val fillPath = Path()

            values.forEachIndexed { idx, v ->
                val x = idx * stepX
                val normalized = (v / maxVal).coerceIn(0f, 1f)
                val y = padY + usableH * (1f - normalized)
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo((values.size - 1) * stepX, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = fillAlpha), Color.Transparent),
                ),
            )
            drawPath(
                path = linePath,
                color = color,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        drawSeries(txHistoryKbps, txColor, fillAlpha = 0.14f)
        drawSeries(rxHistoryKbps, rxColor, fillAlpha = 0.22f)
    }
}

@Composable
fun SignalBarsMeter(
    level: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val heights = listOf(8.dp, 12.dp, 16.dp, 20.dp)
        heights.forEachIndexed { index, barHeight ->
            val filled = index < level.coerceIn(0, 4)
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (filled) activeColor else inactiveColor.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
fun WifiQrCodeDialog(
    ssid: String,
    password: String,
    securityProtocol: String,
    proxyAddress: String?,
    onDismiss: () -> Unit,
) {
    val qrBitmap = remember(ssid, password) {
        val payload = QrCodeGenerator.buildWifiPayload(ssid, password)
        QrCodeGenerator.generateQrBitmap(payload, sizePx = 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("qr_dialog_close_button"),
            ) {
                Text("Готово")
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Подключение по QR",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Wi-Fi QR-код для $ssid",
                            modifier = Modifier
                                .size(210.dp)
                                .padding(12.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "SSID: $ssid",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Пароль: $password",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = securityProtocol,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (!proxyAddress.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Для выхода в 4G интернет в режиме Wi-Fi Моста укажите HTTP-прокси на клиентском устройстве: $proxyAddress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}
