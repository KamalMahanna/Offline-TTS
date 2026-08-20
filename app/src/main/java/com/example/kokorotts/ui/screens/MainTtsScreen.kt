package com.example.kokorotts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kokorotts.data.ModelStatus
import com.example.kokorotts.data.TtsViewModel
import com.example.kokorotts.ui.components.AudioPlayerCard
import com.example.kokorotts.ui.components.MetricsCard
import com.example.kokorotts.ui.components.SidebarDrawerContent
import com.example.kokorotts.ui.components.SystemResourceGraph
import com.example.kokorotts.ui.components.TextInputCard
import com.example.kokorotts.ui.theme.BgDark
import com.example.kokorotts.ui.theme.CyanBright
import com.example.kokorotts.ui.theme.CyanPrimary
import com.example.kokorotts.ui.theme.EmeraldAccent
import com.example.kokorotts.ui.theme.PurpleAccent
import com.example.kokorotts.ui.theme.SurfaceDark
import com.example.kokorotts.ui.theme.TextPrimary
import com.example.kokorotts.ui.theme.TextSecondary
import com.example.kokorotts.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTtsScreen(
    viewModel: TtsViewModel,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val inputText by viewModel.inputText.collectAsState()
    val selectedSpeaker by viewModel.selectedSpeaker.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val lastMetrics by viewModel.lastMetrics.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val isAudioReady by viewModel.isAudioReady.collectAsState()

    val resourceHistory by viewModel.resourceHistory.collectAsState()
    val currentResource by viewModel.currentResource.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarDrawerContent(
                selectedSpeaker = selectedSpeaker,
                onSpeakerSelected = viewModel::onSpeakerSelected,
                speed = speed,
                onSpeedChanged = viewModel::onSpeedChanged,
                modelStatus = modelStatus,
                onRetryInitialization = viewModel::retryInitialization,
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kokoro TTS",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            // Active voice pill badge
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF131B2E))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(CyanPrimary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = selectedSpeaker.name.substringBefore(" ("),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CyanBright
                                    )
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Sidebar Menu",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Voice & Speed Settings",
                                tint = CyanBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BgDark,
                        titleContentColor = TextPrimary,
                        navigationIconContentColor = TextPrimary,
                        actionIconContentColor = CyanBright
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = BgDark
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(BgDark)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Text Field & Animated Generate Button
                TextInputCard(
                    inputText = inputText,
                    onInputTextChanged = viewModel::onInputTextChanged,
                    isGenerating = isGenerating,
                    isModelReady = (modelStatus is ModelStatus.Ready) && viewModel.isEngineReady,
                    onGenerateClicked = viewModel::generateAudio
                )

                // 2. Audio Player & Seekable Progress Bar
                AudioPlayerCard(
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    isAudioReady = isAudioReady,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSeek = viewModel::seekAudio
                )

                // 3. Generation Telemetry (Moved below Audio Player)
                MetricsCard(
                    metrics = lastMetrics,
                    isGenerating = isGenerating
                )

                // 4. System Resource Continuous 3-Line Graph
                SystemResourceGraph(
                    history = resourceHistory,
                    currentPoint = currentResource
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
