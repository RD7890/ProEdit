package com.rohan.proedit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.proedit.ui.theme.*

@Composable
fun HomeScreen(onImagePicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(onImagePicked) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBg)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            // ── Logo + title ──────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PhotoshopBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint               = TextPrimary,
                        modifier           = Modifier.size(36.dp),
                    )
                }

                Text(
                    text       = "PRO EDIT",
                    style      = MaterialTheme.typography.titleLarge.copy(
                        fontSize     = 28.sp,
                        fontWeight   = FontWeight.Bold,
                        letterSpacing = 4.sp,
                    ),
                    color = TextPrimary,
                )

                Text(
                    text  = "PROFESSIONAL PHOTO EDITOR",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = TextSecondary,
                )
            }

            // ── Feature tags ──────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("NDK C++", "Masking", "Adjustments", "Filters").forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NavyPanel)
                            .border(1.dp, NavyDivider, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentBlue,
                        )
                    }
                }
            }

            // ── Import button ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavySurface)
                    .border(1.dp, NavyDivider, RoundedCornerShape(12.dp))
                    .clickable { launcher.launch("image/*") }
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PhotoshopBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint               = TextPrimary,
                        modifier           = Modifier.size(28.dp),
                    )
                }

                Text(
                    text      = "Import Photo",
                    style     = MaterialTheme.typography.titleMedium,
                    color     = TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text      = "Tap to select from gallery",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            // ── Capabilities list ─────────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CapabilityRow(
                    icon  = Icons.Outlined.Image,
                    title = "Smart & Manual Masking",
                    desc  = "Brush, smart select, radial, linear, color range",
                )
                CapabilityRow(
                    icon  = Icons.Outlined.CameraAlt,
                    title = "Precision Adjustments",
                    desc  = "Brightness, contrast, saturation, highlights & more",
                )
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc:  String,
) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NavyPanel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = PhotoshopBlue,
                modifier           = Modifier.size(18.dp),
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(desc,  style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
