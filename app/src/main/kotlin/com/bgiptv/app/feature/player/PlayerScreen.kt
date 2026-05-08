package com.bgiptv.app.feature.player

import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.bgiptv.app.core.data.entity.SportEventEntity
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    streamId: Int,
    onOpenBrowser: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hudVisible by remember { mutableStateOf(true) }
    var hudTimer by remember { mutableStateOf(0) }

    LaunchedEffect(streamId) {
        viewModel.playChannel(streamId)
    }

    // Auto-hide HUD after 4s of inactivity
    LaunchedEffect(hudTimer) {
        if (hudVisible) {
            delay(4_000)
            hudVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    hudVisible = true
                    hudTimer++
                    when (event.key) {
                        Key.DirectionUp -> { viewModel.zapUp(); true }
                        Key.DirectionDown -> { viewModel.zapDown(); true }
                        Key.DirectionLeft -> { viewModel.prevGroup(); true }
                        Key.DirectionRight -> { viewModel.nextGroup(); true }
                        Key.Enter, Key.DirectionCenter -> { onOpenBrowser(); true }
                        Key.Back -> { onOpenBrowser(); true }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { viewModel.attachSurface(it) }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering indicator
        if (uiState.isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // HUD overlay
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerHud(
                channelName = uiState.channelName,
                programTitle = uiState.currentProgram,
                nextProgram = uiState.nextProgram,
                quality = uiState.quality,
                isLive = true,
            )
        }

        // Score overlay (V2 — when active)
        uiState.liveScoreAlert?.let { alert ->
            LiveScoreAlert(
                alert = alert,
                onSwitch = { viewModel.switchToAlert(alert) },
                onDismiss = { viewModel.dismissAlert() },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }

        // Error state
        if (uiState.hasError) {
            PlayerError(
                message = uiState.errorMessage,
                onRetry = { viewModel.retry() },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerHud(
    channelName: String,
    programTitle: String?,
    nextProgram: String?,
    quality: String,
    isLive: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                    Text("LIVE", fontSize = 11.sp, color = Color.Red, fontFamily = FontFamily.Monospace)
                }
                Text(channelName, fontSize = 16.sp, color = Color.White, fontFamily = FontFamily.Monospace)
            }
            Text(quality, fontSize = 12.sp, color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace)
        }

        if (programTitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(programTitle, fontSize = 13.sp, color = Color(0xCCFFFFFF))
        }
        if (nextProgram != null) {
            Text("Ensuite : $nextProgram", fontSize = 11.sp, color = Color(0x66FFFFFF))
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("↑↓ chaîne  ←→ groupe  OK browser", fontSize = 10.sp, color = Color(0x55FFFFFF), fontFamily = FontFamily.Monospace)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveScoreAlert(
    alert: ScoreAlert,
    onSwitch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(alert) {
        delay(8_000)
        visible = false
        onDismiss()
    }

    AnimatedVisibility(visible = visible, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }, modifier = modifier) {
        Column(
            modifier = Modifier
                .background(Color(0xEE1A1A2E), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(12.dp)
                .width(240.dp)
        ) {
            Text("⚡ BUT", fontSize = 11.sp, color = Color(0xFFFFCC00), fontFamily = FontFamily.Monospace)
            Text(
                "${alert.homeTeam} ${alert.homeScore} - ${alert.awayScore} ${alert.awayTeam}",
                fontSize = 14.sp,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
            )
            Text("${alert.minute}'  •  ${alert.scorer ?: alert.competition}", fontSize = 11.sp, color = Color(0xAAFFFFFF))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSwitch, modifier = Modifier.weight(1f)) {
                    Text("Switcher", fontSize = 11.sp)
                }
                Button(
                    onClick = { visible = false; onDismiss() },
                    colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Ignorer", fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerError(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("⚠ Erreur de lecture", fontSize = 18.sp, color = Color.White)
        if (message != null) Text(message, fontSize = 12.sp, color = Color(0x88FFFFFF))
        Button(onClick = onRetry) { Text("Réessayer") }
    }
}

data class ScoreAlert(
    val eventId: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val minute: Int,
    val scorer: String?,
    val competition: String,
    val channelId: Int,
)
