package com.focus.moment.ui.timer

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.moment.data.AppSettings
import com.focus.moment.data.model.LockMode
import com.focus.moment.data.model.SessionStatus
import com.focus.moment.data.model.TimerMode
import com.focus.moment.data.model.TimerPhase
import com.focus.moment.data.model.WhiteNoiseType
import com.focus.moment.service.FocusSessionState
import com.focus.moment.service.TimerService
import com.focus.moment.ui.theme.wallpaperOf
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(settings: AppSettings, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val state by FocusSessionState.state.collectAsState()

    // 屏幕常亮
    val activity = ctx as? Activity
    androidx.compose.runtime.DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // 无会话时自动返回（防误入）
    LaunchedEffect(state.phase) {
        if (state.phase == TimerPhase.IDLE) onExit()
    }

    // 计时中的返回键行为
    var showGiveUp by remember { mutableStateOf(false) }
    val lockMode = LockMode.from(settings.lockMode)
    BackHandler(enabled = state.phase != TimerPhase.IDLE) {
        when {
            state.phase == TimerPhase.FINISHED -> { TimerService.complete(ctx, SessionStatus.COMPLETED); onExit() }
            lockMode == LockMode.STRICT -> { /* 严格模式：返回键无效 */ }
            else -> showGiveUp = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 壁纸背景
        val wp = wallpaperOf(settings.timerWallpaper)
        Image(
            painter = painterResource(wp.res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (settings.wallpaperBlur > 0.5f) Modifier.blur(settings.wallpaperBlur.dp) else Modifier)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = settings.wallpaperDim.coerceIn(0f, 0.7f)))
        )

        when (state.phase) {
            TimerPhase.IDLE -> {}
            TimerPhase.FINISHED -> FinishedView(settings, onExit)
            TimerPhase.RUNNING -> RunningView(settings, onExit, showGiveUpRequest = { showGiveUp = true })
        }
    }

    if (showGiveUp && state.phase == TimerPhase.RUNNING) {
        AlertDialog(
            onDismissRequest = { showGiveUp = false },
            title = { Text("放弃专注？") },
            text = { Text("本次专注将被记录为「放弃」，仍会计入报告统计。") },
            confirmButton = {
                TextButton(onClick = {
                    showGiveUp = false
                    TimerService.complete(ctx, SessionStatus.ABANDONED)
                    onExit()
                }) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showGiveUp = false }) { Text("继续专注") } }
        )
    }
}

@Composable
private fun RunningView(settings: AppSettings, onExit: () -> Unit, showGiveUpRequest: () -> Unit) {
    val state by FocusSessionState.state.collectAsState()
    val ctx = LocalContext.current
    var pressing by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    var showNoise by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.weight(0.6f))
        // 标题区
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                state.category.label,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1
            )
        }

        Spacer(Modifier.weight(1f))

        // 时间与进度环
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val totalSec = state.plannedMinutes * 60L
            val remainingSec = if (state.mode == TimerMode.COUNTDOWN && state.endAt > 0) {
                ((state.endAt - state.nowTick).coerceAtLeast(0)) / 1000
            } else 0L
            val elapsedSec = ((state.nowTick - state.startedAt).coerceAtLeast(0)) / 1000
            val fraction = if (state.mode == TimerMode.COUNTDOWN && totalSec > 0) {
                1f - remainingSec.toFloat() / totalSec
            } else 0f

            Canvas(Modifier.size(280.dp)) {
                val stroke = 14f
                val d = size.minDimension - stroke
                val tl = androidx.compose.ui.geometry.Offset((size.width - d) / 2, (size.height - d) / 2)
                // 背景环
                drawArc(
                    color = Color.White.copy(alpha = 0.18f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = tl, size = androidx.compose.ui.geometry.Size(d, d),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // 正计时模式：旋转呼吸点缀
                if (state.mode == TimerMode.COUNTUP) {
                    rotate(degrees = (elapsedSec % 60) * 6f) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.5f),
                            startAngle = -90f, sweepAngle = 40f, useCenter = false,
                            topLeft = tl, size = androidx.compose.ui.geometry.Size(d, d),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                } else {
                    drawArc(
                        color = Color.White,
                        startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                        topLeft = tl, size = androidx.compose.ui.geometry.Size(d, d),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.mode == TimerMode.COUNTDOWN) {
                    Text(
                        com.focus.moment.data.TimeFmt.mmss(remainingSec),
                        color = Color.White,
                        fontSize = 64.sp
                    )
                } else {
                    Text(
                        com.focus.moment.data.TimeFmt.mmss(elapsedSec),
                        color = Color.White,
                        fontSize = 64.sp
                    )
                    Text("正计时中", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 操作区
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.mode == TimerMode.COUNTDOWN) {
                Button(
                    onClick = { TimerService.complete(ctx, SessionStatus.EARLY_FINISH); onExit() },
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                ) { Text("提前完成") }
            } else {
                Button(
                    onClick = { TimerService.complete(ctx, SessionStatus.COMPLETED); onExit() },
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                ) { Text("完成") }
            }

            // 放弃：长按 3 秒
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressing = true
                                try { awaitRelease() } finally { pressing = false }
                            }
                        )
                    }
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (pressing) "继续按住以放弃…" else "长按放弃",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (pressing) {
                        LinearProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // 白噪音入口
            OutlinedButton(
                onClick = { showNoise = true },
                modifier = Modifier.fillMaxWidth(0.7f).height(44.dp)
            ) {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("白噪音", color = Color.White)
            }
        }
        Spacer(Modifier.weight(0.4f))
    }

    // 长按 3 秒触发放弃确认（进度条同步填充）
    LaunchedEffect(pressing) {
        if (pressing) {
            holdProgress = 0f
            while (pressing && holdProgress < 1f) {
                delay(150)
                if (!pressing) break
                holdProgress = (holdProgress + 0.05f).coerceAtMost(1f)
            }
            if (holdProgress >= 1f) showGiveUpRequest()
        } else {
            holdProgress = 0f
        }
    }

    if (showNoise) {
        NoiseSheet(settings, onDismiss = { showNoise = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoiseSheet(settings: AppSettings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var selected by remember { mutableStateOf(WhiteNoiseType.from(settings.noiseDefault)) }
    var volume by remember { mutableStateOf(settings.noiseVolume) }
    var playing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text("白噪音", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhiteNoiseType.entries.forEach { t ->
                    FilterChip(
                        selected = selected == t,
                        onClick = {
                            selected = t
                            if (playing) TimerService.noiseChange(ctx, t, null)
                        },
                        label = { Text(t.label) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("音量", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        if (playing) TimerService.noiseChange(ctx, null, it)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (playing) {
                        TimerService.noiseStop(ctx)
                        playing = false
                    } else {
                        TimerService.noiseStart(ctx, selected, volume)
                        playing = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text(if (playing) "停止播放" else "播放") }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FinishedView(settings: AppSettings, onExit: () -> Unit) {
    val state by FocusSessionState.state.collectAsState()
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("专注完成！", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "本次专注 ${state.finishedSeconds / 60} 分钟",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { TimerService.complete(ctx, SessionStatus.COMPLETED); onExit() },
            modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
        ) { Text("停止提示音") }
    }
}
