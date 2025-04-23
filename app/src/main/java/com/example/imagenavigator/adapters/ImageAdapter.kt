package com.example.imagenavigator.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.imagenavigator.R
import com.example.imagenavigator.utils.ImageGroup
import android.util.Log

class ImageAdapter(
    private var rootGroups: List<ImageGroup>,
    private val onImageSelected: (Bitmap, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val expandedGroups = mutableSetOf<String>()
    private var displayItems = flattenGroups(rootGroups)

    enum class ItemType { GROUP, IMAGE }

    data class DisplayItem(
        val type: ItemType,
        val name: String,
        val bitmap: Bitmap? = null,
        val imagePath: String? = null,
        val level: Int = 0,
        val fullPath: String
    )

    fun updateData(newGroups: List<ImageGroup>) {
        rootGroups = newGroups
        displayItems = flattenGroups(newGroups)
        notifyDataSetChanged()
    }

    private fun flattenGroups(groups: List<ImageGroup>, level: Int = 0): List<DisplayItem> {
        val result = mutableListOf<DisplayItem>()
        for (group in groups) {
            val groupPath = group.fullPath
            Log.d("Adapter", "Ajout de groupe: ${group.name} | fullPath=${group.fullPath}")
            result.add(DisplayItem(ItemType.GROUP, group.fullPath, level = level, fullPath = groupPath))
            if (expandedGroups.contains(groupPath)) {
                result.addAll(group.images.map { (bitmap, name) ->
                    Log.d("Adapter", "Ajout image: $name dans ${group.fullPath}")
                DisplayItem(ItemType.IMAGE, name, bitmap, name, level + 1, fullPath = name) })
                result.addAll(flattenGroups(group.children, level + 1))
            }
        }
        return result
    }

    override fun getItemViewType(position: Int): Int = when (displayItems[position].type) {
        ItemType.GROUP -> 0
        ItemType.IMAGE -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val view = inflater.inflate(R.layout.item_group, parent, false)
            GroupViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_image, parent, false)
            ImageViewHolder(view)
        }
    }

    override fun getItemCount(): Int = displayItems.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayItems[position]
        Log.d("onBind", "Bind type: ${item.type} | name: ${item.name} | fullPath: ${item.fullPath}")
        when (holder) {
            is GroupViewHolder -> holder.bind(item)
            is ImageViewHolder -> holder.bind(item)
        }
    }

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.worldNameTextView)

        fun bind(item: DisplayItem) {
            val key = item.fullPath
            textView.text = "${"  ".repeat(item.level)}📁 ${item.name}"
            itemView.setOnClickListener {
                if (expandedGroups.contains(key)) {
                    expandedGroups.remove(key)
                } else {
                    expandedGroups.add(key)
                }
                displayItems = flattenGroups(rootGroups)
                notifyDataSetChanged()
            }
        }
    }

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.image_view)

        fun bind(item: DisplayItem) {
            imageView.setImageBitmap(item.bitmap)
            itemView.setOnClickListener {
                item.bitmap?.let { onImageSelected(it, item.fullPath) }
            }
        }
    }
}
