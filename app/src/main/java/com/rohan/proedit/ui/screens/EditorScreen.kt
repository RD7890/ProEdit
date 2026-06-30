package com.rohan.proedit.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.proedit.ui.components.AdjustmentPanel
import com.rohan.proedit.ui.theme.*
import com.rohan.proedit.viewmodel.EditorTab
import com.rohan.proedit.viewmodel.EditorViewModel
import kotlinx.coroutines.delay

private val FILTER_NAMES = listOf(
    "Original", "Vivid", "Dramatic", "B&W", "Cinematic", "Aqua", "Matte", "Golden"
)

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
) {
    val state   by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Mask screen takes over full display
    if (state.activeTab == EditorTab.MASK) {
        MaskScreen(viewModel = viewModel)
        return
    }

    // Save confirmation effect — LaunchedEffect must NOT be conditional
    val savedPath = state.savedPath
    LaunchedEffect(savedPath) {
        if (savedPath != null) {
            delay(2500)
            viewModel.clearSaveState()
        }
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
                .padding(horizontal = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint               = TextPrimary,
                )
            }

            Text(
                text  = "PRO EDIT",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                color = TextPrimary,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Active mask indicator dot
                if (state.hasMask) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PhotoshopBlue)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(
                    onClick  = { viewModel.saveToGallery(context) },
                    enabled  = !state.isSaving && state.displayBitmap != null,
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Save,
                        contentDescription = "Save",
                        tint               = if (state.isSaving) TextDisabled else AccentBlue,
                    )
                }
            }
        }

        // ── Image preview ─────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(NavyBg),
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

            if (state.isProcessing) {
                CircularProgressIndicator(
                    color    = PhotoshopBlue,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // ── Tab row ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavySurface)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EditorTabItem(
                label    = "Tune",
                icon     = Icons.Outlined.Tune,
                selected = state.activeTab == EditorTab.TUNE,
                onClick  = { viewModel.setActiveTab(EditorTab.TUNE) },
            )
            EditorTabItem(
                label    = "Filters",
                icon     = Icons.Outlined.FilterVintage,
                selected = state.activeTab == EditorTab.FILTERS,
                onClick  = { viewModel.setActiveTab(EditorTab.FILTERS) },
            )
            EditorTabItem(
                label    = "Mask",
                icon     = Icons.Outlined.Brush,
                selected = false,
                onClick  = { viewModel.enterMaskMode() },
            )
        }

        // ── Bottom content ────────────────────────────────────────────────
        when (state.activeTab) {
            EditorTab.TUNE -> AdjustmentPanel(
                adjustments        = state.adjustments,
                onAdjustmentChange = { block -> viewModel.updateAdjustment(block) },
                onReset            = { viewModel.resetAdjustments() },
            )
            EditorTab.FILTERS -> FilterStrip(
                activeIndex = state.activeFilter,
                onSelect    = { viewModel.applyFilter(it) },
            )
            else -> {}
        }

        // ── Save toast ─────────────────────────────────────────────────────
        if (savedPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuccessGreen)
                    .padding(12.dp),
            ) {
                Text(
                    text  = "Saved to gallery",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EditorTabItem(
    label:    String,
    icon:     ImageVector,
    selected: Boolean,
    onClick:  () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (selected) NavySelected else NavyPanel)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = if (selected) AccentBlue else TextSecondary,
            modifier           = Modifier.size(20.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AccentBlue else TextSecondary,
        )
    }
}

@Composable
private fun FilterStrip(activeIndex: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(FILTER_NAMES) { index, name ->
            val selected = activeIndex == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier            = Modifier.clickable { onSelect(index) },
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavySurface)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) PhotoshopBlue else NavyDivider,
                            shape = RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.FilterVintage,
                        contentDescription = null,
                        tint               = if (selected) AccentBlue else TextSecondary,
                        modifier           = Modifier.size(24.dp),
                    )
                }
                Text(
                    text  = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) AccentBlue else TextSecondary,
                )
            }
        }
    }
}
