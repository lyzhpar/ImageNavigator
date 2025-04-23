package com.example.imagenavigator.utils

import android.graphics.Bitmap
import android.util.Log
import com.example.imagenavigator.utils.ImageGroupNode

object ImageGroupTreeBuilder {
    fun buildImageGroupTree(imagePaths: List<Pair<Bitmap, String>>): ImageGroupNode {
        val root = ImageGroupNode(name = "Racine")

        for ((bitmap, fullPath) in imagePaths) {
            Log.d("TreeBuilder", "Ajout image: $fullPath")
            val parts = fullPath.split("/")
            var currentNode = root

            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                val existingChild = currentNode.children.find { it.name == part }
                if (existingChild != null) {
                    currentNode = existingChild
                } else {
                    val newNode = ImageGroupNode(
                        name = part,
                        fullPath = if (currentNode.fullPath != null) "${currentNode.fullPath}/$part" else part
                    )
                    Log.d("TreeBuilder", "Création noeud: name=$part | fullPath=${newNode.fullPath}")
                    currentNode.children.add(newNode)
                    currentNode = newNode
                }
            }

            currentNode.images.add(bitmap to fullPath)
        }

        return root
    }
}