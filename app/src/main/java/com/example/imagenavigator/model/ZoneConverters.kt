package com.example.imagenavigator.model

import android.graphics.RectF

fun ZoneData.toZone(): Zone {
    return Zone(
        rect = RectF(rect.left, rect.top, rect.right, rect.bottom),
        linkedImagePath = linkedImagePath
    )
}

fun Zone.toZoneData(): ZoneData {
    return ZoneData(
        linkedImagePath = linkedImagePath,
        rect = RectData(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )
    )
}