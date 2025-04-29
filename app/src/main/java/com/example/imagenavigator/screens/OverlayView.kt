package com.example.imagenavigator

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.imagenavigator.model.ZoneData

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var zones: List<ZoneData> = emptyList()
    var onZoneClicked: ((String) -> Unit)? = null // Appelé quand on clique sur une zone valide

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / width   // Position relative X (0..1)
        val y = event.y / height  // Position relative Y (0..1)

        if (event.action == MotionEvent.ACTION_DOWN) {
            // Détection du clic
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