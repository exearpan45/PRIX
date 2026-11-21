package com.example.prix.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalPrixColors
import com.example.ui.theme.LocalPrixEffects
import com.example.ui.theme.PrixAccentBlue
import com.example.ui.theme.PrixPurpleDeep
import com.example.ui.theme.PrixPurplePrimary

/**
 * Lightweight, static ambient Liquid Glass backdrop.
 * Zero infinite animations when idle; automatically simplifies on low-RAM devices.
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val baseBg = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .then(
                if (effects.enableAmbientHighlights) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(prixColors.ambientPurple, Color.Transparent),
                                center = Offset(size.width * 0.16f, size.height * 0.08f),
                                radius = size.maxDimension * 0.52f
                            ),
                            center = Offset(size.width * 0.16f, size.height * 0.08f),
                            radius = size.maxDimension * 0.52f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(prixColors.ambientBlue, Color.Transparent),
                                center = Offset(size.width * 0.88f, size.height * 0.28f),
                                radius = size.maxDimension * 0.44f
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.28f),
                            radius = size.maxDimension * 0.44f
                        )
                    }
                } else {
                    Modifier
                }
            ),
        content = content
    )
}

/**
 * Core translucent Liquid Glass container with inner specular highlight and performance-aware shadow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    elevated: Boolean = false,
    tintOverlay: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed && onClick != null && effects.enablePressScale) 0.978f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.78f),
        label = "glassCardScale"
    )

    val baseSurface = if (elevated) prixColors.glassElevated else prixColors.glassSurface
    val finalContainer = tintOverlay ?: baseSurface
    val rimBrush = remember(prixColors.glassHighlightBorder, prixColors.glassOuterBorder, borderColor) {
        if (borderColor != null) {
            SolidColor(borderColor)
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    prixColors.glassHighlightBorder,
                    prixColors.glassOuterBorder
                )
            )
        }
    }

    val shadowModifier = if (effects.enableGlassShadows) {
        Modifier.shadow(
            elevation = if (elevated) 10.dp else 4.dp,
            shape = shape,
            ambientColor = PrixPurplePrimary.copy(alpha = if (prixColors.isDark) 0.22f else 0.12f),
            spotColor = PrixAccentBlue.copy(alpha = if (prixColors.isDark) 0.18f else 0.08f)
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .scale(pressScale)
            .then(shadowModifier)
            .border(width = 1.dp, brush = rimBrush, shape = shape),
        shape = shape,
        color = finalContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple(),
                            onClick = onClick
                        )
                    } else Modifier
                )
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && effects.enablePressScale) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassBtnScale"
    )

    if (isPrimary) {
        val gradient = if (enabled) {
            Brush.linearGradient(listOf(PrixPurplePrimary, PrixPurpleDeep))
        } else {
            SolidColor(MaterialTheme.colorScheme.surfaceVariant)
        }
        Box(
            modifier = modifier
                .scale(scale)
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(gradient)
                .border(
                    width = 1.dp,
                    color = if (enabled) Color.White.copy(alpha = 0.28f) else prixColors.glassOuterBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(color = Color.White),
                    onClick = onClick
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) Color.White else prixColors.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else prixColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = prixColors.glassElevated,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, prixColors.glassOuterBorder),
            modifier = modifier
                .scale(scale)
                .height(52.dp)
                .minimumInteractiveComponentSize()
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color? = null,
    size: Dp = 44.dp
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && effects.enablePressScale) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "glassIconScale"
    )

    Surface(
        shape = CircleShape,
        color = containerColor ?: prixColors.glassElevated,
        border = BorderStroke(1.dp, prixColors.glassOuterBorder),
        modifier = modifier
            .scale(scale)
            .size(size)
            .minimumInteractiveComponentSize()
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val prixColors = LocalPrixColors.current
    Surface(
        color = prixColors.glassElevated,
        border = BorderStroke(1.dp, prixColors.glassOuterBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    modifier = if (backTestTag != null) Modifier.testTag(backTestTag) else Modifier
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = prixColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = actions
            )
        }
    }
}

/**
 * Interactive or display-only Liquid Glass search bar.
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search documents, folders, OCR…",
    modifier: Modifier = Modifier,
    readOnlyClick: (() -> Unit)? = null
) {
    val prixColors = LocalPrixColors.current
    val shape = RoundedCornerShape(20.dp)

    Surface(
        shape = shape,
        color = prixColors.glassElevated,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(prixColors.glassHighlightBorder, prixColors.glassOuterBorder)
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (readOnlyClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(onClick = readOnlyClick)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (readOnlyClick != null) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = prixColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = prixColors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = prixColors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Immutable
data class GlassNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

/**
 * Apple-inspired floating Liquid Glass bottom navigation dock with sliding capsule indicator
 * and full gesture navigation bar inset protection.
 */
@Composable
fun GlassBottomBar(
    tabs: List<GlassNavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val dockShape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = dockShape,
            color = prixColors.glassElevated,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(prixColors.glassHighlightBorder, prixColors.glassOuterBorder)
                )
            ),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .height(68.dp)
                .then(
                    if (effects.enableGlassShadows) {
                        Modifier.shadow(
                            elevation = 14.dp,
                            shape = dockShape,
                            ambientColor = PrixPurplePrimary.copy(alpha = 0.24f),
                            spotColor = PrixAccentBlue.copy(alpha = 0.16f)
                        )
                    } else Modifier
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val safeCount = tabs.size.coerceAtLeast(1)
                val slotWidth = maxWidth / safeCount
                val safeSelected = selectedIndex.coerceIn(0, safeCount - 1)
                val indicatorOffset by animateDpAsState(
                    targetValue = slotWidth * safeSelected,
                    animationSpec = if (effects.shouldAnimate) {
                        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.82f)
                    } else {
                        tween(0)
                    },
                    label = "glassNavPillOffset"
                )

                // Animated active capsule pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(slotWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (prixColors.isDark) 0.28f else 0.14f),
                                    PrixAccentBlue.copy(alpha = if (prixColors.isDark) 0.18f else 0.10f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                            shape = RoundedCornerShape(24.dp)
                        )
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = index == safeSelected
                        val tint by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else prixColors.secondaryText,
                            animationSpec = if (effects.shouldAnimate) tween(180) else tween(0),
                            label = "navItemTint"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected && effects.shouldAnimate) 1.08f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "navIconScale"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { onSelect(index) }
                                .minimumInteractiveComponentSize()
                                .testTag(tab.testTag),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AnimatedContent(
                                targetState = isSelected,
                                transitionSpec = {
                                    if (effects.shouldAnimate) {
                                        fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                                    } else {
                                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    }
                                },
                                label = "navIconCrossfade"
                            ) { active ->
                                Icon(
                                    imageVector = if (active) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .scale(iconScale)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated scale + fade Liquid Glass modal dialog wrapper.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = if (effects.shouldAnimate) {
                fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.93f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)
                )
            } else fadeIn(tween(0)),
            exit = if (effects.shouldAnimate) fadeOut(tween(120)) + scaleOut(targetScale = 0.95f) else fadeOut(tween(0))
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = prixColors.glassElevated,
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(listOf(prixColors.glassHighlightBorder, prixColors.glassOuterBorder))
                ),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (icon != null) {
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            icon()
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun GlassSettingsItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val prixColors = LocalPrixColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onClick)
                } else if (checked != null && onCheckedChange != null) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onCheckedChange(!checked) }
                } else Modifier
            )
            .minimumInteractiveComponentSize()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = prixColors.secondaryText)
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (checked != null && onCheckedChange != null) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = prixColors.secondaryText
            )
        }
    }
}

@Composable
fun GlassProgressIndicator(
    progress: Float,
    label: String,
    completed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val prixColors = LocalPrixColors.current
    val effects = LocalPrixEffects.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (effects.shouldAnimate) tween(220) else tween(0),
        label = "glassProgress"
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (completed) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = prixColors.success,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            if (completed) listOf(prixColors.success, prixColors.success)
                            else listOf(PrixPurplePrimary, PrixAccentBlue)
                        )
                    )
            )
        }
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val prixColors = LocalPrixColors.current
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = if (prixColors.isDark) 0.28f else 0.14f)
    } else {
        prixColors.glassSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        prixColors.glassOuterBorder
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else prixColors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
