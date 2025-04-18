package com.example.imagenavigator.model

/**
 * Représente une image dans la configuration.
 */
data class ImageData(
    val imageName: String,         // nom du fichier image
    val zones: List<Zone>,         // zones cliquables sur cette image
    val audioFileName: String?     // fichier audio associé à l’image
)