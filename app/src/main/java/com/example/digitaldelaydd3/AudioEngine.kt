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
        onFinished: () -> Unit
    ) {
        stopPreview()
        isPreviewing = true
        thread {
            try {
                val dec = decodePcm(ctx, uri, 60) ?: return@thread
                val wet = applyEffects(dec.first, dec.second, dec.third, eLevel, fBack, delayMs, ancEnabled, ancLevel, true)
                val chMask = if (dec.third == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                val minBuf = AudioTrack.getMinBufferSize(dec.second, chMask, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(dec.second).setChannelMask(chMask).build())
                    .setBufferSizeInBytes(minBuf)
                    .build()
                previewTrack = track
                track.play()
                var offset = 0
                while (isPreviewing && offset < wet.size) {
                    val len = minOf(1024, wet.size - offset)
                    track.write(wet, offset, len)
                    offset += len
                }
            } catch (_: Exception) {
            } finally {
                stopPreview()
                onFinished()
            }
        }
    }

    fun exportEchoWav(
        ctx: Context,
        uri: Uri,
        eLevel: Float,
        fBack: Float,
        delayMs: Int,
        ancEnabled: Boolean,
        ancLevel: Float,
        onSaved: (Uri?, String) -> Unit
    ) {
        thread {
            val dec = decodePcm(ctx, uri, 300)
            if (dec == null) {
                onSaved(null, "Audio decode failed!")
                return@thread
            }
            val wet = applyEffects(dec.first, dec.second, dec.third, eLevel, fBack, delayMs, ancEnabled, ancLevel, true)
            val byteBuf = ByteBuffer.allocate(wet.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in wet) byteBuf.putShort(s)
            val name = "Baapu_Echo_${System.currentTimeMillis()}.wav"
            val savedUri = writeWavToMediaStore(ctx, byteBuf.array(), dec.second, dec.third, name)
            onSaved(savedUri, "Saved: Music/Baapu/$name")
        }
    }

    private fun decodePcm(ctx: Context, uri: Uri, maxSec: Int): Triple<ShortArray, Int, Int>? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(ctx, uri, null)
            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIdx = i
                    format = f
                    break
                }
            }
            if (trackIdx < 0 || format == null) return null
            extractor.selectTrack(trackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val maxSamples = sr * ch * maxSec

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val outSamples = ArrayList<Short>()
            val info = MediaCodec.BufferInfo()
            var isEOS = false
            while (!isEOS && outSamples.size < maxSamples) {
                val inIdx = codec.dequeueInputBuffer(5000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val sz = extractor.readSampleData(buf, 0)
                    if (sz < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 5000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val sb = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (sb.hasRemaining() && outSamples.size < maxSamples) {
                        outSamples.add(sb.get())
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
            codec.stop()
            codec.release()
            Triple(outSamples.toShortArray(), sr, ch)
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    fun writeWavToMediaStore(ctx: Context, pcmData: ByteArray, sampleRate: Int, channels: Int, fileName: String): Uri? {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + pcmData.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * 2)
            putShort((channels * 2).toShort())
            putShort(16)
            put("data".toByteArray())
            putInt(pcmData.size)
        }.array()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Baapu")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    ctx.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(header)
                        os.write(pcmData)
                    }
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Baapu")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { fos ->
                    fos.write(header)
                    fos.write(pcmData)
                }
                Uri.fromFile(file)
            }
        } catch (_: Exception) {
            null
        }
    }
}
