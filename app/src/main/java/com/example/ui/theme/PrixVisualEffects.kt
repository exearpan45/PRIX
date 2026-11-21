package com.example.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.prix.core.model.PerformanceMode
import com.example.prix.core.model.PerformanceProfile

@Immutable
data class PrixEffectsConfig(
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val performanceModeSetting: PerformanceMode = PerformanceMode.AUTO,
    val isLowEndDevice: Boolean = false,
    val animationsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val enableAmbientHighlights: Boolean = true,
    val enableGlassShadows: Boolean = true,
    val enablePressScale: Boolean = true,
    val enableKeySurfaceBlur: Boolean = false,
    val animationDurationMs: Int = 220
) {
    val shouldAnimate: Boolean
        get() = animationsEnabled && !reducedMotion
}

val LocalPrixEffects = staticCompositionLocalOf { PrixEffectsConfig() }

/**
 * Pure capability resolver used at runtime and in unit tests.
 */
fun resolveEffectivePerformanceProfile(
    mode: PerformanceMode,
    totalMemMb: Long,
    memoryClassMb: Int,
    isLowRamDevice: Boolean
): PerformanceProfile {
    return when (mode) {
        PerformanceMode.LOW -> PerformanceProfile.LOW
        PerformanceMode.BALANCED -> PerformanceProfile.BALANCED
        PerformanceMode.HIGH -> PerformanceProfile.HIGH
        PerformanceMode.AUTO -> {
            when {
                isLowRamDevice || (totalMemMb in 1..3300) || (memoryClassMb in 1..192) -> PerformanceProfile.LOW
                totalMemMb >= 5600 && memoryClassMb >= 256 -> PerformanceProfile.HIGH
                else -> PerformanceProfile.BALANCED
            }
        }
    }
}

@Composable
fun rememberPrixEffectsConfig(
    performanceMode: PerformanceMode = PerformanceMode.AUTO,
    animationsEnabled: Boolean,
    reducedMotionOverride: Boolean
): PrixEffectsConfig {
    val context = LocalContext.current
    return remember(context, performanceMode, animationsEnabled, reducedMotionOverride) {
        val hw = readDeviceMemoryCapability(context)
        val profile = resolveEffectivePerformanceProfile(
            mode = performanceMode,
            totalMemMb = hw.totalMemMb,
            memoryClassMb = hw.memoryClassMb,
            isLowRamDevice = hw.isLowRam
        )
        val isLowProfile = profile == PerformanceProfile.LOW
        val isHighProfile = profile == PerformanceProfile.HIGH
        val systemAnimatorDisabled = isSystemAnimatorDisabled(context)
        val reducedMotion = reducedMotionOverride || systemAnimatorDisabled || !animationsEnabled
        val effectiveAnimate = animationsEnabled && !reducedMotion

        PrixEffectsConfig(
            performanceProfile = profile,
            performanceModeSetting = performanceMode,
            isLowEndDevice = isLowProfile,
            animationsEnabled = animationsEnabled,
            reducedMotion = reducedMotion,
            enableAmbientHighlights = !isLowProfile && !reducedMotion,
            enableGlassShadows = !isLowProfile,
            enablePressScale = effectiveAnimate && !isLowProfile,
            enableKeySurfaceBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isHighProfile && !reducedMotion,
            animationDurationMs = when {
                reducedMotion -> 0
                isLowProfile -> 120
                isHighProfile -> 240
                else -> 180
            }
        )
    }
}

private data class DeviceMemoryCapability(
    val totalMemMb: Long,
    val memoryClassMb: Int,
    val isLowRam: Boolean
)

private fun readDeviceMemoryCapability(context: Context): DeviceMemoryCapability {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalMb = memInfo.totalMem / (1024L * 1024L)
            DeviceMemoryCapability(
                totalMemMb = totalMb,
                memoryClassMb = am.memoryClass,
                isLowRam = am.isLowRamDevice
            )
        } else {
            DeviceMemoryCapability(totalMemMb = 4096L, memoryClassMb = 256, isLowRam = false)
        }
    } catch (_: Throwable) {
        DeviceMemoryCapability(totalMemMb = 4096L, memoryClassMb = 256, isLowRam = false)
    }
}

private fun isSystemAnimatorDisabled(context: Context): Boolean {
    return try {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale <= 0f
    } catch (_: Throwable) {
        false
    }
}
