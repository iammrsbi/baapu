package com.example.digitaldelaydd3

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(2400L)
                    showSplash = false
                }
                Crossfade(targetState = showSplash, animationSpec = tween(700), label = "splash") { isSplash ->
                    if (isSplash) SplashScreen() else Boss800App()
                }
            }
        }
    }
}

data class AppThemeConfig(
    val name: String,
    val bgColors: List<Color>,
    val accent: Color,
    val titleColor: Color,
    val glassTint: List<Color>
)

@Composable
fun Boss800App() {
    val ctx = LocalContext.current
    val engine = remember { LiveDelayEngine(ctx) }

    val themes = remember {
        listOf(
            AppThemeConfig(
                name = "Madina Emerald",
                bgColors = listOf(Color(0xFF041C15), Color(0xFF0B3B2D), Color(0xFF03120D)),
                accent = Color(0xFF00F5D4),
                titleColor = Color(0xFFF6E27F),
                glassTint = listOf(Color(0x441B4D3E), Color(0x330D2B22))
            ),
            AppThemeConfig(
                name = "Royal Gold",
                bgColors = listOf(Color(0xFF1A1408), Color(0xFF33260E), Color(0xFF120D04)),
                accent = Color(0xFFFFD166),
                titleColor = Color(0xFFFFF3B0),
                glassTint = listOf(Color(0x444A3714), Color(0x33261C0A))
            ),
            AppThemeConfig(
                name = "Midnight Cyan",
                bgColors = listOf(Color(0xFF06111C), Color(0xFF0F2942), Color(0xFF040B12)),
                accent = Color(0xFF00E5FF),
                titleColor = Color(0xFFE0F7FA),
                glassTint = listOf(Color(0x441E3A5F), Color(0x330F2036))
            )
        )
    }

    var themeIndex by remember { mutableIntStateOf(0) }
    val currentTheme = themes[themeIndex]

    val perms = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.READ_MEDIA_AUDIO)
        else add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }.toTypedArray()

    var hasPerm by remember {
        mutableStateOf(perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED })
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = it[Manifest.permission.RECORD_AUDIO] == true
    }

    var tab by remember { mutableIntStateOf(0) }
    var bypassed by remember { mutableStateOf(true) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingFile by remember { mutableStateOf(false) }
    var outRoute by remember { mutableIntStateOf(0) }

    var eLevel by remember { mutableFloatStateOf(0.55f) }
    var fBack by remember { mutableFloatStateOf(0.35f) }
    var dTime by remember { mutableFloatStateOf(0.45f) }
    var mode by remember { mutableIntStateOf(1) }
    var ancEnabled by remember { mutableStateOf(true) }
    var ancLevel by remember { mutableFloatStateOf(0.65f) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("No Audio File Selected") }
    var statusMsg by remember { mutableStateOf("Ready • Use Earphones for Best Echo") }
    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }
    fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }
    var waveList by remember { mutableStateOf(List(48) { i -> abs(sin(i * 0.25f)) * 0.65f + 0.12f }) }

    val modes = listOf("STD", "ANALOG", "TAPE", "HOLD")
    val routes = listOf("EARPHONE", "SPEAKER", "BLUETOOTH")
    val maxMs = when (mode) {
        0 -> 150
        1 -> 350
        else -> 800
    }
    val ms = (15 + dTime * (maxMs - 15)).roundToInt()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            var displayName = uri.lastPathSegment ?: "Selected_Audio.mp3"
            try {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx)
                    }
                }
            } catch (_: Exception) {}
            selectedFileName = displayName
            statusMsg = "Loaded: $displayName"
            AudioFileProcessor.analyzeWaveforms(ctx, uri, eLevel, fBack, ms, ancEnabled, ancLevel) { _, after ->
                if (after.isNotEmpty()) waveList = after
            }
        }
    }

    LaunchedEffect(eLevel, fBack, ms, mode, ancEnabled, ancLevel, outRoute) {
        engine.effectLevel = eLevel
        engine.feedback = fBack
        engine.delayTimeMs = ms
        engine.holdMode = (mode == 3)
        engine.ancEnabled = ancEnabled
        engine.ancLevel = ancLevel
        engine.outRoute = outRoute
    }

    LaunchedEffect(bypassed, tab) {
        var tick = 0f
        while (!bypassed && tab == 0) {
            tick += 0.35f
            val lvl = (engine.currentInputLevel * 2.5f).coerceIn(0.08f, 1f)
            waveList = List(48) { i ->
                val shape = abs(sin(i * 0.22f + tick))
                (shape * lvl).coerceIn(0.08f, 1f)
            }
            delay(65L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.stop()
            AudioFileProcessor.stopPreview()
        }
    }

    val bgInf = rememberInfiniteTransition(label = "bgInf")
    val shift by bgInf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgShift"
    )
    val pulse by bgInf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = currentTheme.bgColors,
                    start = Offset(0f, 0f),
                    end = Offset(300f + 900f * shift, 2000f - 700f * shift)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gulame Mustafa",
                        color = currentTheme.titleColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "BAAPU DIGITAL DELAY",
                        color = currentTheme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x55000000))
                        .border(1.dp, currentTheme.accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable { themeIndex = (themeIndex + 1) % themes.size }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🎨 ${currentTheme.name}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x55000000))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(20.dp))
                    .padding(4.dp)
            ) {
                listOf("🎙️ LIVE MIC", "📁 FILE UPLOAD").forEachIndexed { idx, title ->
                    val sel = tab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) currentTheme.accent else Color.Transparent)
                            .clickable {
                                if (idx == 0) {
                                    AudioFileProcessor.stopPreview()
                                    isPlayingFile = false
                                } else if (!bypassed) {
                                    if (isRecording) {
                                        engine.stopAndSaveRecordingWav { u, msg ->
                                            if (u != null) lastSavedUri = u
                                            statusMsg = msg
                                        }
                                    }
                                    engine.stop()
                                    bypassed = true
                                    isRecording = false
                                    statusMsg = "Live echo band hai"
                                }
                                tab = idx
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (sel) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Waveform + status
            GlassCard(
                currentTheme,
                borderColor = if (!bypassed) currentTheme.accent.copy(alpha = 0.25f + 0.65f * pulse) else Color(0x33FFFFFF)
            ) {
                Waveform(waveList, currentTheme.accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = statusMsg,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            // Presets
            GlassCard(currentTheme) {
                Text("PRESETS", color = currentTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppRepository.presets.forEach { p ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x33FFFFFF))
                                .border(1.dp, currentTheme.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    mode = p.modeIdx
                                    val mx = when (p.modeIdx) {
                                        0 -> 150
                                        1 -> 350
                                        else -> 800
                                    }
                                    eLevel = p.eLevel
                                    fBack = p.fBack
                                    dTime = ((p.dTimeMs - 15f) / (mx - 15f)).coerceIn(0f, 1f)
                                    statusMsg = "Preset: ${p.name}"
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(p.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Knobs + mode
            GlassCard(currentTheme) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Knob("EFFECT", "${(eLevel * 100).roundToInt()}%", eLevel, currentTheme.accent) { eLevel = it }
                    Knob("FEEDBACK", "${(fBack * 100).roundToInt()}%", fBack, currentTheme.accent) { fBack = it }
                    Knob("TIME", "$ms ms", dTime, currentTheme.accent) { dTime = it }
                }
                Spacer(Modifier.height(12.dp))
                Text("MODE", color = currentTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                ChipRow(modes, mode, currentTheme.accent) { mode = it }
            }

            Spacer(Modifier.height(12.dp))

            // ANC
            GlassCard(currentTheme) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NOISE CANCEL (ANC)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = ancEnabled,
                        onCheckedChange = { ancEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = currentTheme.accent,
                            checkedThumbColor = Color.Black
                        )
                    )
                }
                if (ancEnabled) {
                    Slider(
                        value = ancLevel,
                        onValueChange = { ancLevel = it },
                        colors = SliderDefaults.colors(
                            thumbColor = currentTheme.accent,
                            activeTrackColor = currentTheme.accent
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (tab == 0) {
                // ---------- LIVE MIC ----------
                GlassCard(currentTheme) {
                    Text("OUTPUT", color = currentTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ChipRow(routes, outRoute, currentTheme.accent) { outRoute = it }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠️ Bina earphone ke seeti (feedback) bajegi! Wired earphone lagayein.",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp
                    )
                    if (!bypassed) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Output badalne ke liye echo band karke dobara start karein.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                ActionButton(
                    text = when {
                        !hasPerm -> "🔑 ALLOW MIC PERMISSION"
                        bypassed -> "▶ START LIVE ECHO"
                        else -> "■ STOP LIVE ECHO"
                    },
                    color = currentTheme.accent,
                    pulse = hasPerm && !bypassed
                ) {
                    if (!hasPerm) {
                        permLauncher.launch(perms)
                    } else if (bypassed) {
                        engine.outRoute = outRoute
                        engine.start()
                        bypassed = false
                        statusMsg = "LIVE ECHO ON • $ms ms"
                    } else {
                        if (isRecording) {
                            engine.stopAndSaveRecordingWav { u, msg ->
                                if (u != null) lastSavedUri = u
                                statusMsg = msg
                            }
                        }
                        engine.stop()
                        bypassed = true
                        isRecording = false
                        statusMsg = "Live echo band hai"
                    }
                }

                if (!bypassed) {
                    Spacer(Modifier.height(10.dp))
                    ActionButton(
                        text = if (isRecording) "💾 SAVE RECORDING" else "● RECORD WAV",
                        color = if (isRecording) Color(0xFFE53935) else Color(0x66FFFFFF),
                        textColor = Color.White
                    ) {
                        if (!isRecording) {
                            engine.startRecordingWav()
                            isRecording = true
                            statusMsg = "Recording chal rahi hai..."
                        } else {
                            isRecording = false
                            engine.stopAndSaveRecordingWav { u, msg ->
                                if (u != null) lastSavedUri = u
                                statusMsg = msg
                                toast(msg)
                            }
                        }
                    }
                }
            } else {
                // ---------- FILE UPLOAD ----------
                GlassCard(currentTheme) {
                    Text("SELECTED FILE", color = currentTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        selectedFileName,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(12.dp))

                ActionButton("📁 SELECT AUDIO FILE", currentTheme.accent) {
                    filePicker.launch("audio/*")
                }

                Spacer(Modifier.height(10.dp))

                ActionButton(
                    text = if (isPlayingFile) "■ STOP PREVIEW" else "▶ PREVIEW ECHO",
                    color = Color(0x66FFFFFF),
                    textColor = Color.White,
                    enabled = selectedUri != null
                ) {
                    selectedUri?.let { u ->
                        if (isPlayingFile) {
                            AudioFileProcessor.stopPreview()
                            isPlayingFile = false
                        } else {
                            isPlayingFile = true
                            AudioFileProcessor.playPreview(ctx, u, eLevel, fBack, ms, ancEnabled, ancLevel) {
                                isPlayingFile = false
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                ActionButton(
                    text = "💾 SAVE ECHO FILE",
                    color = Color(0x66FFFFFF),
                    textColor = Color.White,
                    enabled = selectedUri != null
                ) {
                    selectedUri?.let { u ->
                        statusMsg = "Echo file ban rahi hai..."
                        AudioFileProcessor.exportEchoWav(ctx, u, eLevel, fBack, ms, ancEnabled, ancLevel) { saved, msg ->
                            if (saved != null) lastSavedUri = saved
                            statusMsg = msg
                            toast(msg)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                ActionButton(
                    text = "SHARE ON WHATSAPP",
                    color = Color(0xFF25D366),
                    enabled = lastSavedUri != null
                ) {
                    lastSavedUri?.let { u ->
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, u)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            ctx.startActivity(
                                android.content.Intent(send)
                                    .setPackage("com.whatsapp")
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            try {
                                ctx.startActivity(
                                    android.content.Intent.createChooser(send, "Share")
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                toast("Share nahi ho paya")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun GlassCard(
    theme: AppThemeConfig,
    borderColor: Color = Color(0x33FFFFFF),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(theme.glassTint))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun Waveform(values: List<Float>, accent: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        val n = values.size.coerceAtLeast(1)
        val bw = size.width / n
        values.forEachIndexed { i, v ->
            val h = v.coerceIn(0.05f, 1f) * size.height
            drawRoundRect(
                color = accent.copy(alpha = 0.9f),
                topLeft = Offset(i * bw + bw * 0.2f, (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(bw * 0.6f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw * 0.3f)
            )
        }
    }
}

@Composable
fun Knob(label: String, valueText: String, value: Float, accent: Color, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .size(78.dp)
                .pointerInput(Unit) {
                    detectDragGestures { pc, drag ->
                        pc.consume()
                        change((current - drag.y / 350f + drag.x / 700f).coerceIn(0f, 1f))
                    }
                }
        ) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF1B1B1F), radius = r)
            drawCircle(accent.copy(alpha = 0.4f), radius = r, style = Stroke(3f))
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * current,
                useCenter = false,
                topLeft = Offset(8f, 8f),
                size = androidx.compose.ui.geometry.Size(size.width - 16f, size.height - 16f),
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
            rotate(degrees = -135f + 270f * current) {
                drawLine(
                    color = Color.White,
                    start = Offset(center.x, center.y - r * 0.25f),
                    end = Offset(center.x, center.y - r * 0.65f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(valueText, color = accent, fontSize = 11.sp)
    }
}

@Composable
fun ChipRow(items: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) accent else Color(0x33FFFFFF))
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t,
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    textColor: Color = Color.Black,
    enabled: Boolean = true,
    pulse: Boolean = false,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "btnInf")
    val s by inf.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "btnScale"
    )
    val scale = if (pulse) s else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else color.copy(alpha = 0.25f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SplashScreen() {
    val inf = rememberInfiniteTransition(label = "splashInf")
    val glow by inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splashGlow"
    )
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF041C15), Color(0xFF0B3B2D), Color(0xFF03120D)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(280.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF00F5D4).copy(alpha = glow * 0.6f), Color.Transparent)
                        ),
                        radius = size.minDimension / 2f * (0.85f + 0.15f * glow)
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(170.dp)
                        .graphicsLayer {
                            val sc = 0.6f + 0.4f * appear.value
                            scaleX = sc
                            scaleY = sc
                            alpha = appear.value
                        }
                        .clip(RoundedCornerShape(36.dp))
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Gulame Mustafa",
                color = Color(0xFFF6E27F),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.graphicsLayer { alpha = appear.value }
            )
            Text(
                text = "BAAPU DIGITAL DELAY",
                color = Color(0xFF00F5D4),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.graphicsLayer { alpha = appear.value }
            )
        }
    }
}

