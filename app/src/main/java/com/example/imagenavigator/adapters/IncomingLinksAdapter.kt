package com.example.imagenavigator.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.imagenavigator.R
import com.example.imagenavigator.screens.ImageClickSource

class IncomingLinksAdapter(
    private val onImageSelected: (String, ImageClickSource, Boolean) -> Unit,
    var imageFileMap: Map<String, DocumentFile>,
    var selectedPaths: Set<String> = emptySet()
) : ListAdapter<String, IncomingLinksAdapter.ImageItemViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_compact, parent, false)
        return ImageItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageItemViewHolder, position: Int) {
        val imagePath = getItem(position)
        val documentFile = imageFileMap[imagePath]
        val density = holder.imageView.context.resources.displayMetrics.density
        val widthPx = (120 * density).toInt() // largeur 80dp
        val heightPx = (80 * density).toInt() // hauteur 60dp = 4:3
        if (documentFile != null && documentFile.exists()) {
            Glide.with(holder.imageView.context)
                .load(documentFile.uri)
                .override(widthPx, heightPx) // 4:3 ratio
                .centerCrop()
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(android.R.drawable.stat_notify_error)
        }

        holder.itemView.setOnClickListener {
            onImageSelected(imagePath, ImageClickSource.SIDEBAR, false)
        }
    }

    override fun onViewRecycled(holder: ImageItemViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.imageView.context).clear(holder.imageView)
    }

    class ImageItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_view)
    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
