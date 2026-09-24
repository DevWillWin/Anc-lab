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
    var isLowPassEnabled by remember { mutableStateOf(inversionEngine.isLowPassEnabled) }
    var liveMicDb by remember { mutableStateOf(inversionEngine.liveMicDb) }
    var showWhyEchoBanner by remember { mutableStateOf(false) }

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

    // Continuously poll live audio energy while running
    LaunchedEffect(isAncOn) {
        while (isAncOn) {
            liveMicDb = inversionEngine.liveMicDb
            delay(100)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
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
                onClick = { showWhyEchoBanner = !showWhyEchoBanner },
                modifier = Modifier.testTag("why_echo_button")
            ) {
                Icon(
                    imageVector = if (showWhyEchoBanner) Icons.Default.Close else Icons.Default.HelpOutline,
                    contentDescription = "Why was it amplified?",
                    tint = TextSecondary
                )
            }
        }

        // Explanation popdown for the user's exact issue
        AnimatedVisibility(visible = showWhyEchoBanner) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Why did raw inversion sound amplified?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AmberWarning
                        )
                    }
                    Text(
                        text = "In physics, when an inverted sound wave is delayed by even 10-15 milliseconds (the time Android takes to process mic-to-speaker), the waves no longer collide out-of-phase.\n\n" +
                               "Instead, the delayed peaks align constructively with incoming peaks, creating an ECHO or +6 dB NOISE AMPLIFICATION.\n\n" +
                               "How we fixed it:\n" +
                               "1. Added Low-Pass Filter: Cuts out voices & mid-high sounds so you don't hear your own delayed voice.\n" +
                               "2. Added Phase Alignment Trim: Lets you dial the delay to match the physical distance to your ear.\n" +
                               "3. Fast Hardware Buffers: Minimized system latency to the absolute floor.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // THE BIG ANC BUTTON
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .padding(8.dp)
        ) {
            // Glowing aura when active
            if (isAncOn) {
                Box(
                    modifier = Modifier
                        .size(230.dp)
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
                            inversionEngine.isLowPassEnabled = isLowPassEnabled
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
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isAncOn) "INVERTING" else "ANC OFF",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = iconColor
                    )
                    Text(
                        text = if (isAncOn) "Anti-Noise Active" else "Tap to Invert Mic",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isAncOn) MidnightBg.copy(alpha = 0.8f) else TextTertiary
                    )
                }
            }
        }

        // Live Mic Meter & Status Card
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
                    Text("PHASE INVERSION", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isAncOn) "180° Inverted (-1x)" else "Bypassed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAncOn) EmeraldSuccess else TextTertiary
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
                            text = "Anti-Wave Gain",
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
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.testTag("gain_slider")
                )

                Text(
                    text = "Controls the volume of the inverted anti-signal. Keep at 40-60% to avoid overpowering ambient sound.",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
        }

        // Low-Pass Filter Switch (The key to eliminating the echo/amplification!)
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
                        text = "Low-Pass Anti-Drone Filter (< 250 Hz)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Filters out speech, chatter, and high frequencies so you don't hear a delayed echo of your voice.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = isLowPassEnabled,
                    onCheckedChange = {
                        isLowPassEnabled = it
                        inversionEngine.isLowPassEnabled = it
                    },
                    modifier = Modifier.testTag("lowpass_switch")
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
                    text = "Adjust slowly while listening to a fan or AC hum until you find the quietest point.",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
        }
    }
}
