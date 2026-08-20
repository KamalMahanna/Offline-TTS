package com.example.kokorotts.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kokorotts.data.ResourceDataPoint
import com.example.kokorotts.ui.theme.CpuLineColor
import com.example.kokorotts.ui.theme.MemoryLineColor
import com.example.kokorotts.ui.theme.SurfaceDark
import com.example.kokorotts.ui.theme.TempLineColor
import com.example.kokorotts.ui.theme.TextPrimary
import com.example.kokorotts.ui.theme.TextSecondary

@Composable
fun SystemResourceGraph(
    history: List<ResourceDataPoint>,
    currentPoint: ResourceDataPoint,
    modifier: Modifier = Modifier
) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by pulseTransition.animateFloat(
        initialValue = 4f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
        color = SurfaceDark,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with title and live telemetry badge (subheading line removed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Telemetry",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                // Live status pulsing pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Value Cards (Temp, CPU, Memory - memory MB count removed, percentage retained)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    icon = Icons.Default.Thermostat,
                    name = "Temp",
                    value = String.format("%.1f°C", currentPoint.temperatureCelsius),
                    color = TempLineColor,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    icon = Icons.Default.Speed,
                    name = "CPU",
                    value = String.format("%.0f%%", currentPoint.cpuPercent),
                    color = CpuLineColor,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    icon = Icons.Default.Memory,
                    name = "Memory",
                    value = String.format("%.0f%%", currentPoint.memoryPercent),
                    color = MemoryLineColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Canvas Graph Area (3-line continuous plot with no axis labels)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF090D16), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchX = offset.x
                                tryAwaitRelease()
                                touchX = null
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> touchX = offset.x },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null },
                            onDrag = { change, _ ->
                                touchX = change.position.x
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val paddingLeft = 10f
                    val paddingRight = 10f
                    val paddingTop = 12f
                    val paddingBottom = 12f

                    val chartWidth = width - paddingLeft - paddingRight
                    val chartHeight = height - paddingTop - paddingBottom

                    // Draw subtle Horizontal Grid Lines (No axis labels)
                    val yLevels = listOf(0f, 25f, 50f, 75f, 100f)
                    for (level in yLevels) {
                        val y = paddingTop + chartHeight * (1f - (level / 100f))
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(paddingLeft, y),
                            end = Offset(width - paddingRight, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    if (history.isNotEmpty()) {
                        val maxPoints = 60
                        val stepX = chartWidth / (maxPoints - 1).coerceAtLeast(1)

                        // Function to convert data point to chart coords
                        fun getCoordinates(valueSupplier: (ResourceDataPoint) -> Float): List<Offset> {
                            val startIndex = (maxPoints - history.size).coerceAtLeast(0)
                            return history.mapIndexed { idx, point ->
                                val x = paddingLeft + (startIndex + idx) * stepX
                                val value = valueSupplier(point).coerceIn(0f, 100f)
                                val y = paddingTop + chartHeight * (1f - (value / 100f))
                                Offset(x, y)
                            }
                        }

                        // Temperature mapped: 20°C = 0%, 80°C = 100%
                        fun tempToPercent(tempC: Float): Float {
                            return ((tempC - 20f) / (80f - 20f) * 100f).coerceIn(0f, 100f)
                        }

                        val tempCoords = getCoordinates { tempToPercent(it.temperatureCelsius) }
                        val memCoords = getCoordinates { it.memoryPercent }
                        val cpuCoords = getCoordinates { it.cpuPercent }

                        // Draw continuous curved 3 lines with gradient glow fills
                        drawMetricLine(
                            coords = tempCoords,
                            lineColor = TempLineColor,
                            chartBottom = paddingTop + chartHeight,
                            pulseRadius = pulseRadius
                        )

                        drawMetricLine(
                            coords = memCoords,
                            lineColor = MemoryLineColor,
                            chartBottom = paddingTop + chartHeight,
                            pulseRadius = pulseRadius
                        )

                        drawMetricLine(
                            coords = cpuCoords,
                            lineColor = CpuLineColor,
                            chartBottom = paddingTop + chartHeight,
                            pulseRadius = pulseRadius
                        )

                        // Draw Touch Inspection Crosshair if active
                        touchX?.let { tx ->
                            val clampedX = tx.coerceIn(paddingLeft, paddingLeft + chartWidth)
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(clampedX, paddingTop),
                                end = Offset(clampedX, paddingTop + chartHeight),
                                strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )

                            // Find nearest data point
                            val relX = clampedX - paddingLeft
                            val pointIndex = ((relX / chartWidth) * (history.size - 1)).toInt().coerceIn(0, history.size - 1)
                            val inspectedPoint = history.getOrNull(pointIndex)

                            if (inspectedPoint != null) {
                                val infoText = "CPU: ${inspectedPoint.cpuPercent.toInt()}% | RAM: ${inspectedPoint.memoryPercent.toInt()}% | ${String.format("%.1f°C", inspectedPoint.temperatureCelsius)}"
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = infoText,
                                    topLeft = Offset((clampedX - 90f).coerceIn(paddingLeft, width - 200f), paddingTop + 4f),
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        background = Color(0xDD0F172A)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawMetricLine(
    coords: List<Offset>,
    lineColor: Color,
    chartBottom: Float,
    pulseRadius: Float
) {
    if (coords.isEmpty()) return

    if (coords.size == 1) {
        drawCircle(color = lineColor, radius = 4f, center = coords[0])
        return
    }

    val path = Path()
    val fillPath = Path()

    path.moveTo(coords[0].x, coords[0].y)
    fillPath.moveTo(coords[0].x, chartBottom)
    fillPath.lineTo(coords[0].x, coords[0].y)

    for (i in 0 until coords.size - 1) {
        val p0 = coords[i]
        val p1 = coords[i + 1]
        val controlX = (p0.x + p1.x) / 2f

        path.cubicTo(
            x1 = controlX, y1 = p0.y,
            x2 = controlX, y2 = p1.y,
            x3 = p1.x, y3 = p1.y
        )
        fillPath.cubicTo(
            x1 = controlX, y1 = p0.y,
            x2 = controlX, y2 = p1.y,
            x3 = p1.x, y3 = p1.y
        )
    }

    val last = coords.last()
    fillPath.lineTo(last.x, chartBottom)
    fillPath.close()

    // Draw translucent gradient glow fill
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
            startY = 0f,
            endY = chartBottom
        )
    )

    // Draw crisp antialiased bezier line
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
    )

    // Draw live pulsing head marker at current point
    drawCircle(
        color = lineColor.copy(alpha = 0.35f),
        radius = pulseRadius,
        center = last
    )
    drawCircle(
        color = Color.White,
        radius = 3.5f,
        center = last
    )
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF090D16), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = name,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }
    }
}
