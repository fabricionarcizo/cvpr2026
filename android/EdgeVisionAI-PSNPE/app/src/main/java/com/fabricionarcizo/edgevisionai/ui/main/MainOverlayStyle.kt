/*
 * MIT License
 *
 * Copyright (c) 2026 Elizabete Munzlinger and Fabricio Batista Narcizo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.fabricionarcizo.edgevisionai.ui.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.paints.OverlayPaintFactory
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.ResultsOverlayView

/**
 * A composable function that configures the overlay style.
 *
 * @param resultsOverlayView The view that displays the object detection results.
 */
@Composable
fun ConfigureOverlayStyle(resultsOverlayView: ResultsOverlayView) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val primaryTextColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val density = LocalDensity.current

    val primaryTextSizePx =
        with(density) {
            MaterialTheme.typography.titleMedium.fontSize
                .toPx()
        }

    val overlayPaints =
        remember(primaryColor, primaryTextColor, primaryTextSizePx) {
            OverlayPaintFactory(
                primaryColor = primaryColor,
                labelTextColor = primaryTextColor,
                labelTextSizePx = primaryTextSizePx,
            ).create()
        }

    SideEffect {
        resultsOverlayView.setOverlayPaints(overlayPaints)
    }
}
