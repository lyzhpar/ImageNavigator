package com.example.imagenavigator.model

import android.graphics.RectF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Zone cliquable rectangulaire, avec des coordonnées relatives.
 */
@Parcelize
data class Zone(
    var rect: RectF,                 // coordonnées relatives (0f → 1f)
    var linkedImagePath: String? = null,   // image cible (si cliqué)
    var audioFileName: String?= null      // son à jouer au clic (optionnel)
) : Parcelable