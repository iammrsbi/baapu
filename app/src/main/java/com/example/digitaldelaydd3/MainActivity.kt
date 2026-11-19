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
                Boss800App()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(currentTheme.bgColors))
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
                                        val u = selectedUri
                                        if (u == null) {
                                            Toast.makeText(ctx, "Pehle koi audio file select karein!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        statusMsg = "Exporting WAV..."
                                        AudioFileProcessor.exportEchoWav(ctx, u, eLevel, fBack, ms, ancEnabled, ancLevel) { _, msg ->
                                            statusMsg = msg
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x44FFFFFF)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(0.9f).height(46.dp)
                                ) {
                                    Text(
                                        text = "💾 SAVE WAV",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = statusMsg,
                        color = currentTheme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun BossKnob(
    label: String,
    valueText: String,
    value: Float,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(62.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / 180f
                        onValueChange((value + delta).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                drawArc(
                    color = Color(0x44FFFFFF),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = 135f,
                    sweepAngle = 270f * value,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawCircle(color = Color(0xFF181C20), radius = size.minDimension / 2.6f)
                rotate(degrees = -135f + (value * 270f)) {
                    drawLine(
                        color = accentColor,
                        start = Offset(size.width / 2f, size.height / 2f),
                        end = Offset(size.width / 2f, size.height * 0.18f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = valueText, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun GlowingWaveformView(
    amplitudes: List<Float>,
    active: Boolean,
    accentColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x44000000))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val barWidth = size.width / (amplitudes.size * 1.6f)
        val spacing = (size.width - barWidth * amplitudes.size) / (amplitudes.size + 1)
        amplitudes.forEachIndexed { idx, amp ->
            val barH = size.height * amp.coerceIn(0.08f, 1f)
            val x = spacing + idx * (barWidth + spacing)
            val y = (size.height - barH) / 2f
            drawRoundRect(
                color = if (active) accentColor else accentColor.copy(alpha = 0.35f),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
