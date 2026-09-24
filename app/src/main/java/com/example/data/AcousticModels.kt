package com.example.data

data class AudioDeviceInfo(
    val name: String,
    val typeName: String,
    val isWired: Boolean,
    val isUsb: Boolean,
    val isBluetooth: Boolean,
    val isSpeaker: Boolean,
    val typicalHardwareLatencyMs: Int
)

data class LatencyMeasurement(
    val roundTripMs: Float,
    val inputBufferMs: Float,
    val outputBufferMs: Float,
    val sampleRate: Int,
    val framesPerBuffer: Int,
    val isExclusiveOboeEligible: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
