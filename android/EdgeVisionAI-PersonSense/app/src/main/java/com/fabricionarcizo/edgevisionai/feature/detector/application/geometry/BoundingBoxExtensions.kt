package com.fabricionarcizo.edgevisionai.feature.detector.application.geometry

import android.graphics.RectF
import com.fabricionarcizo.edgevisionai.feature.detector.domain.model.BoundingBox

fun BoundingBox.toRectF(): RectF = RectF(left, top, right, bottom)

fun RectF.toBoundingBox(): BoundingBox = BoundingBox(
    left = left,
    top = top,
    right = right,
    bottom = bottom,
)
