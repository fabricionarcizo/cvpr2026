package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui

import android.content.Context
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose wrapper around CameraX's `PreviewView`. The actual camera binding
 * is driven by the ViewModel; this composable just owns the View instance
 * and hands its handle back via [onPreviewView].
 */
@Composable
fun CameraPreview(
    onPreviewView: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewView = remember(context) { createPreviewView(context) }
    AndroidView(
        factory = {
            onPreviewView(previewView)
            previewView
        },
        modifier = modifier,
    )
}

private fun createPreviewView(context: Context): PreviewView = PreviewView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    scaleType = PreviewView.ScaleType.FIT_CENTER
}
