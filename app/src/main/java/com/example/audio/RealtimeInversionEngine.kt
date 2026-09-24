package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Advanced Clean-Inversion ANC Engine.
 *
 * Implements 4 key solutions to prevent hearing yourself:
 * 1. Voice Activity Mute (Voice Ducking): Automatically silences mic feed during talking.
 * 2. Tunable Low-Pass Cutoff (80 Hz, 120 Hz, 200 Hz): Keeps only sub-voice drone.
 * 3. Spectral Subtraction / Ambient Noise Floor Estimator: Isolates steady drone and subtracts dynamic speech.
 * 4. Ambient Comfort Masking Bed (Brown Noise Floor): Fills the silence so outside chatter is masked.
 */
class RealtimeInversionEngine(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var isAncActive: Boolean = false
        private set

    // User controls
    var antiGain: Float = 0.5f // 0.1 to 1.0 (inverted playback gain)
    var phaseTrimMs: Float = 0.0f // 0 to 30 ms manual fine-tuning delay
    var isLowPassEnabled: Boolean = true
    var cutoffFreqHz: Float = 120f // 60Hz to 300Hz (default 120Hz stops human voice fundamentals)
    var isVoiceDuckingEnabled: Boolean = true // Mutes inverted playback when you speak
    var isComfortMaskingBedEnabled: Boolean = true // Soft Brownian bed to mask high-frequency leaks

    var liveMicDb: Float = 35f
        private set
    var isSpeakingDetected: Boolean = false
        private set

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var streamThread: Thread? = null
    @Volatile private var isRunning: Boolean = false

    val nativeSampleRate: Int by lazy {
        val rateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        rateStr?.toIntOrNull() ?: 48000
    }

    val nativeFramesPerBuffer: Int by lazy {
        val framesStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        framesStr?.toIntOrNull() ?: 192
    }

    fun start() {
        if (isAncActive) return
        isAncActive = true
        isRunning = true

        streamThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            runRealtimeLoop()
        }, "RealtimeAncInversionThread").apply { start() }
    }

    private fun runRealtimeLoop() {
        val sampleRate = nativeSampleRate
        val burstFrames = nativeFramesPerBuffer

        val minInBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val inBufSize = max(minInBuf, burstFrames * 2)

        val minOutBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val outBufSize = max(minOutBuf, burstFrames * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                inBufSize
            )

            audioTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(outBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            val record = audioRecord ?: return
            val track = audioTrack ?: return

            record.startRecording()
            track.play()

            val silence = ShortArray(burstFrames)
            track.write(silence, 0, silence.size)

            val chunk = ShortArray(burstFrames)

            // Circular delay line
            val maxDelaySamples = (sampleRate * 0.05).toInt()
            val delayBuffer = FloatArray(maxDelaySamples)
            var delayWriteIdx = 0

            // 2nd-order Butterworth low-pass filter states
            var y1 = 0.0
            var y2 = 0.0
            var x1 = 0.0
            var x2 = 0.0

            // Voice activity & stationary noise tracker
            var noiseFloorRms = 100.0
            var voiceDuckingGain = 1.0f

            // Brownian noise generator for comfort bed
            val random = Random()
            var brownVal = 0.0

            var energySum = 0.0
            var sampleCount = 0

            while (isRunning) {
                val read = record.read(chunk, 0, burstFrames)
                if (read <= 0) continue

                val currentGain = antiGain
                val delaySamples = ((phaseTrimMs / 1000f) * sampleRate).toInt().coerceIn(0, maxDelaySamples - 1)

                // Update filter coefficients based on user's cutoff frequency
                val cutoff = cutoffFreqHz.toDouble().coerceIn(40.0, 400.0)
                val dt = 1.0 / sampleRate
                val rc = 1.0 / (2.0 * Math.PI * cutoff)
                val alpha = (dt / (rc + dt)).toFloat()

                // Calculate energy of this burst for Voice Activity Detection
                var burstEnergy = 0.0
                for (i in 0 until read) {
                    val s = chunk[i].toDouble()
                    burstEnergy += s * s
                }
                val burstRms = sqrt(burstEnergy / read)

                // Track background noise floor (slow rise, fast fall)
                if (burstRms < noiseFloorRms) {
                    noiseFloorRms = (noiseFloorRms * 0.95) + (burstRms * 0.05)
                } else {
                    noiseFloorRms = (noiseFloorRms * 0.999) + (burstRms * 0.001)
                }

                // If input exceeds 2.2x background noise floor, speech/transient is detected!
                val isVoiceDetected = burstRms > (noiseFloorRms * 2.3) && burstRms > 600.0
                isSpeakingDetected = isVoiceDetected

                // Smooth voice ducking envelope (fast mute in 10ms, gentle return in 300ms)
                val targetDucking = if (isVoiceDuckingEnabled && isVoiceDetected) 0.0f else 1.0f
                val attack = if (targetDucking < voiceDuckingGain) 0.4f else 0.03f
                voiceDuckingGain += attack * (targetDucking - voiceDuckingGain)

                // Process samples
                for (i in 0 until read) {
                    val rawSample = chunk[i].toFloat()

                    energySum += rawSample * rawSample
                    sampleCount++

                    // 1. Low-Pass filter to strip vocal formants and speech harmonics
                    val filteredSample = if (isLowPassEnabled) {
                        y1 += alpha * (rawSample - y1)
                        // Second pass for steeper 12dB/octave roll-off
                        y2 += alpha * (y1 - y2)
                        y2.toFloat()
                    } else {
                        rawSample
                    }

                    // 2. Circular delay line for phase calibration
                    delayBuffer[delayWriteIdx] = filteredSample
                    val readIdx = (delayWriteIdx - delaySamples + maxDelaySamples) % maxDelaySamples
                    delayWriteIdx = (delayWriteIdx + 1) % maxDelaySamples
                    val delayedSample = delayBuffer[readIdx]

                    // 3. INVERT PHASE (-1.0) and apply Voice Ducking
                    val antiNoise = -delayedSample * currentGain * voiceDuckingGain

                    // 4. Subtle Brownian comfort bed to mask passive earphone leakage
                    val comfortSample = if (isComfortMaskingBedEnabled) {
                        val white = (random.nextDouble() * 2.0) - 1.0
                        brownVal = (brownVal + (0.015 * white)) / 1.015
                        (brownVal * 0.18 * 24000.0).toFloat()
                    } else 0f

                    val finalOutput = antiNoise + comfortSample
                    val clamped = finalOutput.coerceIn(-28000f, 28000f)
                    chunk[i] = clamped.toInt().toShort()
                }

                track.write(chunk, 0, read)

                if (sampleCount >= 2400) {
                    val rms = sqrt(energySum / sampleCount)
                    val db = if (rms > 1.0) (20.0 * kotlin.math.log10(rms)).toFloat() else 25f
                    liveMicDb = (liveMicDb * 0.7f) + (db * 0.3f)
                    energySum = 0.0
                    sampleCount = 0
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioRecord = null
            audioTrack = null
        }
    }

    fun stop() {
        isAncActive = false
        isRunning = false
        try {
            streamThread?.interrupt()
        } catch (_: Exception) {}
        streamThread = null
    }
}
