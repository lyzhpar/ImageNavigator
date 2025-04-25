package com.example.imagenavigator.model

/**
 * Données complètes d'une interface de navigation.
 */
data class Configuration(
    val name: String,                   // nom de la configuration (ex : "Navigation principale")
    val backgroundColor: String,       // couleur de fond hexadécimale (ex : "#ffffff")
    val startImageName: String?,       // nom de l'image de départ (celle marquée par une étoile)
    val images: List<ImageData>        // liste des images avec leurs zones
)