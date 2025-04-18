package com.example.imagenavigator.screens

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.imagenavigator.model.Zone

/**
 * Vue personnalisée qui permet d'afficher une image et de dessiner des zones rectangulaires
 * avec le doigt (drag & drop).
 */
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var imageBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    // Liste des zones visibles pour cette image
    val zones: MutableList<Zone> = mutableListOf()

    // Callback appelée quand une nouvelle zone est créée
    var onZoneCreated: ((Zone) -> Unit)? = null

    private val paintZone = Paint().apply {
        color = Color.argb(128, 0, 255, 0)
        style = Paint.Style.FILL
    }

    private val paintBorder = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private var drawingRect: RectF? = null
    private var startX = 0f
    private var startY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = imageBitmap ?: return
        val dstRect = getImageDisplayRect(bitmap)

        // Dessiner l’image
        canvas.drawBitmap(bitmap, null, dstRect, null)

        // Dessiner les zones enregistrées
        for (zone in zones) {
            val r = zone.rect
            val absLeft = dstRect.left + r.left * dstRect.width()
            val absTop = dstRect.top + r.top * dstRect.height()
            val absRight = dstRect.left + r.right * dstRect.width()
            val absBottom = dstRect.top + r.bottom * dstRect.height()
            val absRect = RectF(absLeft, absTop, absRight, absBottom)

            canvas.drawRect(absRect, paintZone)
            canvas.drawRect(absRect, paintBorder)
        }

        // Dessiner temporairement le rectangle qu'on est en train de tracer
        drawingRect?.let {
            canvas.drawRect(it, paintZone)
            canvas.drawRect(it, paintBorder)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap = imageBitmap ?: return false
        val dstRect = getImageDisplayRect(bitmap)

        val x = event.x.coerceIn(dstRect.left, dstRect.right)
        val y = event.y.coerceIn(dstRect.top, dstRect.bottom)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                drawingRect = RectF(startX, startY, startX, startY)
            }
            MotionEvent.ACTION_MOVE -> {
                drawingRect?.set(
                    minOf(startX, x),
                    minOf(startY, y),
                    maxOf(startX, x),
                    maxOf(startY, y)
                )
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                drawingRect?.let { rect ->
                    val relative = RectF(
                        (rect.left - dstRect.left) / dstRect.width(),
                        (rect.top - dstRect.top) / dstRect.height(),
                        (rect.right - dstRect.left) / dstRect.width(),
                        (rect.bottom - dstRect.top) / dstRect.height()
                    )

                    // Empêche les zones trop petites
                    if (relative.width() > 0.01f && relative.height() > 0.01f) {
                        val zone = Zone(relative, null, null)
                        zones.add(zone)
                        onZoneCreated?.invoke(zone)
                    }
                }
                drawingRect = null
                invalidate()
            }
        }

        return true
    }

    /**
     * Calcule la zone de l'écran où l'image est affichée.
     */
    private fun getImageDisplayRect(bitmap: Bitmap): RectF {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        // Center the image horizontally and vertically
        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2

        return RectF(left, top, left + scaledWidth, top + scaledHeight)
    }
}