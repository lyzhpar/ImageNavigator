package com.example.imagenavigator

import android.content.Context
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
    var onZoneClicked: ((String) -> Unit)? = null // Appelé quand on clique sur une zone valide
    var onLongClickAt: ((Float, Float) -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / width
        val y = event.y / height

        if (event.action == MotionEvent.ACTION_DOWN) {
            postDelayed({
                performLongClick()
                onLongClickAt?.invoke(event.rawX, event.rawY)
            }, ViewConfiguration.getLongPressTimeout().toLong())
            Log.d("OVERLAY", "overlayView size: $width x $height")
            if (width < 5000 && height < 5000) {
                postDelayed({ performLongClick() }, ViewConfiguration.getLongPressTimeout().toLong())
            } else {
                Log.e("OVERLAY", "overlayView trop grand, long-clic ignoré")
            }
            val clickedZone = findZoneAt(x, y)
            clickedZone?.linkedImagePath?.let { targetPath ->
                onZoneClicked?.invoke(targetPath)
            }
        }
        return true
    }

    private fun findZoneAt(x: Float, y: Float): ZoneData? {
        return zones.firstOrNull { zone ->
            val rect = zone.rect
            x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
        }
    }
}