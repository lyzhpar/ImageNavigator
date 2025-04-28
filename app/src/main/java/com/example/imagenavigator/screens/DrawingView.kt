package com.example.imagenavigator.screens

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.imagenavigator.model.Zone
import android.util.Log
import android.view.GestureDetector
import android.os.Handler
import android.os.Looper

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        isClickable = true
        isLongClickable = true
    }

    var imageBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    val zones: MutableList<Zone> = mutableListOf()
    var selectedZone: Zone? = null
    private var drawingRect: RectF? = null
    private var startX = 0f
    private var startY = 0f

    // Déclarer spotlightActive pour gérer la sélection des zones
    private var spotlightActive: Boolean = false

    // Ajout de la variable onTapListener pour permettre l'appel après un tap
    var onTapListener: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Log.d("DrawingView", "onSingleTapUp détecté")
            onTapListener?.invoke()  // Appel de onTapListener lorsque le tap est détecté
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            Log.d("DrawingView", "onLongPress détecté - appel performLongClick()")
            performLongClick()
        }
    })

    private val paintZone = Paint().apply {
        color = Color.argb(128, 0, 255, 0)
        style = Paint.Style.FILL
    }

    private val paintBorder = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // Suppression de la logique spotlight et gestion d'image liée
    // Redessiner les zones et détecter les clics pour les zones
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = imageBitmap ?: return
        val dstRect = getImageDisplayRect(bitmap)

        canvas.drawBitmap(bitmap, null, dstRect, null)

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

        drawingRect?.let {
            canvas.drawRect(it, paintZone)
            canvas.drawRect(it, paintBorder)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("DrawingView", "onTouchEvent: ${event.action}, spotlightActive=$spotlightActive, selectedZone=$selectedZone")
        gestureDetector.onTouchEvent(event)

        val bitmap = imageBitmap
        if (bitmap == null) {
            return true
        }

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
                    val movementX = Math.abs(rect.right - rect.left)
                    val movementY = Math.abs(rect.bottom - rect.top)

                    if (movementX < 10f && movementY < 10f) {
                        // Petit déplacement : considérer comme un clic
                        val touchX = event.x
                        val touchY = event.y
                        var foundZone = false
                        for (zone in zones) {
                            val absLeft = dstRect.left + zone.rect.left * dstRect.width()
                            val absTop = dstRect.top + zone.rect.top * dstRect.height()
                            val absRight = dstRect.left + zone.rect.right * dstRect.width()
                            val absBottom = dstRect.top + zone.rect.bottom * dstRect.height()
                            val absRect = RectF(absLeft, absTop, absRight, absBottom)

                            if (absRect.contains(touchX, touchY)) {
                                selectedZone = zone
                                spotlightActive = true  // Activer le spotlight
                                invalidate()
                                foundZone = true
                                break
                            }
                        }
                        if (!foundZone) {
                            clearSpotlight()
                        }
                    }
                }
                drawingRect = null
            }
        }
        return true
    }

    private fun getImageDisplayRect(bitmap: Bitmap): RectF {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2

        return RectF(left, top, left + scaledWidth, top + scaledHeight)
    }

    fun clearSpotlight() {
        selectedZone = null
        spotlightActive = false  // Désactiver le spotlight
        invalidate()
    }

    fun assignLinkedImageToZone(zone: Zone, imagePath: String) {
        zone.linkedImagePath = imagePath
        invalidate()  // Redessiner la vue
    }
}