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

import android.content.Context
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui.ResultsOverlayView

/**
 * Creates a new [PreviewView] instance with the specified context.
 *
 * @param context The context used to create the [PreviewView].
 *
 * @return A new [PreviewView] instance.
 */
fun createPreviewView(context: Context): PreviewView =
    PreviewView(context).apply {
        layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        scaleType = PreviewView.ScaleType.FILL_START
    }

/**
 * Creates a new [ResultsOverlayView] instance with the specified context.
 *
 * @param context The context used to create the [ResultsOverlayView].
 *
 * @return A new [ResultsOverlayView] instance.
 */
fun createResultsOverlayView(context: Context): ResultsOverlayView = ResultsOverlayView(context)
