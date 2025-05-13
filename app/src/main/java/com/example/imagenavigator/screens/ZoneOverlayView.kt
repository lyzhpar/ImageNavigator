package com.example.imagenavigator.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.imagenavigator.model.ZoneData

class ZoneOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var zones: List<ZoneData> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

// Fond intérieur
        val fillPaint = Paint().apply {
            color = Color.argb(80, 0, 255, 0) // vert semi-transparent
            style = Paint.Style.FILL
            isAntiAlias = true
        }

// Contour
        val strokePaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        for (zone in zones) {
            val rect = RectF(
                zone.rect.left * width,
                zone.rect.top * height,
                zone.rect.right * width,
                zone.rect.bottom * height
            )
// Dessin de la zone
            canvas.drawRect(rect, fillPaint)   // intérieur
            canvas.drawRect(rect, strokePaint) // contour
        }
    }
}