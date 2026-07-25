package com.mkbhdana.streamhive.settings

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun HapticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    hapticsEnabled: Boolean = true
) {
    val view = LocalView.current
    var lastStep by remember(valueRange.start, valueRange.endInclusive, steps) {
        mutableIntStateOf(sliderHapticStep(value, valueRange, steps))
    }

    Slider(
        value = value,
        onValueChange = { newValue ->
            val nextStep = sliderHapticStep(newValue, valueRange, steps)
            if (hapticsEnabled && nextStep != lastStep) {
                view.performSliderHaptic()
                lastStep = nextStep
            } else if (!hapticsEnabled) {
                lastStep = nextStep
            }
            onValueChange(newValue)
        },
        valueRange = valueRange,
        steps = steps,
        modifier = modifier
    )
}

fun View.performSliderHaptic() {
    isHapticFeedbackEnabled = true
    val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    if (!performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, flags)) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, flags)
    }
}

fun View.performSwitchHaptic() {
    isHapticFeedbackEnabled = true
    performHapticFeedback(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
}

fun sliderHapticStep(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Int {
    val rangeSize = valueRange.endInclusive - valueRange.start
    if (rangeSize <= 0f) return 0
    val intervals = if (steps > 0) steps + 1 else 20
    val fraction = ((value - valueRange.start) / rangeSize).coerceIn(0f, 1f)
    return (fraction * intervals).toInt()
}

@Composable
fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean = true
) {
    val view = LocalView.current
    fun updateChecked(value: Boolean) {
        if (hapticsEnabled) view.performSwitchHaptic()
        onCheckedChange(value)
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { updateChecked(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = ::updateChecked)
    }
}

/** A tappable row that performs an action (open an editor, reset a group of settings). */
@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsDropdownItem(
    title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onToggle) { content() }
    }
}
