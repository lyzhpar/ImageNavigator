package com.example.imagenavigator.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.imagenavigator.R
import com.example.imagenavigator.utils.ImageGroup
import android.util.Log
import com.bumptech.glide.Glide
import android.graphics.Color
import android.graphics.RectF
import com.example.imagenavigator.model.ZoneData
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ImageAdapter(
    private var rootGroups: List<ImageGroup>,
    private val onImageSelected: (String) -> Unit,
    private val onItemLongPress: (DisplayItem) -> Unit,
    var imageFileMap: Map<String, DocumentFile>
) : ListAdapter<ImageAdapter.DisplayItem, RecyclerView.ViewHolder>(DiffCallback()) {

    var startImagePath: String? = null

    private val expandedGroups = mutableSetOf<String>()
    private var displayItems = flattenGroups(rootGroups)

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<String>()

    var imageZonesMap: Map<String, List<ZoneData>> = emptyMap()

    // Ajout de la variable linkedImagePaths pour les images liées à une zone
    var linkedImagePaths: Set<String> = emptySet()

    sealed class DisplayItem {
        abstract val fullPath: String

        data class ImageItem(override val fullPath: String) : DisplayItem()
        data class GroupItem(val name: String, override val fullPath: String) : DisplayItem()
    }

    class DiffCallback : DiffUtil.ItemCallback<DisplayItem>() {
        override fun areItemsTheSame(oldItem: DisplayItem, newItem: DisplayItem): Boolean {
            return oldItem.fullPath == newItem.fullPath
        }

        override fun areContentsTheSame(oldItem: DisplayItem, newItem: DisplayItem): Boolean {
            return oldItem == newItem
        }
    }


    fun updateData(newGroups: List<ImageGroup>) {
        rootGroups = newGroups.toMutableList()
        displayItems = flattenGroups(rootGroups)
        submitList(displayItems)
    }

    fun addImage(fullPath: String) {
        val mainGroupName = fullPath.substringBefore("/", "Racine")
        var group = rootGroups.find { it.name == mainGroupName }

        if (group == null) {
            group =
                ImageGroup(name = mainGroupName, images = mutableListOf(), fullPath = mainGroupName)
            rootGroups = rootGroups + group
        }

        // Ensure no duplicate images
        if (!group.images.contains(fullPath)) {
            group.images.add(fullPath)
        }

        displayItems = flattenGroups(rootGroups)
        submitList(displayItems)
    }

    private fun flattenGroups(groups: List<ImageGroup>, level: Int = 0): List<DisplayItem> {
        val result = mutableListOf<DisplayItem>()
        val sortedGroups = groups.sortedWith(compareBy({ it.name != "Racine" }, { it.name }))
        for (group in sortedGroups) {
            Log.d("Adapter", "Ajout de groupe: ${group.name} | fullPath=${group.fullPath}")
            val safeGroupName = group.name.ifBlank { "[nom inconnu]" }
            val groupKey = group.fullPath ?: safeGroupName

            result.add(DisplayItem.GroupItem(safeGroupName, groupKey))

            val shouldExpand = groupKey.isBlank() || expandedGroups.contains(groupKey)
            if (shouldExpand) {
                result.addAll(group.images.map { name ->
                    DisplayItem.ImageItem(name)
                })
                result.addAll(flattenGroups(group.children, level + 1))
            }
        }
        return result
    }


    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DisplayItem.GroupItem -> 0
        is DisplayItem.ImageItem -> 1
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

    override fun getItemCount(): Int = currentList.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is GroupViewHolder -> {
                holder.bind(item as DisplayItem.GroupItem)
                holder.itemView.setOnLongClickListener {
                    Log.d("ImageAdapter", "Long press sur : ${item.fullPath}")
                    onItemLongPress(item as DisplayItem.GroupItem)
                    true
                }
            }

            is ImageViewHolder -> {
                holder.bind(item as DisplayItem.ImageItem)
                holder.itemView.setOnLongClickListener {
                    Log.d("ImageAdapter", "Long press sur : ${item.fullPath}")
                    onItemLongPress(item as DisplayItem.ImageItem)
                    true
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ImageViewHolder) {
            Glide.with(holder.imageView.context).clear(holder.imageView)
        }
    }

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.worldNameTextView)
        private val folderIcon: ImageView = view.findViewById(R.id.folderIcon)
        private val checkbox: ImageView = view.findViewById(R.id.checkbox)

        fun bind(item: DisplayItem.GroupItem) {
            textView.text = item.name
            textView.setTypeface(
                null,
                if (item.name == "Racine") android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL
            )
            textView.visibility = View.VISIBLE

            val isExpanded = expandedGroups.contains(item.fullPath)
            folderIcon.setImageResource(
                if (isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_left
            )

            itemView.setOnClickListener {
                if (expandedGroups.contains(item.fullPath)) {
                    expandedGroups.remove(item.fullPath)
                } else {
                    expandedGroups.add(item.fullPath)
                }
                displayItems = flattenGroups(rootGroups)
                submitList(displayItems)
            }
        }
    }

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_view)
        private val overlayView: View? = view.findViewById(R.id.zoneOverlayView)
        private val checkbox: ImageView = view.findViewById(R.id.checkbox)  // La coche pour l'image
        fun bind(item: DisplayItem) {

            if (item is DisplayItem.ImageItem) {
                val zones = imageZonesMap[item.fullPath]
                overlayView?.visibility =
                    if (!zones.isNullOrEmpty() && zones.any { it.linkedImagePath != null })
                        View.VISIBLE
                    else View.GONE

                Log.d("ImageAdapter", "Bind image: ${item.fullPath}")

                val documentFile = imageFileMap[item.fullPath]
                if (documentFile != null && documentFile.exists()) {
                    val currentFullPath = item.fullPath
                    var retryCount = 0

                    fun loadImageWithRetry() {
                        Glide.with(imageView.context)
                            .load(documentFile.uri)
                            .override(400, 250)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Log.e(
                                        "ImageAdapter",
                                        "Erreur de chargement image: $currentFullPath, essai $retryCount",
                                        e
                                    )
                                    if (retryCount < 3) {
                                        Glide.get(imageView.context).clearMemory()
                                        val delay = 500L * (1 shl retryCount)
                                        retryCount++
                                        imageView.postDelayed({ loadImageWithRetry() }, delay)
                                    } else {
                                        imageView.setImageResource(android.R.drawable.stat_notify_error)
                                        imageView.setOnClickListener {
                                            retryCount = 0
                                            loadImageWithRetry()
                                        }
                                    }
                                    return true
                                }

                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    if (adapterPosition != RecyclerView.NO_POSITION && currentList[adapterPosition].fullPath == currentFullPath) {
                                        return false
                                    }
                                    return true
                                }
                            })
                            .into(imageView)
                    }

                    loadImageWithRetry()
                } else {
                    android.util.Log.e(
                        "ImageAdapter",
                        "DocumentFile invalide ou inexistant: ${item.fullPath}"
                    )
                }
                when {
                    startImagePath == item.fullPath -> {
                        imageView.setColorFilter(Color.parseColor("#80FFD700")) // doré
                        checkbox.visibility = View.VISIBLE
                    }

                    selectedItems.contains(item.fullPath) -> {
                        imageView.setColorFilter(Color.argb(150, 128, 128, 128)) // gris
                        checkbox.visibility = View.VISIBLE
                    }

                    else -> {
                        imageView.clearColorFilter()
                        checkbox.visibility = View.GONE
                    }
                }
                itemView.setOnClickListener {
                    if (item.fullPath.isNotBlank()) {
                        onImageSelected(item.fullPath)
                    }
                }
                itemView.setOnLongClickListener {
                    onItemLongPress(item)
                    true
                }
            }
        }

        // Permet de forcer le rafraîchissement complet du RecyclerView
        fun forceRefresh() {
            submitList(displayItems.toList())
        }
    }
}