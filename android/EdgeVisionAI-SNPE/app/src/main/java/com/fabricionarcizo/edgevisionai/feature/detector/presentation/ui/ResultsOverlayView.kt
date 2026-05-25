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
package com.fabricionarcizo.edgevisionai.feature.detector.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.ObjectDetection
import com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorFramePayload
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.OverlayCoordinateMapper
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.paints.OverlayPaintFactory
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.paints.OverlayPaints
import com.fabricionarcizo.edgevisionai.feature.detector.overlay.renderer.ObjectOverlayRenderer
import com.fabricionarcizo.edgevisionai.feature.detector.presentation.config.OverlayConfig

/**
 * A custom view for rendering graphical overlays on top of a camera preview or image.
 *
 * This view is used to draw bounding boxes and detection results over a live or static image
 * source.
 *
 * @param context The context used to instantiate the view.
 * @param attrs The attribute set passed from the XML layout (optional).
 */
class ResultsOverlayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        /**
         * Companion object containing constants used for calculations.
         */
        companion object {
            /**
             * Minimum interval between updates in milliseconds to prevent excessive UI updates.
             */
            private const val MIN_UPDATE_INTERVAL_MS = 16L // ~60 FPS
        }

        /**
         * The width of the source image used for scaling bounding boxes.
         */
        private var imageWidth: Int = 0

        /**
         * The height of the source image used for scaling bounding boxes.
         */
        private var imageHeight: Int = 0

        /**
         * A flag indicating whether the camera is in front or back position.
         */
        private var isFrontCamera: Boolean = false

        /**
         * The list of object detection results to be rendered on the overlay.
         */
        private var objectDetections: List<ObjectDetection> = emptyList()

        /**
         * Mapper responsible for converting raw detection results + frame info into drawing
         * primitives (rects + labels) in view coordinates.
         */
        private val coordinateMapper = OverlayCoordinateMapper()

        /**
         * Paint objects used for drawing various overlay elements.
         */
        private var paints: OverlayPaints =
            OverlayPaintFactory(
                primaryColor = OverlayConfig.DEFAULT_BACKGROUND_COLOR,
                labelTextColor = OverlayConfig.DEFAULT_PRIMARY_TEXT_COLOR,
                labelTextSizePx = OverlayConfig.DEFAULT_PRIMARY_TEXT_SIZE_PX,
            ).create()

        /**
         * Renderers for object overlays.
         */
        private var objectRenderer =
            ObjectOverlayRenderer(
                boxPaint = paints.objectBoxPaint,
                backgroundPaint = paints.objectBackgroundPaint,
                labelPaint = paints.labelPaint,
            )

        /**
         * Handler for ensuring UI updates happen on the main thread.
         */
        private val uiHandler = Handler(Looper.getMainLooper())

        /**
         * Flag to prevent excessive UI update requests.
         */
        private var hasPendingUpdate = false

        /**
         * Last update time for throttling updates during high load.
         */
        private var lastUpdateTimeMs = 0L

        /**
         * Draws detection results on the canvas overlay.
         *
         * This method draws object bounding boxes with labels. Drawing is skipped if the image
         * dimensions are not properly set.
         *
         * @param canvas The canvas on which the overlay content is drawn.
         */
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (imageWidth == 0 || imageHeight == 0) return

            val objectItems =
                coordinateMapper.mapObjects(
                    frameWidth = imageWidth,
                    frameHeight = imageHeight,
                    isFrontCamera = isFrontCamera,
                    viewWidth = width,
                    viewHeight = height,
                    objects = objectDetections,
                )

            objectRenderer.render(canvas, objectItems)
        }

        /**
         * Sets the Paint objects used for drawing overlays.
         *
         * @param paints An instance of [OverlayPaints] containing the Paint objects.
         */
        fun setOverlayPaints(paints: OverlayPaints) {
            this.paints = paints
            this.objectRenderer =
                ObjectOverlayRenderer(
                    paints.objectBoxPaint,
                    paints.objectBackgroundPaint,
                    paints.labelPaint,
                )
            invalidate()
        }

        /**
         * Updates the overlay with new detection and landmark results from a
         * [com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorFramePayload].
         *
         * This method is thread-safe and can be called from any thread. UI updates are
         * automatically marshalled to the main thread to prevent ANR issues. Includes throttling
         * to prevent excessive updates during high load.
         *
         * @param payload The [com.fabricionarcizo.edgevisionai.feature.detector.infra.camera.model.DetectorFramePayload] containing detection results and frame info.
         */
        fun update(payload: DetectorFramePayload) {
            val currentTimeMs = System.currentTimeMillis()

            if (hasPendingUpdate || (currentTimeMs - lastUpdateTimeMs < MIN_UPDATE_INTERVAL_MS)) {
                return
            }

            hasPendingUpdate = true
            lastUpdateTimeMs = currentTimeMs

            if (Looper.myLooper() != Looper.getMainLooper()) {
                uiHandler.postAtFrontOfQueue { performUpdate(payload) }
            } else {
                performUpdate(payload)
            }
        }

        /**
         * Performs the actual update work on the UI thread.
         *
         * @param payload The [DetectorFramePayload] containing detection results and frame info.
         */
        private fun performUpdate(payload: DetectorFramePayload) {
            try {
                val detections = payload.detections
                val frame = payload.frame

                this.objectDetections = detections.objects
                this.imageWidth = frame.width
                this.imageHeight = frame.height
                this.isFrontCamera = frame.isFrontCamera
                invalidate()
            } finally {
                hasPendingUpdate = false
            }
        }
    }
