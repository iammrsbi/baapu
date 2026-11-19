package com.example.digitaldelaydd3

import android.content.ContentValues
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

class LiveDelayEngine(private val ctx: Context) {
    @Volatile var isRunning = false
    @Volatile var isRecording = false
    @Volatile var effectLevel = 0.60f
    @Volatile var feedback = 0.35f
    @Volatile var delayTimeMs = 240
    @Volatile var holdMode = false
    @Volatile var outRoute = 0
    @Volatile var ancEnabled = true
    @Volatile var ancLevel = 0.65f
    @Volatile var currentInputLevel = 0f

    private val sr = 44100
    private val maxSamples = sr * 2
    private val buffer = FloatArray(maxSamples)
    private var writeIdx = 0
    private var thread: Thread? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recStream: ByteArrayOutputStream? = null

    fun startRecordingWav() {
        recStream = ByteArrayOutputStream()
        isRecording = true
    }

    fun stopAndSaveRecordingWav(onSaved: (Uri?, String) -> Unit) {
        isRecording = false
        val raw = recStream?.toByteArray() ?: ByteArray(0)
        recStream = null
        if (raw.isEmpty()) {
            onSaved(null, "Koi recording nahi mili!")
            return
        }
        thread {
            val name = "Baapu_Live_${System.currentTimeMillis()}.wav"
            val u = AudioFileProcessor.writeWavToMediaStore(ctx, raw, sr, 1, name)
            onSaved(u, "Saved: Music/Baapu/$name")
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (isRunning) return
        isRunning = true
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (outRoute) {
            0 -> { am.mode = AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = false }
            1 -> { am.mode = AudioManager.MODE_NORMAL; am.isSpeakerphoneOn = true }
            2 -> { am.mode = AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = false; am.startBluetoothSco(); am.isBluetoothScoOn = true }
        }
        thread = thread(start = true, priority = Thread.MAX_PRIORITY) {
            val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(512)
            val src = if (outRoute == 1 && !ancEnabled) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.VOICE_COMMUNICATION
            val rec = AudioRecord(src, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf)
            val aec = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } else null
            noiseSuppressor = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = ancEnabled } else null
            val usage = if (outRoute == 1) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION
            val play = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(minBuf)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            val data = ShortArray(256)
            var env = 0f
            var lp = 0f
            try {
                rec.startRecording()
                play.play()
                while (isRunning) {
                    noiseSuppressor?.enabled = ancEnabled && (ancLevel > 0.05f)
                    val n = rec.read(data, 0, data.size)
                    if (n > 0) {
                        val dSamples = ((delayTimeMs / 1000f) * sr).roundToInt().coerceIn(1, maxSamples - 1)
                        val safeFb = if (outRoute == 0) feedback.coerceAtMost(0.65f) else feedback
                        val gate = 0.004f + (ancLevel * 0.055f)
                        val att = (1f - ancLevel).coerceIn(0.02f, 1f)
                        val alpha = (1f - (ancLevel * 0.45f)).coerceIn(0.5f, 1f)
                        var peak = 0f
                        for (i in 0 until n) {
                            var dry = data[i] / 32768f
                            if (ancEnabled && ancLevel > 0.01f) {
                                lp += alpha * (dry - lp)
                                dry = lp
                                val lvl = abs(dry)
                                env = if (lvl > env) 0.25f * lvl + 0.75f * env else 0.015f * lvl + 0.985f * env
                                if (env < gate) dry *= (env / gate) * att
                            }
                            if (abs(dry) > peak) peak = abs(dry)
                            var rIdx = writeIdx - dSamples
                            if (rIdx < 0) rIdx += maxSamples
                            val delayed = buffer[rIdx]
                            buffer[writeIdx] = if (holdMode) delayed else (dry + delayed * safeFb).coerceIn(-1f, 1f)
                            val outVal = ((dry + delayed * effectLevel).coerceIn(-0.95f, 0.95f) * 32767f).toInt().toShort()
                            data[i] = outVal
                            if (isRecording) {
                                recStream?.write(outVal.toInt() and 0xFF)
                                recStream?.write((outVal.toInt() shr 8) and 0xFF)
                            }
                            writeIdx = (writeIdx + 1) % maxSamples
                        }
                        currentInputLevel = peak
                        play.write(data, 0, n)
                    }
                }
            } catch (_: Exception) {
            } finally {
                currentInputLevel = 0f
                try { noiseSuppressor?.release() } catch (_: Exception) {}
                noiseSuppressor = null
                try { aec?.release() } catch (_: Exception) {}
                try { rec.stop(); rec.release() } catch (_: Exception) {}
                try { play.stop(); play.release() } catch (_: Exception) {}
                if (outRoute == 2) {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
                am.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    fun stop() {
        isRunning = false
        isRecording = false
        try { thread?.join(500) } catch (_: Exception) {}
    }
}

object AudioFileProcessor {
    private var previewTrack: AudioTrack? = null
    @Volatile private var isPreviewing = false

    fun stopPreview() {
        isPreviewing = false
        try {
            previewTrack?.stop()
            previewTrack?.release()
        } catch (_: Exception) {}
        previewTrack = null
    }

    private fun applyEffects(
        samples: ShortArray,
        sr: Int,
        ch: Int,
        eLevel: Float,
        fBack: Float,
        delayMs: Int,
        ancEnabled: Boolean,
        ancLevel: Float,
        tail: Boolean
    ): ShortArray {
        val dSamples = ((delayMs / 1000f) * sr * ch).roundToInt().coerceAtLeast(1)
        val out = ShortArray(samples.size + if (tail) (sr * ch * 1.5f).roundToInt() else 0)
        val dBuf = FloatArray(dSamples)
        var dIdx = 0
        var env = 0f
        var lp = 0f
        val gate = 0.004f + (ancLevel * 0.05f)
        val att = (1f - ancLevel).coerceIn(0.02f, 1f)
        val alpha = (1f - (ancLevel * 0.45f)).coerceIn(0.5f, 1f)

        for (i in out.indices) {
            var dry = if (i < samples.size) samples[i] / 32768f else 0f
            if (ancEnabled && ancLevel > 0.01f && i < samples.size) {
                lp += alpha * (dry - lp)
                dry = lp
                val lvl = abs(dry)
                env = if (lvl > env) 0.25f * lvl + 0.75f * env else 0.015f * lvl + 0.985f * env
                if (env < gate) dry *= (env / gate) * att
            }
            val delayed = dBuf[dIdx]
            dBuf[dIdx] = (dry + delayed * fBack).coerceIn(-1f, 1f)
            out[i] = ((dry + delayed * eLevel).coerceIn(-0.98f, 0.98f) * 32767f).toInt().toShort()
            dIdx = (dIdx + 1) % dSamples
        }
        return out
    }

    fun analyzeWaveforms(
        ctx: Context,
        uri: Uri,
        eLevel: Float,
        fBack: Float,
        delayMs: Int,
        ancEnabled: Boolean,
        ancLevel: Float,
        onResult: (List<Float>, List<Float>) -> Unit
    ) {
        thread {
            val dec = decodePcm(ctx, uri, 15) ?: return@thread
            val wet = applyEffects(dec.first, dec.second, dec.third, eLevel, fBack, delayMs, ancEnabled, ancLevel, false)
            fun peaks(arr: ShortArray): List<Float> {
                val step = (arr.size / 48).coerceAtLeast(1)
                return List(48) { b ->
                    var mx = 0f
                    for (k in 0 until step) {
                        val idx = b * step + k
                        if (idx < arr.size) {
                            val v = abs(arr[idx] / 32768f)
                            if (v > mx) mx = v
                        }
                    }
                    mx.coerceIn(0.08f, 1f)
                }
            }
            onResult(peaks(dec.first), peaks(wet))
        }
    }

    fun playPreview(
        ctx: Context,
        uri: Uri,
        eLevel: Float,
        fBack: Float,
        delayMs: Int,
        ancEnabled: Boolean,
        ancLevel: Float,
