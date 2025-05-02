package com.example.imagenavigator

// TODO: Prévoir future gestion du zoom et du pan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.imagenavigator.model.ZoneData

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var zones: List<ZoneData> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var onZoneClicked: ((String) -> Unit)? = null
    var onLongClickAt: ((Float, Float) -> Unit)? = null
    var showZonesOverlay: Boolean = false

    private val overlayPaint = Paint().apply {
        color = Color.argb(100, 128, 128, 128)
        style = Paint.Style.FILL
    }

    private var longClickTriggered = false
    private var lastRawX = 0f
    private var lastRawY = 0f

    var imageOffsetX = 0f
    var imageOffsetY = 0f
    var imageDisplayWidth = 0f
    var imageDisplayHeight = 0f

    private val longClickRunnable = Runnable {
        longClickTriggered = true
        performLongClick()
        onLongClickAt?.invoke(lastRawX, lastRawY)
    }

    // Met à jour les dimensions et la position de l’image affichée.
    // Utilise postInvalidateOnAnimation() pour redessiner de façon fluide au prochain cycle d’animation.
    fun updateImageBounds(offsetX: Float, offsetY: Float, width: Float, height: Float) {
        imageOffsetX = offsetX
        imageOffsetY = offsetY
        imageDisplayWidth = width
        imageDisplayHeight = height
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("OVERLAY_TOUCH", "ACTION_DOWN at x=$x, y=$y, rawX=${event.rawX}, rawY=${event.rawY}")
                longClickTriggered = false
                lastRawX = event.rawX
                lastRawY = event.rawY
                postDelayed(longClickRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_UP -> {
                Log.d("OVERLAY_TOUCH", "ACTION_UP at x=$x, y=$y, longClickTriggered=$longClickTriggered, showZonesOverlay=$showZonesOverlay")
                removeCallbacks(longClickRunnable)
                if (!longClickTriggered) {
                    val matchedZone = findZoneAt(x, y)
                    if (matchedZone != null && !matchedZone.linkedImagePath.isNullOrEmpty()) {
                        onZoneClicked?.invoke(matchedZone.linkedImagePath!!)
                    }
                }
                longClickTriggered = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longClickRunnable)
                longClickTriggered = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // TODO: Affichage des zones grises en overlay
        if (showZonesOverlay && imageDisplayWidth > 0 && imageDisplayHeight > 0 && zones.isNotEmpty()) {
            zones.forEach { zone ->
                val rect = RectF(
                    imageOffsetX + zone.rect.left * imageDisplayWidth,
                    imageOffsetY + zone.rect.top * imageDisplayHeight,
                    imageOffsetX + zone.rect.right * imageDisplayWidth,
                    imageOffsetY + zone.rect.bottom * imageDisplayHeight
                )
                canvas.drawRect(rect, overlayPaint)
            }
        }
    }

    private fun findZoneAt(x: Float, y: Float): ZoneData? {
        if (imageDisplayWidth <= 0 || imageDisplayHeight <= 0) return null
        Log.d("OVERLAY_TOUCH", "Checking zones, count=${zones.size}, imageDisplayWidth=$imageDisplayWidth, imageDisplayHeight=$imageDisplayHeight")
        // TODO: Optimiser si nécessaire en utilisant un spatial index pour les grandes listes
        zones.forEach { zone ->
            val left = imageOffsetX + zone.rect.left * imageDisplayWidth
            val top = imageOffsetY + zone.rect.top * imageDisplayHeight
            val right = imageOffsetX + zone.rect.right * imageDisplayWidth
            val bottom = imageOffsetY + zone.rect.bottom * imageDisplayHeight
            Log.d("OVERLAY_TOUCH", "Zone check: left=$left, top=$top, right=$right, bottom=$bottom, point=($x, $y)")
            if (x in left..right && y in top..bottom) {
                Log.d("OVERLAY_TOUCH", "Matched zone: ${zone.linkedImagePath}")
                return zone
            }
        }
        Log.d("OVERLAY_TOUCH", "No zone matched at x=$x, y=$y")
        return null
    }
}