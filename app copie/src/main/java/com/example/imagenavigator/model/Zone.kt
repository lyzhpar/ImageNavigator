package com.example.imagenavigator.model

import android.graphics.RectF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Zone cliquable rectangulaire, avec des coordonnées relatives.
 */
@Parcelize
data class Zone(
    val rect: RectF,                 // coordonnées relatives (0f → 1f)
    val targetImageName: String?,   // image cible (si cliqué)
    val audioFileName: String?      // son à jouer au clic (optionnel)
) : Parcelable