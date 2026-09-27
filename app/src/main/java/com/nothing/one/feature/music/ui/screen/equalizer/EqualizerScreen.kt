package com.nothing.one.feature.music.ui.screen.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nothing.one.feature.music.ui.theme.DotMatrixFont
import com.nothing.one.feature.music.ui.theme.NothingTextSecondary
import com.nothing.one.feature.music.ui.theme.NothingTextTertiary

private val FREQ_LABELS = listOf("60", "230", "1K", "3.5K", "14K")
private const val BAND_MIN = -15f
private const val BAND_MAX = 15f

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsState()
    val gains by viewModel.gains.collectAsState()
    val presetName by viewModel.presetName.collectAsState()
    val bassEnabled by viewModel.bassBoostEnabled.collectAsState()
    val bassStrength by viewModel.bassBoostStrength.collectAsState()
    val virtEnabled by viewModel.virtualizerEnabled.collectAsState()
    val virtStrength by viewModel.virtualizerStrength.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "EQUALIZER",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = DotMatrixFont),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.resetAll() }) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = "Reset",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EQUALIZER",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = DotMatrixFont),
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (enabled) "ON · $presetName" else "OFF",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTextTertiary,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = viewModel::setEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = com.nothing.one.feature.music.ui.theme.NothingSurfaceHighlight,
                ),
            )
        }

        if (enabled) {
            EqBands(
                gains = gains,
                onGainChange = { index, valueMb ->
                    viewModel.setBandGain(index, valueMb)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 20.dp),
            )
        }

        Text(
            text = "PRESETS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = DotMatrixFont,
                letterSpacing = 2.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(EqPresets.ALL, key = { it.name }) { preset ->
                PresetChip(
                    name = preset.name,
                    isSelected = presetName == preset.name && enabled,
                    onClick = { viewModel.applyPreset(preset) },
                )
            }
        }

        EffectRow(
            title = "BASS BOOST",
            subtitle = "Emphasize low frequencies",
            enabled = bassEnabled,
            strength = bassStrength,
            onToggle = { viewModel.setBassBoost(it, bassStrength) },
            onStrengthChange = { viewModel.setBassBoost(bassEnabled, it) },
        )

        EffectRow(
            title = "VIRTUALIZER",
            subtitle = "Surround sound effect",
            enabled = virtEnabled,
            strength = virtStrength,
            onToggle = { viewModel.setVirtualizer(it, virtStrength) },
            onStrengthChange = { viewModel.setVirtualizer(virtEnabled, it) },
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun EqBands(
    gains: FloatArray,
    onGainChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        gains.forEachIndexed { index, gain ->
            VerticalBandSlider(
                value = gain,
                onValueChange = { onGainChange(index, it) },
                label = FREQ_LABELS[index],
            )
        }
    }
}

@Composable
private fun VerticalBandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
) {
    var dragged by remember { mutableFloatStateOf(value) }
    val display = dragged

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatMb(dragged),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = DotMatrixFont),
            color = if (dragged == 0f) NothingTextSecondary else MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        VerticalSlider(
            value = display,
            onValueChange = {
                dragged = it
                onValueChange(it)
            },
            modifier = Modifier
                .width(36.dp)
                .height(150.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NothingTextTertiary,
        )
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var localValue by remember { mutableFloatStateOf(value) }
    val fraction = ((localValue - BAND_MIN) / (BAND_MAX - BAND_MIN)).coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = com.nothing.one.feature.music.ui.theme.NothingSurfaceHighlight

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    val heightPx = size.height
                    val newFraction = (fraction - (dragAmount / heightPx)).coerceIn(0f, 1f)
                    val newValue = BAND_MIN + newFraction * (BAND_MAX - BAND_MIN)
                    localValue = newValue
                    onValueChange(newValue)
                }
            )
        }
    ) {
        val barWidth = 6.dp.toPx()
        val left = (size.width - barWidth) / 2
        val sliderTop = 0f
        val sliderBottom = size.height
        val barY = sliderTop + (1f - fraction) * (sliderBottom - sliderTop)

        // Track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(left, sliderTop),
            size = Size(barWidth, sliderBottom - sliderTop),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
        )
        // Fill below thumb -> red, above -> subtle
        if (fraction > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(left, barY),
                size = Size(barWidth, sliderBottom - barY),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
        // Thumb
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(left + barWidth / 2, barY),
        )
        drawCircle(
            color = activeColor,
            radius = 5.dp.toPx(),
            center = Offset(left + barWidth / 2, barY),
        )
    }
}

@Composable
private fun PresetChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = name.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = DotMatrixFont),
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else NothingTextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EffectRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    strength: Int,
    onToggle: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(com.nothing.one.feature.music.ui.theme.NothingSurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = DotMatrixFont),
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTextTertiary,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        if (enabled) {
            androidx.compose.material3.Slider(
                value = strength.toFloat(),
                onValueChange = { onStrengthChange(it.toInt()) },
                valueRange = 0f..100f,
            )
            Text(
                text = "$strength%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = DotMatrixFont),
                color = NothingTextSecondary,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private fun formatMb(valueMb: Float): String {
    val db = valueMb / 100f
    return if (db == 0f) "0" else if (db > 0) "+${db.toInt()}" else db.toInt().toString()
}