package com.example.kokorotts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kokorotts.data.GenerationMetrics
import com.example.kokorotts.ui.theme.AmberAccent
import com.example.kokorotts.ui.theme.CyanBright
import com.example.kokorotts.ui.theme.EmeraldAccent
import com.example.kokorotts.ui.theme.SurfaceDark
import com.example.kokorotts.ui.theme.TextPrimary
import com.example.kokorotts.ui.theme.TextSecondary
import com.example.kokorotts.ui.theme.TextTertiary

@Composable
fun MetricsCard(
    metrics: GenerationMetrics?,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
        color = SurfaceDark,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Generation Telemetry",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                if (isGenerating) {
                    Text(
                        text = "Synthesizing...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            color = AmberAccent,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else if (metrics != null) {
                    val speedFactor = if (metrics.rtf > 0) String.format("%.1fx realtime", 1f / metrics.rtf) else ""
                    Text(
                        text = speedFactor,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            color = EmeraldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (metrics == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090D16), RoundedCornerShape(12.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isGenerating) "Running Kokoro ONNX neural inference..." else "Generate speech to view timing and RTF telemetry",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(
                        title = "Generation Time",
                        value = "${String.format("%.2f", metrics.latencyMs / 1000f)}s",
                        subValue = "${metrics.latencyMs} ms",
                        icon = Icons.Default.Timer,
                        color = AmberAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "Audio Duration",
                        value = "${String.format("%.2f", metrics.audioDurationSeconds)}s",
                        subValue = "${metrics.sampleRate / 1000} kHz PCM",
                        icon = Icons.Default.GraphicEq,
                        color = CyanBright,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "Real-Time Factor",
                        value = String.format("%.2fx", metrics.rtf),
                        subValue = if (metrics.rtf > 0) "${String.format("%.1f", 1f / metrics.rtf)}x faster" else "-",
                        icon = Icons.Default.Bolt,
                        color = EmeraldAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF090D16), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = value,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )

        Text(
            text = subValue,
            style = TextStyle(
                fontSize = 10.sp,
                color = TextTertiary,
                fontWeight = FontWeight.Normal
            )
        )
    }
}
