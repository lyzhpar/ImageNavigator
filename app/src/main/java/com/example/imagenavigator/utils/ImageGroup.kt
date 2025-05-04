package com.example.imagenavigator.utils

import android.graphics.Bitmap
import android.util.Log

data class ImageGroup(
    val name: String,
    val images: MutableList<Pair<Bitmap?, String>>,
    val children: List<ImageGroup> = listOf(),
    val fullPath: String? = null
) {
    companion object {
        fun fromTree(node: ImageGroupNode): List<ImageGroup> {
            return node.children.map { child ->
                Log.d("ImageGroup", "Conversion vers ImageGroup: ${child.name} | fullPath=${child.fullPath}")
                ImageGroup(
                    name = child.name,
                    images = child.images.map { Pair(it.first as? Bitmap, it.second) }.toMutableList(),
                    children = fromTree(child),
                    fullPath = child.fullPath
                )
            }
        }
    }
}
