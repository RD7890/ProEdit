package com.rohan.proedit.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Simple composable that displays a [Bitmap] fit-centered within the available space.
 * Used for both the main image display and the mask overlay.
 */
@Composable
fun ImageCanvas(
    bitmap:   Bitmap?,
    overlay:  Bitmap?  = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap             = it.asImageBitmap(),
                contentDescription = "Photo",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize(),
            )
        }
        overlay?.let {
            Image(
                bitmap             = it.asImageBitmap(),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize(),
            )
        }
    }
}
