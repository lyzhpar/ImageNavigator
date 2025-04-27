package com.example.imagenavigator.model

data class AdventureData(
    val adventureTitle: String,
    val images: List<ImageData>,
    val folderUri: String? = null   // 🆕 chemin du dossier source
)