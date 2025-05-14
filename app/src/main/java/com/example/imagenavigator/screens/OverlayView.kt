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

    var debugShowZones: Boolean = true

    var zones: List<ZoneData> = emptyList()
        set(value) {
            Log.d("OVERLAY_TOUCH", "Zones updated: count=${value.size}")
            field = value
            postInvalidate()
        }

    var onZoneClicked: ((String) -> Unit)? = null
    var onLongClickAt: ((Float, Float) -> Unit)? = null
    var showZonesOverlay: Boolean = true // DEBUG: afficher les zones en permanence


    private val overlayPaint = Paint().apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }


    fun setZonesVisible(visible: Boolean) {
        overlayPaint.color = if (visible) {
            Color.argb(60, 255, 255, 255)
        } else {
            Color.argb(0, 0, 0, 0) // complètement transparent
        }
        invalidate()
    }


    private var longClickTriggered = false
    private var lastRawX = 0f
    private var lastRawY = 0f

    var imageOffsetX = 0f
    var imageOffsetY = 0f
    var imageDisplayWidth = 0f
    var imageDisplayHeight = 0f

    var bitmapWidth: Float = 0f
    var bitmapHeight: Float = 0f

    private val longClickRunnable = Runnable {
        longClickTriggered = true
        performLongClick()
        onLongClickAt?.invoke(lastRawX, lastRawY) ?: Log.w("OVERLAY_TOUCH", "onLongClickAt listener is null")
    }

    fun updateImageBounds(viewWidth: Float, viewHeight: Float, bitmapWidth: Float, bitmapHeight: Float) {
        Log.d("OVERLAY_TOUCH", "updateImageBounds START: viewWidth=$viewWidth, viewHeight=$viewHeight, bitmapWidth=$bitmapWidth, bitmapHeight=$bitmapHeight")
        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            Log.w("OVERLAY_TOUCH", "Invalid dimensions, skipping updateImageBounds")
            return
        }
        val viewRatio = viewWidth / viewHeight
        val imageRatio = bitmapWidth / bitmapHeight
        Log.d("OVERLAY_TOUCH", "viewRatio=$viewRatio, imageRatio=$imageRatio")

        val scaledWidth: Float
        val scaledHeight: Float
        val offsetX: Float
        val offsetY: Float

        if (imageRatio > viewRatio) {
            scaledWidth = viewWidth
            scaledHeight = viewWidth / imageRatio
            offsetX = 0f
            offsetY = (viewHeight - scaledHeight) / 2f
        } else {
            scaledHeight = viewHeight
            scaledWidth = viewHeight * imageRatio
            offsetX = (viewWidth - scaledWidth) / 2f
            offsetY = 0f
        }

        imageOffsetX = offsetX
        imageOffsetY = offsetY
        imageDisplayWidth = scaledWidth
        imageDisplayHeight = scaledHeight

        this.bitmapWidth = bitmapWidth
        this.bitmapHeight = bitmapHeight

        Log.d("OVERLAY_TOUCH", "Calculated → scaledWidth=$scaledWidth, scaledHeight=$scaledHeight, offsetX=$offsetX, offsetY=$offsetY")
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("OVERLAY_TOUCH", "ACTION_DOWN at x=$x, y=$y, rawX=${event.rawX}, rawY=${event.rawY}")
                Log.d("OVERLAY_TOUCH", "ACTION_DOWN triggered, scheduling longClickRunnable")
                longClickTriggered = false
                lastRawX = event.rawX
                lastRawY = event.rawY
                postDelayed(longClickRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_UP -> {
                Log.d("OVERLAY_TOUCH", "ACTION_UP triggered, checking for zone match")
                Log.d("OVERLAY_TOUCH", "ACTION_UP at x=$x, y=$y, longClickTriggered=$longClickTriggered, showZonesOverlay=$showZonesOverlay")
                removeCallbacks(longClickRunnable)
                if (!longClickTriggered) {
                    val matchedZone = findZoneAt(x, y)
                    if (matchedZone != null) {
                        if (!matchedZone.linkedImagePath.isNullOrEmpty()) {
                            onZoneClicked?.invoke(matchedZone.linkedImagePath!!).also {
                                Log.d("OVERLAY_TOUCH", "Zone clicked: ${matchedZone.linkedImagePath}")
                            }
                        } else {
                            Log.w("OVERLAY_TOUCH", "Matched zone has null or empty linkedImagePath")
                        }
                    }
                }
                longClickTriggered = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                Log.d("OVERLAY_TOUCH", "ACTION_CANCEL triggered, cancelling longClickRunnable")
                removeCallbacks(longClickRunnable)
                longClickTriggered = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("DEBUG", "DrawingView size = ${width}x${height}")
        Log.d("DEBUG", "imageDisplayWidth=$imageDisplayWidth, imageDisplayHeight=$imageDisplayHeight, imageOffsetX=$imageOffsetX, imageOffsetY=$imageOffsetY")
        if (showZonesOverlay && imageDisplayWidth > 0 && imageDisplayHeight > 0) {
            if (zones.isEmpty()) {
                Log.d("OVERLAY_TOUCH", "No zones to draw")
                return
            }
            Log.d("OVERLAY_TOUCH", "Drawing ${zones.size} zones")
            if (debugShowZones) {
                val bitmapRatioX = bitmapWidth / imageDisplayWidth
                val bitmapRatioY = bitmapHeight / imageDisplayHeight
                zones.forEach { zone ->
                    val scaledLeft = imageOffsetX + (zone.rect.left * imageDisplayWidth)
                    val scaledTop = imageOffsetY + (zone.rect.top * imageDisplayHeight)
                    val scaledRight = imageOffsetX + (zone.rect.right * imageDisplayWidth)
                    val scaledBottom = imageOffsetY + (zone.rect.bottom * imageDisplayHeight)

                    Log.d("DEBUG", "zone: left=${zone.rect.left}, top=${zone.rect.top}, right=${zone.rect.right}, bottom=${zone.rect.bottom}")
                    Log.d("DEBUG", "scaled: left=$scaledLeft, top=$scaledTop, right=$scaledRight, bottom=$scaledBottom")

                    val rect = RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)
                    canvas.drawRect(rect, overlayPaint)
                }
            }
        } else {
            Log.w("OVERLAY_TOUCH", "Skipping draw: imageDisplayWidth or imageDisplayHeight is zero")
        }
    }

    private fun findZoneAt(x: Float, y: Float): ZoneData? {
        Log.d("DEBUG", "Checking zones, count=${zones.size}, imageDisplayWidth=$imageDisplayWidth, imageDisplayHeight=$imageDisplayHeight")
        if (imageDisplayWidth <= 0 || imageDisplayHeight <= 0) return null
        Log.d("OVERLAY_TOUCH", "Checking zones, count=${zones.size}, imageDisplayWidth=$imageDisplayWidth, imageDisplayHeight=$imageDisplayHeight")
        val bitmapRatioX = bitmapWidth / imageDisplayWidth
        val bitmapRatioY = bitmapHeight / imageDisplayHeight
        // TODO (later): Use spatial index if zones list becomes large
        zones.forEach { zone ->
            val left = imageOffsetX + (zone.rect.left * imageDisplayWidth)
            val top = imageOffsetY + (zone.rect.top * imageDisplayHeight)
            val right = imageOffsetX + (zone.rect.right * imageDisplayWidth)
            val bottom = imageOffsetY + (zone.rect.bottom * imageDisplayHeight)
            Log.d("DEBUG", "→ checkZone left=$left, top=$top, right=$right, bottom=$bottom, x=$x, y=$y")
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
