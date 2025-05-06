package com.example.imagenavigator.utils

import android.graphics.Bitmap
import android.util.Log

data class ImageGroup(
    val name: String,
    val images: MutableList<String> = mutableListOf(),
    val children: List<ImageGroup> = listOf(),
    val fullPath: String? = null
) {
    companion object {
        fun fromTree(node: ImageGroupNode): List<ImageGroup> {
            return node.children.map { child ->
                Log.d("ImageGroup", "Conversion vers ImageGroup: ${child.name} | fullPath=${child.fullPath}")
                ImageGroup(
                    name = child.name,
                    images = child.images.toMutableList(),
                    children = fromTree(child),
                    fullPath = child.fullPath
                )
            }
        }
    }
}
