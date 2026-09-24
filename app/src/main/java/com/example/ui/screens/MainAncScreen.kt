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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AncEngine
import com.example.audio.AncSimulationMode
import com.example.audio.AudioHardwareManager
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MainAncScreen(
    ancEngine: AncEngine,
    audioHardwareManager: AudioHardwareManager,
    modifier: Modifier = Modifier
) {
    var isAncOn by remember { mutableStateOf(ancEngine.isAncActive) }
    var selectedMode by remember { mutableStateOf(ancEngine.mode) }
    var intensity by remember { mutableStateOf(ancEngine.intensity) }
    var liveDb by remember { mutableStateOf(ancEngine.liveAmbientDb) }
    var reductionDb by remember { mutableStateOf(ancEngine.estimatedReductionDb) }
    var showExplanation by remember { mutableStateOf(false) }

    val deviceInfo = remember { audioHardwareManager.getConnectedDeviceInfo() }

    // Pulsing animation for the big ANC power button when active
    val infiniteTransition = rememberInfiniteTransition(label = "anc_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isAncOn) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isAncOn) 0.65f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    // Poll live mic dB & reduction info while active
    LaunchedEffect(isAncOn) {
        while (isAncOn) {
            liveDb = ancEngine.liveAmbientDb
            reductionDb = ancEngine.estimatedReductionDb
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
        // Top App Header
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
                    modifier = Modifier.size(40.dp)
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
                        text = "Active Noise Cancellation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (deviceInfo.isWired || deviceInfo.isUsb) "Headphones Connected" else "Earphones Recommended",
                        fontSize = 12.sp,
                        color = if (deviceInfo.isWired || deviceInfo.isUsb) EmeraldSuccess else AmberWarning
                    )
                }
            }

            IconButton(
                onClick = { showExplanation = !showExplanation },
                modifier = Modifier.testTag("help_button")
            ) {
                Icon(
                    imageVector = if (showExplanation) Icons.Default.Close else Icons.Default.Info,
                    contentDescription = "Info",
                    tint = TextSecondary
                )
            }
        }

        // Educational quick popover if user clicked (?)
        AnimatedVisibility(visible = showExplanation) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "How Phone ANC Simulation Works",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ElectricCyan
                    )
                    Text(
                        text = "Real ANC headphones react in 0.03 ms right at the ear. A phone audio loop has 12-18 ms of buffer delay.\n\n" +
                               "To give you real noise reduction, this app combines:\n" +
                               "1. Dynamic sound masking that quiets ambient drone.\n" +
                               "2. Low-frequency anti-waves targeting hums.\n" +
                               "3. Passive earphone isolation for instant comfort.",
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
            // Outer glowing ring when active
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

            // Main Interactive Circle
            val buttonBgColor by animateColorAsState(
                targetValue = if (isAncOn) ElectricCyan else SurfaceDark,
                animationSpec = tween(400),
                label = "btn_bg"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isAncOn) MidnightBg else TextSecondary,
                animationSpec = tween(400),
                label = "icon_color"
            )

            Surface(
                modifier = Modifier
                    .size(175.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (isAncOn) {
                            ancEngine.stopAnc()
                            isAncOn = false
                        } else {
                            ancEngine.mode = selectedMode
                            ancEngine.intensity = intensity
                            ancEngine.startAnc()
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
                        contentDescription = if (isAncOn) "Turn ANC Off" else "Turn ANC On",
                        tint = iconColor,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isAncOn) "ANC ON" else "ANC OFF",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = iconColor
                    )
                    Text(
                        text = if (isAncOn) "Cancelling Noise" else "Tap to activate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isAncOn) MidnightBg.copy(alpha = 0.8f) else TextTertiary
                    )
                }
            }
        }

        // Live Status Banner
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
                    Text("STATUS", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isAncOn) "Active" else "Standby",
                        fontSize = 15.sp,
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
                    Text("ESTIMATED QUIET", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isAncOn) String.format(Locale.US, "-%.0f dB", reductionDb) else "0 dB",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAncOn) ElectricCyan else TextTertiary
                    )
                }

                Divider(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp),
                    color = SurfaceVariantDark
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ROOM NOISE", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isAncOn) String.format(Locale.US, "%.0f dB", liveDb) else "-- dB",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // ANC Strength / Intensity Slider
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
                        Icon(Icons.Default.Tune, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "ANC Intensity Level",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${(intensity * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                Slider(
                    value = intensity,
                    onValueChange = {
                        intensity = it
                        ancEngine.intensity = it
                    },
                    valueRange = 0.2f..1.0f,
                    modifier = Modifier.testTag("intensity_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtle", fontSize = 11.sp, color = TextTertiary)
                    Text("Balanced", fontSize = 11.sp, color = TextTertiary)
                    Text("Maximum Silence", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }

        // ANC Profile Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ANC Mode",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                AncSimulationMode.values().forEach { m ->
                    val isSelected = selectedMode == m
                    Surface(
                        color = if (isSelected) SurfaceCard else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ElectricCyan else SurfaceVariantDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMode = m
                                ancEngine.mode = m
                                if (isAncOn) {
                                    // Restart with new mode
                                    ancEngine.stopAnc()
                                    ancEngine.startAnc()
                                }
                            }
                            .testTag("mode_${m.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedMode = m
                                    ancEngine.mode = m
                                    if (isAncOn) {
                                        ancEngine.stopAnc()
                                        ancEngine.startAnc()
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ElectricCyan,
                                    unselectedColor = TextSecondary
                                )
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = m.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) ElectricCyan else TextPrimary
                                )
                                Text(
                                    text = m.subtitle,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Headphone Tip Recommendation Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .border(1.dp, SurfaceVariantDark, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldSuccess,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Tip: For best results, use snug in-ear silicone or foam tips with wired headphones to seal out high frequencies.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = TextSecondary
            )
        }
    }
}
