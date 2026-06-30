package com.rohan.proedit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.proedit.ui.theme.*
import com.rohan.proedit.viewmodel.AdjustmentState

private data class AdjItem(
    val label: String,
    val value: Float,
    val onSet: AdjustmentState.(Float) -> AdjustmentState,
)

@Composable
fun AdjustmentPanel(
    adjustments:        AdjustmentState,
    onAdjustmentChange: (AdjustmentState.() -> AdjustmentState) -> Unit,
    onReset:            () -> Unit,
) {
    val items = listOf(
        AdjItem("Brightness",   adjustments.brightness)  { v -> copy(brightness  = v) },
        AdjItem("Contrast",     adjustments.contrast)    { v -> copy(contrast    = v) },
        AdjItem("Saturation",   adjustments.saturation)  { v -> copy(saturation  = v) },
        AdjItem("Highlights",   adjustments.highlights)  { v -> copy(highlights  = v) },
        AdjItem("Shadows",      adjustments.shadows)     { v -> copy(shadows     = v) },
        AdjItem("Temperature",  adjustments.temperature) { v -> copy(temperature = v) },
        AdjItem("Sharpness",    adjustments.sharpness)   { v -> copy(sharpness   = v) },
        AdjItem("Ambiance",     adjustments.ambiance)    { v -> copy(ambiance    = v) },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .background(NavyPanel)
            .padding(bottom = 8.dp),
    ) {
        // Header
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = "Adjustments",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Row(
                modifier  = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.RestartAlt,
                    contentDescription = "Reset",
                    tint               = TextSecondary,
                    modifier           = Modifier.size(14.dp),
                )
                Text(
                    text  = "Reset",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        HorizontalDivider(color = NavyDivider, thickness = 1.dp)

        LazyColumn(
            contentPadding        = PaddingValues(vertical = 4.dp),
            verticalArrangement   = Arrangement.spacedBy(0.dp),
        ) {
            items(items.size) { i ->
                val item = items[i]
                AdjustmentRow(
                    label   = item.label,
                    value   = item.value,
                    onValueChange = { v -> onAdjustmentChange { item.onSet(this, v) } },
                )
            }
        }
    }
}

@Composable
private fun AdjustmentRow(
    label:         String,
    value:         Float,
    onValueChange: (Float) -> Unit,
) {
    val displayVal = (value * 100).toInt()

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            modifier = Modifier.width(88.dp),
        )

        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = -1f..1f,
            modifier      = Modifier.weight(1f),
            colors        = SliderDefaults.colors(
                thumbColor            = if (value != 0f) PhotoshopBlue else TextSecondary,
                activeTrackColor      = PhotoshopBlue,
                inactiveTrackColor    = NavyDivider,
            ),
        )

        Text(
            text     = if (displayVal >= 0) "+$displayVal" else "$displayVal",
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (value != 0f) FontWeight.SemiBold else FontWeight.Normal,
                fontSize   = 11.sp,
            ),
            color    = if (value != 0f) AccentBlue else TextDisabled,
            modifier = Modifier.width(32.dp),
        )
    }
}
