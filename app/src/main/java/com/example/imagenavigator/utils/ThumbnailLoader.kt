package com.example.imagenavigator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.net.Uri

object ThumbnailLoader {
    fun load(
        context: Context,
        imageUri: Uri,
        width: Int = 200,
        height: Int = 200,
        onReady: (Bitmap, Float) -> Unit
    ) {
        Glide.with(context)
            .asBitmap()
            .load(imageUri)
            .override(width, height)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val aspectRatio = resource.width.toFloat() / resource.height.toFloat()
                    onReady(resource, aspectRatio)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Aucun traitement nécessaire ici
                }
            })
    }
}