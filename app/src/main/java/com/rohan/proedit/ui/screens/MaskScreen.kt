package com.rohan.proedit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.proedit.ui.theme.*
import com.rohan.proedit.viewmodel.EditorViewModel
import com.rohan.proedit.viewmodel.MaskMode

@Composable
fun MaskScreen(viewModel: EditorViewModel) {
    val state by viewModel.uiState.collectAsState()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun toBitmapCoord(offset: Offset): Offset {
        val bmp = state.originalBitmap ?: return offset
        val cw = canvasSize.width.toFloat().coerceAtLeast(1f)
        val ch = canvasSize.height.toFloat().coerceAtLeast(1f)
        val scaleX = bmp.width.toFloat()  / cw
        val scaleY = bmp.height.toFloat() / ch
        val scale  = maxOf(scaleX, scaleY)
        val dispW  = bmp.width  / scale
        val dispH  = bmp.height / scale
        val offX   = (cw - dispW) / 2f
        val offY   = (ch - dispH) / 2f
        return Offset((offset.x - offX) * scale, (offset.y - offY) * scale)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
            .systemBarsPadding(),
    ) {
        // ── Top bar ───────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(NavySurface)
                .padding(horizontal = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = "Mask",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                color = TextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { viewModel.invertMask() }) {
                    Icon(Icons.Outlined.InvertColors, "Invert mask", tint = TextSecondary)
                }
                IconButton(onClick = { viewModel.clearMask() }) {
                    Icon(Icons.Outlined.Delete, "Clear mask", tint = TextSecondary)
                }
            }
        }

        // ── Image + mask overlay ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(NavyBg)
                .onSizeChanged { canvasSize = it }
                .pointerInput(state.maskMode, state.brushAdding) {
                    when (state.maskMode) {
                        MaskMode.BRUSH -> detectDragGestures(
                            onDragStart = { off ->
                                val bc = toBitmapCoord(off)
                                viewModel.paintBrush(bc.x, bc.y)
                            },
                            onDrag = { change, _ ->
                                val bc = toBitmapCoord(change.position)
                                viewModel.paintBrush(bc.x, bc.y)
                            }
                        )
                        MaskMode.SMART_BRUSH -> detectTapGestures { off ->
                            val bc = toBitmapCoord(off)
                            viewModel.smartBrush(bc.x, bc.y)
                        }
                        else -> Unit
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            state.displayBitmap?.let { bmp ->
                Image(
                    bitmap             = bmp.asImageBitmap(),
                    contentDescription = "Photo",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize(),
                )
            }

            state.overlayBitmap?.let { overlay ->
                Image(
                    bitmap             = overlay.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize(),
                )
            }

            // Brush size preview ring in center of canvas
            if ((state.maskMode == MaskMode.BRUSH || state.maskMode == MaskMode.SMART_BRUSH)
                && canvasSize.width > 0
            ) {
                val bmp = state.originalBitmap
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (bmp != null) {
                        val scaleX = bmp.width.toFloat() / canvasSize.width.toFloat()
                        val dispRadius = state.brushSize / scaleX
                        drawCircle(
                            color  = if (state.brushAdding) Color(0x551473E6) else Color(0x55CC2200),
                            radius = dispRadius,
                            center = center,
                            style  = Stroke(width = 1.5f),
                        )
                    }
                }
            }
        }

        // ── Mask tool selector ────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(NavySurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MaskToolButton(
                icon     = Icons.Outlined.Brush,
                label    = "Brush",
                selected = state.maskMode == MaskMode.BRUSH,
                onClick  = { viewModel.setMaskMode(MaskMode.BRUSH) },
            )
            MaskToolButton(
                icon     = Icons.Outlined.AutoFixHigh,
                label    = "Smart",
                selected = state.maskMode == MaskMode.SMART_BRUSH,
                onClick  = { viewModel.setMaskMode(MaskMode.SMART_BRUSH) },
            )
            MaskToolButton(
                icon     = Icons.Outlined.RadioButtonUnchecked,
                label    = "Radial",
                selected = state.maskMode == MaskMode.RADIAL,
                onClick  = {
                    viewModel.setMaskMode(MaskMode.RADIAL)
                    val bmp = state.originalBitmap ?: return@MaskToolButton
                    viewModel.applyRadialMask(
                        bmp.width / 2f, bmp.height / 2f,
                        bmp.width * 0.2f, bmp.width * 0.45f, false,
                    )
                },
            )
            MaskToolButton(
                icon     = Icons.Outlined.LinearScale,
                label    = "Linear",
                selected = state.maskMode == MaskMode.LINEAR,
                onClick  = {
                    viewModel.setMaskMode(MaskMode.LINEAR)
                    val bmp = state.originalBitmap ?: return@MaskToolButton
                    viewModel.applyLinearMask(
                        0f, bmp.height * 0.3f,
                        bmp.width.toFloat(), bmp.height * 0.7f,
                    )
                },
            )
            MaskToolButton(
                icon     = Icons.Outlined.Colorize,
                label    = "Color",
                selected = state.maskMode == MaskMode.COLOR_RANGE,
                onClick  = { viewModel.setMaskMode(MaskMode.COLOR_RANGE) },
            )
        }

        // ── Add / Erase + brush size ──────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(NavyPanel)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Add button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.brushAdding) PhotoshopBlue else NavySurface)
                    .border(1.dp, NavyDivider, RoundedCornerShape(6.dp))
                    .clickable { viewModel.setBrushAdding(true) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Add, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }

            // Erase button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (!state.brushAdding) MaskRed else NavySurface)
                    .border(1.dp, NavyDivider, RoundedCornerShape(6.dp))
                    .clickable { viewModel.setBrushAdding(false) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Remove, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Text("Erase", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }

            Spacer(Modifier.weight(1f))

            // Brush size slider
            Icon(Icons.Outlined.FiberManualRecord, null, tint = TextSecondary, modifier = Modifier.size(10.dp))
            Slider(
                value         = state.brushSize,
                onValueChange = { viewModel.setBrushSize(it) },
                valueRange    = 10f..150f,
                modifier      = Modifier.width(100.dp),
                colors        = SliderDefaults.colors(
                    thumbColor       = PhotoshopBlue,
                    activeTrackColor = PhotoshopBlue,
                ),
            )
            Icon(Icons.Outlined.FiberManualRecord, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }

        // ── Cancel / Done ─────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(NavySurface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { viewModel.exitMaskMode(false) }) {
                Text(
                    "CANCEL",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                )
            }
            Button(
                onClick = { viewModel.exitMaskMode(true) },
                colors  = ButtonDefaults.buttonColors(containerColor = PhotoshopBlue),
                shape   = RoundedCornerShape(6.dp),
            ) {
                Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "DONE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                )
            }
        }
    }
}

@Composable
private fun MaskToolButton(
    icon:     ImageVector,
    label:    String,
    selected: Boolean,
    onClick:  () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NavySelected else NavySurface)
            .border(1.dp, if (selected) PhotoshopBlue else NavyDivider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = if (selected) AccentBlue else TextSecondary,
            modifier           = Modifier.size(18.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AccentBlue else TextSecondary,
        )
    }
}
