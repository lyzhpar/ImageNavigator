package com.example.imagenavigator.utils

import android.graphics.Bitmap
import android.util.Log

object ImageGroupTreeBuilder {
    fun buildImageGroupTree(images: List<Pair<Bitmap?, String>>): ImageGroupNode {
        val root = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())

        for ((bitmap, fullPath) in images) {
            Log.d("TreeBuilder", "Ajout image: $fullPath")
            val parts = fullPath.split("/").filter { it.isNotBlank() && it != "Racine" }
            if (parts.isEmpty()) continue

            var currentNode = root
            for (i in 0 until parts.size - 1) {
                val part = parts[i].trim()
                val existingChild = currentNode.children.find { it.name == part }
                if (existingChild != null) {
                    currentNode = existingChild
                } else {
                    val newNode = ImageGroupNode(name = part, parent = currentNode)
                    Log.d("TreeBuilder", "Création noeud: name=$part | fullPath=${newNode.fullPath}")
                    currentNode.children.add(newNode)
                    currentNode = newNode
                }
            }
            currentNode.images.add(bitmap to fullPath)
        }

        sortNodeRecursively(root)
        return root
    }

    private fun sortNodeRecursively(node: ImageGroupNode) {
        node.children.sortBy { it.name }
        node.children.forEach { sortNodeRecursively(it) }
        node.images.sortBy { it.second }
    }
}