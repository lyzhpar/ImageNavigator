package com.example.imagenavigator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

object ThumbnailLoader {
    fun load(
        context: Context,
        imagePath: String,
        width: Int = 100,
        height: Int = 100,
        onReady: (Bitmap) -> Unit
    ) {
        Glide.with(context)
            .asBitmap()
            .load(imagePath)
            .override(width, height)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    onReady(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Aucun traitement nécessaire ici
                }
            })
    }
}