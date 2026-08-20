package com.example.kokorotts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kokorotts.data.KokoroSpeaker
import com.example.kokorotts.data.KokoroSpeakerCatalog
import com.example.kokorotts.data.ModelStatus
import com.example.kokorotts.ui.theme.CyanBright
import com.example.kokorotts.ui.theme.CyanPrimary
import com.example.kokorotts.ui.theme.EmeraldAccent
import com.example.kokorotts.ui.theme.PurpleAccent
import com.example.kokorotts.ui.theme.SurfaceDark
import com.example.kokorotts.ui.theme.SurfaceVariantDark
import com.example.kokorotts.ui.theme.TextPrimary
import com.example.kokorotts.ui.theme.TextSecondary
import com.example.kokorotts.ui.theme.TextTertiary

@Composable
fun SidebarDrawerContent(
    selectedSpeaker: KokoroSpeaker,
    onSpeakerSelected: (KokoroSpeaker) -> Unit,
    speed: Float,
    onSpeedChanged: (Float) -> Unit,
    modelStatus: ModelStatus,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp),
        drawerContainerColor = Color(0xFF0C101D),
        drawerContentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(scrollState)
        ) {
            // Drawer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(CyanPrimary, PurpleAccent))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Voice Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Engine status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131B2E), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Kokoro-82M Engine",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                )
                when (modelStatus) {
                    is ModelStatus.Ready -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EmeraldAccent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ready",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldAccent
                                )
                            )
                        }
                    }
                    is ModelStatus.Loading -> {
                        Text(
                            text = "Loading...",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = CyanBright
                            )
                        )
                    }
                    else -> {
                        Text(
                            text = "Standby",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = TextTertiary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Speed Slider Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = CyanBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Speech Speed",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                // Reset button if speed changed
                if (speed != 1.0f) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSpeedChanged(1.0f) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset speed",
                            tint = CyanBright,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "1.0x",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = CyanBright,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed Slider Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131B2E), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rate Multiplier",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    )
                    Text(
                        text = "${String.format("%.1fx", speed)} ${
                            when {
                                speed < 0.9f -> "(Slow)"
                                speed > 1.1f -> "(Fast)"
                                else -> "(Normal)"
                            }
                        }",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanBright
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = speed,
                    onValueChange = onSpeedChanged,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanBright,
                        activeTrackColor = CyanPrimary,
                        inactiveTrackColor = SurfaceVariantDark
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "0.5x", style = TextStyle(fontSize = 10.sp, color = TextTertiary))
                    Text(text = "1.0x", style = TextStyle(fontSize = 10.sp, color = TextTertiary))
                    Text(text = "2.0x", style = TextStyle(fontSize = 10.sp, color = TextTertiary))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Voice Selection Section
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = PurpleAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kokoro Voices",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // List of voices
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131B2E), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KokoroSpeakerCatalog.speakers.forEach { speaker ->
                    val isSelected = speaker.id == selectedSpeaker.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) {
                                    Color(0xFF1E293B)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) CyanPrimary.copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSpeakerSelected(speaker) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = speaker.name,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CyanBright else TextPrimary
                                )
                            )
                            Text(
                                text = "${speaker.gender} • ${speaker.language}",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = TextTertiary
                                )
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
