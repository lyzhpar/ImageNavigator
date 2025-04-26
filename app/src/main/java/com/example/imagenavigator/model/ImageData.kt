package com.example.imagenavigator.model

data class ImageData(
    val imageName: String,         // nom du fichier image
    val zones: List<Zone>,         // zones cliquables sur cette image

)