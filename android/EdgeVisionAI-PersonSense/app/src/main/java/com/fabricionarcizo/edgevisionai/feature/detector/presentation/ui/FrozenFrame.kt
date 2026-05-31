package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Shows a captured frame in place of the live camera preview. Uses
 * [ContentScale.Fit] so that the [PersonOverlay]'s fit-center coordinate
 * mapping lines up.
 */
@Composable
fun FrozenFrame(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
}
