package com.example.imagenavigator.model

import android.graphics.RectF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Zone cliquable rectangulaire, avec des coordonnées relatives.
 */
@Parcelize
data class Zone(
    val rect: RectF,
    var linkedImagePath: String? = null
) : Parcelable