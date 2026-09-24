package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioHardwareManager
import com.example.audio.RealtimeInversionEngine
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MainAncScreen(
    inversionEngine: RealtimeInversionEngine,
    audioHardwareManager: AudioHardwareManager,
    modifier: Modifier = Modifier
) {
    var isAncOn by remember { mutableStateOf(inversionEngine.isAncActive) }
    var antiGain by remember { mutableStateOf(inversionEngine.antiGain) }
    var phaseTrimMs by remember { mutableStateOf(inversionEngine.phaseTrimMs) }
    var cutoffFreqHz by remember { mutableStateOf(inversionEngine.cutoffFreqHz) }
    var isVoiceDuckingEnabled by remember { mutableStateOf(inversionEngine.isVoiceDuckingEnabled) }
    var isComfortBedEnabled by remember { mutableStateOf(inversionEngine.isComfortMaskingBedEnabled) }
    var isSpeakingDetected by remember { mutableStateOf(false) }
    var liveMicDb by remember { mutableStateOf(inversionEngine.liveMicDb) }
    var showTipsBanner by remember { mutableStateOf(false) }

    val deviceInfo = remember { audioHardwareManager.getConnectedDeviceInfo() }

    // Pulsing animation for the big ANC power button
    val infiniteTransition = rememberInfiniteTransition(label = "anc_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isAncOn) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isAncOn) 0.65f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    // Continuously poll live audio energy and speaking detection
    LaunchedEffect(isAncOn) {
        while (isAncOn) {
            liveMicDb = inversionEngine.liveMicDb
            isSpeakingDetected = inversionEngine.isSpeakingDetected
            delay(80)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Headphones",
                        tint = ElectricCyan,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxSize()
                    )
                }
                Column {
                    Text(
                        text = "Real-Time Inversion ANC",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (deviceInfo.isWired || deviceInfo.isUsb) "Wired Low-Latency Connected" else "Earphones Required",
                        fontSize = 12.sp,
                        color = if (deviceInfo.isWired || deviceInfo.isUsb) EmeraldSuccess else AmberWarning
                    )
                }
            }

            IconButton(
                onClick = { showTipsBanner = !showTipsBanner },
                modifier = Modifier.testTag("tips_button")
            ) {
                Icon(
                    imageVector = if (showTipsBanner) Icons.Default.Close else Icons.Default.HelpOutline,
                    contentDescription = "Why could you hear yourself?",
                    tint = TextSecondary
                )
            }
        }

        // Explanation popdown for the user's feedback
        AnimatedVisibility(visible = showTipsBanner) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Why you heard your own voice & How it's solved:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElectricCyan
                    )
                    Text(
                        text = "• The Problem: The microphone on your wire/phone picks up your voice ~10 ms before it plays in your ears. Because voice frequencies change constantly, that 10 ms lag sounds like an echo or amplifier.\n\n" +
                               "• Solution 1 (Auto Voice Ducking): The app now detects when you speak and automatically mutes the inverted feedback instantly!\n\n" +
                               "• Solution 2 (Sub-Bass Cutoff): Lowering the cutoff slider to 100-120 Hz completely eliminates vocal harmonics, only cancelling deep drone.\n\n" +
                               "• Solution 3 (Comfort Bed): A subtle soft noise floor prevents high-frequency leaks through your ear tips.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // THE BIG ANC BUTTON
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(230.dp)
                .padding(6.dp)
        ) {
            if (isAncOn) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = pulseGlowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            val buttonBgColor by animateColorAsState(
                targetValue = if (isAncOn) ElectricCyan else SurfaceDark,
                animationSpec = tween(350),
                label = "btn_bg"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isAncOn) MidnightBg else TextSecondary,
                animationSpec = tween(350),
                label = "icon_color"
            )

            Surface(
                modifier = Modifier
                    .size(175.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (isAncOn) {
                            inversionEngine.stop()
                            isAncOn = false
                        } else {
                            inversionEngine.antiGain = antiGain
                            inversionEngine.phaseTrimMs = phaseTrimMs
                            inversionEngine.cutoffFreqHz = cutoffFreqHz
                            inversionEngine.isVoiceDuckingEnabled = isVoiceDuckingEnabled
                            inversionEngine.isComfortMaskingBedEnabled = isComfortBedEnabled
                            inversionEngine.start()
                            isAncOn = true
                        }
                    }
                    .testTag("anc_power_button"),
                shape = CircleShape,
                color = buttonBgColor,
                shadowElevation = if (isAncOn) 16.dp else 4.dp,
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = if (isAncOn) ElectricCyan else SurfaceVariantDark
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (isAncOn) "Turn Inversion ANC Off" else "Turn Inversion ANC On",
                        tint = iconColor,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAncOn) "CANCELLING" else "ANC OFF",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = iconColor
                    )
                    Text(
                        text = if (isAncOn) {
                            if (isSpeakingDetected && isVoiceDuckingEnabled) "Voice Muted" else "Anti-Noise Active"
                        } else "Tap to activate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isAncOn) MidnightBg.copy(alpha = 0.85f) else TextTertiary
                    )
                }
            }
        }

        // Live Mic Meter & Voice Detection Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAncOn) SurfaceDark else SurfaceDark.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isAncOn) EmeraldSuccess.copy(alpha = 0.4f) else SurfaceVariantDark
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VOICE SENSING", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (!isAncOn) "Standby" else if (isSpeakingDetected) "Speaking (Muted)" else "Silent (Inverting)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isAncOn) TextTertiary else if (isSpeakingDetected) AmberWarning else EmeraldSuccess
                    )
                }

                Divider(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp),
                    color = SurfaceVariantDark
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MIC INPUT LEVEL", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isAncOn) String.format(Locale.US, "%.0f dB SPL", liveMicDb) else "-- dB",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // Voice Activity Ducking Switch (Prevents hearing yourself talk!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.MicOff, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Auto Voice Mute (Zero Echo)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Instantly silences inverted output whenever you speak so you never hear your voice amplified.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = isVoiceDuckingEnabled,
                    onCheckedChange = {
                        isVoiceDuckingEnabled = it
                        inversionEngine.isVoiceDuckingEnabled = it
                    },
                    modifier = Modifier.testTag("voice_ducking_switch")
                )
            }
        }

        // Tunable Cutoff Frequency Slider (Isolates Low Hum from Human Voice)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.FilterAlt, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Low-Pass Drone Cutoff",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${cutoffFreqHz.toInt()} Hz",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                Slider(
                    value = cutoffFreqHz,
                    onValueChange = {
                        cutoffFreqHz = it
                        inversionEngine.cutoffFreqHz = it
                    },
                    valueRange = 60f..250f,
                    steps = 19,
                    modifier = Modifier.testTag("cutoff_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("60 Hz (Sub-Bass)", fontSize = 10.sp, color = TextTertiary)
                    Text("120 Hz (Motor/AC - Best)", fontSize = 10.sp, color = EmeraldSuccess, fontWeight = FontWeight.Bold)
                    Text("250 Hz (Low Mid)", fontSize = 10.sp, color = TextTertiary)
                }
            }
        }

        // Anti-Noise Gain Level (Volume of the Inverted Signal)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.VolumeDown, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Anti-Wave Inversion Volume",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${(antiGain * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                Slider(
                    value = antiGain,
                    onValueChange = {
                        antiGain = it
                        inversionEngine.antiGain = it
                    },
                    valueRange = 0.1f..0.9f,
                    modifier = Modifier.testTag("gain_slider")
                )

                Text(
                    text = "Controls anti-wave strength. Set to 30-50% for optimal balance against ambient noise.",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
        }

        // Comfort Masking Bed (Masks ear tip leakage)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Acoustic Comfort Bed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Blends a soft, low Brownian noise floor to smooth over sharp external clicks and background whispers.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = isComfortBedEnabled,
                    onCheckedChange = {
                        isComfortBedEnabled = it
                        inversionEngine.isComfortMaskingBedEnabled = it
                    },
                    modifier = Modifier.testTag("comfort_bed_switch")
                )
            }
        }

        // Phase Alignment / Delay Trim
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Phase Alignment Delay",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f ms", phaseTrimMs),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                Slider(
                    value = phaseTrimMs,
                    onValueChange = {
                        phaseTrimMs = it
                        inversionEngine.phaseTrimMs = it
                    },
                    valueRange = 0.0f..30.0f,
                    steps = 30,
                    modifier = Modifier.testTag("phase_trim_slider")
                )

                Text(
                    text = "Fine-tune while near an AC, fan, or fridge until you hear the low-end null.",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
        }
    }
}
