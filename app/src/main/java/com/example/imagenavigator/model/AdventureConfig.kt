package com.example.imagenavigator.model

import android.graphics.RectF

/**
 * Représente la configuration complète d'une aventure Théodyssée.
 * Ce modèle est utilisé pour l'enregistrement et la lecture en JSON.
 */
data class AdventureConfig(
    var name: String,
    var mainImage: String?, // chemin relatif de l'image de départ
    var worlds: MutableMap<String, MutableList<String>>, // dossier/monde -> liste des images
    var links: MutableMap<String, MutableList<ZoneLink>> // image source -> liste de zones cliquables
)

/**
 * Zone cliquable sur une image, exprimée en coordonnées relatives (0f à 1f).
 */
data class ZoneLink(
    val rect: RectF, // zone sous forme de rectangle relatif à l'image
    var target: String, // image de destination
    var sound: String? = null // son optionnel à jouer
)