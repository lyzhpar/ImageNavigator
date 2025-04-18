package com.example.imagenavigator.model

import android.graphics.RectF

/**
 * Zone cliquable rectangulaire, avec des coordonnées relatives.
 */
data class Zone(
    val rect: RectF,                 // coordonnées relatives (0f → 1f)
    val targetImageName: String?,   // image cible (si cliqué)
    val audioFileName: String?      // son à jouer au clic (optionnel)
)