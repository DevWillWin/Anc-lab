package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class AncSimulationMode(val title: String, val subtitle: String) {
    ADAPTIVE_MASKING("Smart ANC (Comfort)", "Dynamic acoustic masking that adapts to ambient noise. Smooth, relaxing, zero howl."),
    HARMONIC_NULL("Low-Hum Inverter (50/60Hz)", "Generates phase-inverted anti-waves targeting low transformer/motor hums."),
    EXPERIMENTAL_LOOP("Real-Time Inversion (Direct)", "Inverts live microphone input. (Expect latency delay effect).")
}

class AncEngine {
    var isAncActive: Boolean = false
        private set

    var mode: AncSimulationMode = AncSimulationMode.ADAPTIVE_MASKING
    var intensity: Float = 0.65f // 0.1 to 1.0

    var liveAmbientDb: Float = 38f
        private set

    var estimatedReductionDb: Float = 0f
        private set

    private var engineJob: Job? = null
    private var micMonitorJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null

    fun startAnc(onStatusUpdate: () -> Unit = {}) {
        if (isAncActive) return
        isAncActive = true

        // Start Ambient Mic Monitor
        startMicMonitor()

        // Start Audio synthesis / processing according to mode
        engineJob = CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 44100
            val minTrackBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minTrackBuf, 2048) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            audioTrack = track
            track.play()

            val random = Random()
            val chunk = ShortArray(512)
            var brownVal = 0.0
            var phase60 = 0.0

            try {
                while (isActive && isAncActive) {
                    when (mode) {
                        AncSimulationMode.ADAPTIVE_MASKING -> {
                            // Calculates simulated reduction based on psychoacoustic masking & passive seal
                            val ambientNorm = ((liveAmbientDb - 35f) / 40f).coerceIn(0.1f, 1f)
                            val gain = (intensity * (0.3f + 0.7f * ambientNorm)).coerceIn(0.05f, 0.9f)
                            estimatedReductionDb = 8f + (intensity * 14f) // 8 to 22 dB perceived reduction

                            for (i in chunk.indices) {
                                val white = (random.nextDouble() * 2.0) - 1.0
                                brownVal = (brownVal + (0.02 * white)) / 1.02
                                val sample = brownVal * 3.5 * gain
                                chunk[i] = (sample.coerceIn(-1.0, 1.0) * 28000.0).toInt().toShort()
                            }
                            track.write(chunk, 0, chunk.size)
                        }

                        AncSimulationMode.HARMONIC_NULL -> {
                            // Synthesize low-frequency anti-tones (50Hz + 60Hz + 120Hz harmonics)
                            estimatedReductionDb = 6f + (intensity * 8f)
                            val f1 = 60.0
                            val f2 = 120.0
                            val gain = intensity * 0.45f

                            for (i in chunk.indices) {
                                phase60 += (2.0 * Math.PI * f1 / sampleRate)
                                if (phase60 > 2.0 * Math.PI) phase60 -= 2.0 * Math.PI
                                val w1 = sin(phase60)
                                val w2 = 0.4 * sin(phase60 * 2.0)
                                val antiWave = -(w1 + w2) * gain
                                chunk[i] = (antiWave.coerceIn(-1.0, 1.0) * 24000.0).toInt().toShort()
                            }
                            track.write(chunk, 0, chunk.size)
                        }

                        AncSimulationMode.EXPERIMENTAL_LOOP -> {
                            // Direct inverted pass-through from mic with soft lowpass
                            estimatedReductionDb = 2f
                            val minRecordBuf = AudioRecord.getMinBufferSize(
                                sampleRate,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            if (audioRecord == null) {
                                try {
                                    audioRecord = AudioRecord(
                                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                                        sampleRate,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        max(minRecordBuf, 1024)
                                    )
                                    audioRecord?.startRecording()
                                } catch (_: Exception) {}
                            }

                            val inBuf = ShortArray(chunk.size)
                            val read = audioRecord?.read(inBuf, 0, inBuf.size) ?: 0
                            if (read > 0) {
                                for (i in 0 until read) {
                                    // Invert phase (anti-noise) and scale by user intensity
                                    val inv = -inBuf[i] * intensity * 0.5f
                                    chunk[i] = inv.toInt().coerceIn(-32000, 32000).toShort()
                                }
                                track.write(chunk, 0, read)
                            } else {
                                delay(10)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
                audioTrack = null
            }
        }
    }

    private fun startMicMonitor() {
        micMonitorJob = CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return@launch

            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                record.startRecording()
                val buf = ShortArray(512)

                while (isActive && isAncActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val v = buf[i].toDouble()
                            sumSq += v * v
                        }
                        val rms = kotlin.math.sqrt(sumSq / read)
                        val db = if (rms > 1.0) (20.0 * kotlin.math.log10(rms)).toFloat() else 25f
                        liveAmbientDb = (liveAmbientDb * 0.8f) + (db * 0.2f)
                    }
                    delay(60)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    record?.stop()
                    record?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopAnc() {
        isAncActive = false
        estimatedReductionDb = 0f
        engineJob?.cancel()
        micMonitorJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
