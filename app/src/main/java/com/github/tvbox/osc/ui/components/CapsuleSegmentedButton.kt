package com.github.tvbox.osc.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class SegmentStyle { Connected, Track, Separated }

private val TrackShape = RoundedCornerShape(percent = 50)

private val TrackPadding = 4.dp

private val TrackSegmentSpacing = 4.dp

private val TrackContentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)

private val SeparatedSegmentSpacing = 8.dp

private val SeparatedContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

data class SegmentOption<T>(
    val label: String,
    val value: T,
    val icon: ImageVector? = null,
    val iconPainter: Painter? = null,
    val badge: String? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> CapsuleSegmentedButton(
    options: List<SegmentOption<T>>,
    selectedValue: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    style: SegmentStyle = SegmentStyle.Connected,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)

    if (style == SegmentStyle.Track) {
        Surface(
            modifier = modifier,
            shape = TrackShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier.padding(TrackPadding),
                horizontalArrangement = Arrangement.spacedBy(TrackSegmentSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { index, option ->
                    CapsuleToggleButton(
                        option = option,
                        checked = selectedIndex == index,
                        onCheckedChange = { onOptionSelected(option.value) },
                        index = index,
                        count = options.size,
                        style = style,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        return
    }

    if (style == SegmentStyle.Separated) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(SeparatedSegmentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                CapsuleToggleButton(
                    option = option,
                    checked = selectedIndex == index,
                    onCheckedChange = { onOptionSelected(option.value) },
                    index = index,
                    count = options.size,
                    style = style,
                    containerColor = containerColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            CapsuleToggleButton(
                option = option,
                checked = selectedIndex == index,
                onCheckedChange = { onOptionSelected(option.value) },
                index = index,
                count = options.size,
                style = style,
                containerColor = containerColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> CapsuleToggleButton(
    option: SegmentOption<T>,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    style: SegmentStyle,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "capsuleScale",
    )

    val segmentModifier = modifier
        .semantics { role = Role.RadioButton }
        .scale(scale)

    if (style == SegmentStyle.Track) {
        ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = segmentModifier,
            shapes = ToggleButtonShapes(
                shape = TrackShape,
                pressedShape = TrackShape,
                checkedShape = TrackShape,
            ),
            colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            elevation = null,
            contentPadding = TrackContentPadding,
            interactionSource = interactionSource,
        ) {
            SegmentContent(option, MaterialTheme.typography.labelSmall)
        }
    } else if (style == SegmentStyle.Separated) {
        ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = segmentModifier,
            shapes = ToggleButtonShapes(
                shape = TrackShape,
                pressedShape = TrackShape,
                checkedShape = TrackShape,
            ),
            colors = ToggleButtonDefaults.colors(containerColor = containerColor),
            contentPadding = SeparatedContentPadding,
            interactionSource = interactionSource,
        ) {
            SegmentContent(option, MaterialTheme.typography.bodySmall)
        }
    } else {
        ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = segmentModifier,
            shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
            colors = ToggleButtonDefaults.colors(containerColor = containerColor),
            interactionSource = interactionSource,
        ) {
            SegmentContent(option, MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun <T> SegmentContent(option: SegmentOption<T>, badgeStyle: TextStyle) {
    if (option.icon != null) {
        Icon(imageVector = option.icon, contentDescription = null)
        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
    } else if (option.iconPainter != null) {
        Icon(painter = option.iconPainter, contentDescription = null)
        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = option.label, overflow = TextOverflow.Ellipsis, maxLines = 1)
        option.badge?.let { badge ->
            Text(
                text = badge,
                style = badgeStyle,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}
