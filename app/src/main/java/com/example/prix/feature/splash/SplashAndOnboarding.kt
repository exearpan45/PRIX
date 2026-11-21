package com.example.prix.feature.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.prix.core.ui.GlassButton
import com.example.prix.core.ui.GlassCard
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.prix.core.ui.PrixLogoMark
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import com.example.ui.theme.PrixAccentBlue
import com.example.ui.theme.PrixPurpleDeep
import com.example.ui.theme.PrixPurplePrimary
import com.example.ui.theme.PrixSuccess
import kotlinx.coroutines.delay

@Composable
fun AnimatedSplashOverlay(
    visible: Boolean,
    animationsEnabled: Boolean,
    onFinished: () -> Unit
) {
    val effects = LocalPrixEffects.current
    val animate = animationsEnabled && effects.shouldAnimate
    val scale = remember { Animatable(if (animate) 0.78f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(visible, animate) {
        if (!visible) return@LaunchedEffect
        if (animate) {
            alpha.animateTo(1f, animationSpec = tween(220))
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.72f)
            )
            delay(320)
        } else {
            scale.snapTo(1f)
            alpha.snapTo(1f)
            delay(90)
        }
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(if (animate) tween(140) else tween(0)),
        exit = fadeOut(if (animate) tween(240, easing = FastOutSlowInEasing) else tween(0))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0E1022),
                            PrixPurpleDeep,
                            PrixPurplePrimary,
                            PrixAccentBlue.copy(alpha = 0.88f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 36.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PrixLogoMark(size = 86.dp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "PRIX",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan. Create. Edit. Organize.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun FirstLaunchOnboardingScreen(
    onGetStarted: () -> Unit
) {
    val prixColors = LocalPrixColors.current
    LiquidGlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PrixLogoMark(size = 68.dp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Welcome to PRIX",
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan. Create. Edit. Organize. Your complete offline document studio — no account required.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = prixColors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 24.dp)
                ) {
                    OnboardingFeatureCard(
                        icon = Icons.Filled.CameraAlt,
                        accent = MaterialTheme.colorScheme.primary,
                        title = "Smart Document Scanner",
                        description = "Capture pages with edge detection, perspective correction, filters, and multi-page PDF export."
                    )
                    OnboardingFeatureCard(
                        icon = Icons.Filled.EditNote,
                        accent = PrixAccentBlue,
                        title = "Full PDF Toolkit & Editor",
                        description = "Merge, split, compress, annotate, sign, watermark, password-protect, and extract OCR text."
                    )
                    OnboardingFeatureCard(
                        icon = Icons.Filled.Shield,
                        accent = PrixSuccess,
                        title = "Private & Offline-First",
                        description = "Documents stay on your device. Camera and file permissions are asked only when you use those tools."
                    )
                }

                GlassButton(
                    text = "Get Started",
                    onClick = onGetStarted,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_get_started")
                )
            }
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String
) {
    val prixColors = LocalPrixColors.current
    GlassCard(
        shape = RoundedCornerShape(22.dp),
        elevated = true,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = prixColors.secondaryText
                )
            }
        }
    }
}
