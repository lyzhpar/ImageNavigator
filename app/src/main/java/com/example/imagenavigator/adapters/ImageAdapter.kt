package com.example.imagenavigator.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.imagenavigator.R

class ImageAdapter(
    private val images: List<Pair<Bitmap, String>>,
    private val onImageClick: (Bitmap, String) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.image_view)

        fun bind(bitmap: Bitmap, name: String) {
            thumbnail.setImageBitmap(bitmap)
            thumbnail.setOnClickListener {
                onImageClick(bitmap, name)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (bitmap, name) = images[position]
        holder.bind(bitmap, name)
    }

    override fun getItemCount(): Int = images.size
}