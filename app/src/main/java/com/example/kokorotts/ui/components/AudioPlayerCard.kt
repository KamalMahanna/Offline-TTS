package com.example.kokorotts.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kokorotts.ui.theme.CyanBright
import com.example.kokorotts.ui.theme.CyanPrimary
import com.example.kokorotts.ui.theme.PurpleAccent
import com.example.kokorotts.ui.theme.SurfaceDark
import com.example.kokorotts.ui.theme.SurfaceVariantDark
import com.example.kokorotts.ui.theme.TextPrimary
import com.example.kokorotts.ui.theme.TextSecondary
import com.example.kokorotts.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerCard(
    isPlaying: Boolean,
    currentPositionMs: Int,
    durationMs: Int,
    isAudioReady: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isUserDragging by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1)
    val currentPosition = if (isUserDragging) {
        (dragSliderValue * safeDuration).toInt()
    } else {
        currentPositionMs.coerceIn(0, safeDuration)
    }

    val sliderProgress = if (isUserDragging) {
        dragSliderValue
    } else {
        (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = CyanBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Audio Player",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                // Audio status chip
                Text(
                    text = if (!isAudioReady) "No audio loaded" else if (isPlaying) "Playing..." else "Paused",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        color = if (isPlaying) CyanBright else TextTertiary
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Visualizer Bars
            if (isAudioReady) {
                AnimatedWaveformBar(isPlaying = isPlaying)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Seekable Music Progress Bar (Slider)
            Slider(
                value = sliderProgress,
                onValueChange = { frac ->
                    isUserDragging = true
                    dragSliderValue = frac
                },
                onValueChangeFinished = {
                    val targetMs = (dragSliderValue * safeDuration).toInt()
                    onSeek(targetMs)
                    isUserDragging = false
                },
                enabled = isAudioReady,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = CyanBright,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = SurfaceVariantDark,
                    disabledThumbColor = Color(0xFF475569),
                    disabledActiveTrackColor = Color(0xFF334155),
                    disabledInactiveTrackColor = Color(0xFF1E293B)
                )
            )

            // Timestamps: Elapsed / Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimeMs(currentPosition),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAudioReady) CyanBright else TextTertiary
                    )
                )
                Text(
                    text = formatTimeMs(durationMs),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Play / Pause and Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Replay from beginning button
                FilledIconButton(
                    onClick = { onSeek(0) },
                    enabled = isAudioReady,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SurfaceVariantDark,
                        contentColor = TextPrimary,
                        disabledContainerColor = Color(0xFF161F30),
                        disabledContentColor = TextTertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Restart Audio",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Main Glowing Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isAudioReady) {
                                Brush.linearGradient(listOf(CyanPrimary, PurpleAccent))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        enabled = isAudioReady,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = TextTertiary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Skip +5s button
                FilledIconButton(
                    onClick = { onSeek((currentPositionMs + 5000).coerceAtMost(durationMs)) },
                    enabled = isAudioReady,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SurfaceVariantDark,
                        contentColor = TextPrimary,
                        disabledContainerColor = Color(0xFF161F30),
                        disabledContentColor = TextTertiary
                    )
                ) {
                    Text(
                        text = "+5s",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedWaveformBar(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = listOf(
        rememberWaveformHeight(infiniteTransition, 300, isPlaying),
        rememberWaveformHeight(infiniteTransition, 450, isPlaying),
        rememberWaveformHeight(infiniteTransition, 250, isPlaying),
        rememberWaveformHeight(infiniteTransition, 500, isPlaying),
        rememberWaveformHeight(infiniteTransition, 350, isPlaying),
        rememberWaveformHeight(infiniteTransition, 600, isPlaying),
        rememberWaveformHeight(infiniteTransition, 280, isPlaying),
        rememberWaveformHeight(infiniteTransition, 420, isPlaying),
        rememberWaveformHeight(infiniteTransition, 320, isPlaying),
        rememberWaveformHeight(infiniteTransition, 490, isPlaying),
        rememberWaveformHeight(infiniteTransition, 270, isPlaying),
        rememberWaveformHeight(infiniteTransition, 390, isPlaying),
        rememberWaveformHeight(infiniteTransition, 520, isPlaying),
        rememberWaveformHeight(infiniteTransition, 310, isPlaying),
        rememberWaveformHeight(infiniteTransition, 460, isPlaying),
        rememberWaveformHeight(infiniteTransition, 340, isPlaying)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { animHeight ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((animHeight * 22).dp.coerceAtLeast(3.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(CyanBright, PurpleAccent)
                        )
                    )
            )
        }
    }
}

@Composable
private fun rememberWaveformHeight(
    infiniteTransition: androidx.compose.animation.core.InfiniteTransition,
    durationMs: Int,
    isPlaying: Boolean
): Float {
    if (!isPlaying) return 0.2f
    val anim by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barHeight"
    )
    return anim
}

private fun formatTimeMs(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return String.format("%02d:%02d.%d", minutes, seconds, tenths)
}
