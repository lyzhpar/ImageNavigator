package com.example.imagenavigator.model

import android.graphics.Bitmap


sealed class DisplayItem(open val fullPath: String) {
    data class ImageItem(val bitmap: Bitmap, override val fullPath: String) : DisplayItem(fullPath)
    data class GroupItem(val name: String, override val fullPath: String) : DisplayItem(fullPath)
}