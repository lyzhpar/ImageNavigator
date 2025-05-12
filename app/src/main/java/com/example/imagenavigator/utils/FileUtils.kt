package com.example.imagenavigator.utils

import android.graphics.RectF

fun RectF.toRectF(viewWidth: Float, viewHeight: Float): RectF {
    return RectF(
        left * viewWidth,
        top * viewHeight,
        right * viewWidth,
        bottom * viewHeight
    )
}