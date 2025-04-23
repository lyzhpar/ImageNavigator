package com.example.imagenavigator.utils

import android.graphics.Bitmap

data class ImageGroupNode(
    val name: String,
    val parent: ImageGroupNode? = null,
    val images: MutableList<Pair<Bitmap, String>> = mutableListOf(),
    val children: MutableList<ImageGroupNode> = mutableListOf(),
    var isExpanded: Boolean = false
) {
    val fullPath: String
        get() = parent?.let { "${it.fullPath}/$name" } ?: name
}