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

class ImageAdapter(
    private var rootGroups: List<ImageGroup>,
    private val onImageSelected: (String) -> Unit,
    private val onGroupRenameRequested: () -> Unit,
    private val onGroupDeleteRequested: () -> Unit,
    private val onItemLongPress: (DisplayItem) -> Unit,
    private val getSelectedItems: () -> Set<String>,
    private val exitSelectionMode: () -> Unit
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
        data class ImageItem(val bitmap: Bitmap, override val fullPath: String) : DisplayItem()
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

    fun getSelectedItems(): Set<String> {
        return selectedItems
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        submitList(displayItems)
    }

    fun updateData(newGroups: List<ImageGroup>) {
        rootGroups = newGroups
        val racine = newGroups.find { it.name == "Racine" }
        racine?.let {
            val key = it.fullPath ?: it.name
            if (!expandedGroups.contains(key)) {
                expandedGroups.add(key)
            }
        }
        displayItems = flattenGroups(newGroups)
        submitList(displayItems)
    }

    fun addImage(bitmap: Bitmap, fullPath: String) {
        // Déduire le nom du groupe principal (premier dossier du chemin)
        val mainGroupName = fullPath.substringBefore("/", "Racine")
        var group = rootGroups.find { it.name == mainGroupName }

        if (group == null) {
            group = ImageGroup(name = mainGroupName, images = mutableListOf(), fullPath = mainGroupName)
            rootGroups = rootGroups + group
        }

        // Ajouter l'image au groupe
        group.images.add(bitmap to fullPath)

        // Mettre à jour la liste aplatie pour l'affichage
        displayItems = flattenGroups(rootGroups)
        submitList(displayItems)
    }

    private fun flattenGroups(groups: List<ImageGroup>, level: Int = 0): List<DisplayItem> {
        val result = mutableListOf<DisplayItem>()
        val sortedGroups = groups.sortedWith(compareBy({ it.name != "Racine" }, { it.name }))
        for (group in sortedGroups) {            Log.d("Adapter", "Ajout de groupe: ${group.name} | fullPath=${group.fullPath}")
            val safeGroupName = group.name.ifBlank { "[nom inconnu]" }
            result.add(DisplayItem.GroupItem(safeGroupName, group.fullPath ?: safeGroupName))
            val key = group.fullPath ?: safeGroupName
            val shouldExpand = key.isBlank() || expandedGroups.contains(key)
            if (shouldExpand) {
                result.addAll(group.images.map { (bitmap, name) ->
                    Log.d("Adapter", "Ajout image: $name dans ${group.fullPath}")
                    val safeName = name.ifBlank { "[image]" }
                    DisplayItem.ImageItem(bitmap, safeName)
                })
                result.addAll(flattenGroups(group.children, level + 1))
            }
        }
        return result
    }

    fun setSelectionMode(isSelectionMode: Boolean, selectedItems: Set<String>) {
        this.isSelectionMode = isSelectionMode
        this.selectedItems.clear()
        this.selectedItems.addAll(selectedItems)
        submitList(displayItems)
    }

    override fun getItemViewType(position: Int): Int = when(getItem(position)) {
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

                if (startImagePath == item.fullPath) {
                    holder.imageView.setColorFilter(Color.parseColor("#80FFD700")) // Filtre doré
                } else {
                    holder.imageView.clearColorFilter() // Retirer le filtre si non liée
                }
            }
        }
    }

    private fun toggleSelection(fullPath: String) {
        if (selectedItems.contains(fullPath)) {
            selectedItems.remove(fullPath)
        } else {
            selectedItems.add(fullPath)
        }
        submitList(displayItems)
    }

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.worldNameTextView)
        private val editText: android.widget.EditText = view.findViewById(R.id.worldNameEditText)
        private val deleteIcon: ImageView = view.findViewById(R.id.deleteGroupIcon)
        private val checkbox: ImageView = view.findViewById(R.id.checkbox)  // La coche pour le groupe



        fun bind(item: DisplayItem.GroupItem) {
            val key = item.fullPath
            textView.text = "📁 ${item.name}"
            textView.setTypeface(null, if (item.name == "Racine") android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)
            editText.setText(item.name)
            textView.visibility = View.VISIBLE
            editText.visibility = View.GONE
            deleteIcon.visibility = View.GONE

            // Appliquer un fond gris si sélectionné
            if (selectedItems.contains(item.fullPath)) {
                itemView.setBackgroundColor(Color.GRAY)  // Griser tout le groupe
                itemView.alpha = 1f  // Pas de transparence
                checkbox.visibility = View.VISIBLE  // Afficher la coche
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT)
                itemView.alpha = 1f  // Pas de transparence
                checkbox.visibility = View.GONE  // Masquer la coche
            }

            itemView.setOnClickListener {
                val key = item.fullPath
                if (isSelectionMode) {
                    onItemLongPress(item) // Si en mode sélection, on sélectionne/désélectionne
                } else {
                    if (expandedGroups.contains(key)) {
                        expandedGroups.remove(key)
                    } else {
                        expandedGroups.add(key)
                    }
                    displayItems = flattenGroups(rootGroups)
                    submitList(displayItems)
                }
            }

            itemView.setOnLongClickListener {
                onItemLongPress(item)  // Gérer la sélection longue pression
                true
            }

            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    val newName = editText.text.toString().trim()
                    if (newName.isNotBlank() && newName != item.name) {
                        onGroupRenameRequested()
                    }
                    textView.visibility = View.VISIBLE
                    editText.visibility = View.GONE
                    deleteIcon.visibility = View.GONE
                    true
                } else {
                    false
                }
            }

            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    textView.visibility = View.VISIBLE
                    editText.visibility = View.GONE
                    deleteIcon.visibility = View.GONE
                }
            }

            deleteIcon.setOnClickListener {
                onGroupDeleteRequested()
            }
        }
    }
    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_view)
        private val checkbox: ImageView = view.findViewById(R.id.checkbox)  // La coche pour l'image


        fun bind(item: DisplayItem) {
            if (item is DisplayItem.ImageItem) {
                var displayBitmap = item.bitmap

                if (displayBitmap.isRecycled) {
                    Log.e("ImageAdapter", "Bitmap recyclé détecté pour ${item.fullPath}, on saute le bind")
                    return
                }

                // Si l'image a des zones liées, dessiner les rectangles verts dessus
                val zones = imageZonesMap[item.fullPath]
                if (!zones.isNullOrEmpty()) {
                    val mutableBitmap = try {
                        item.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    } catch (e: Exception) {
                        item.bitmap
                    }
                    val canvas = android.graphics.Canvas(mutableBitmap)
                    // Opacité et couleur vert
                    val paint = android.graphics.Paint().apply { color = Color.argb(60, 0, 255, 0) }

                    for (zone in zones) {
                        if (zone.linkedImagePath != null) {
                            val rect = zone.rect
                            val left = rect.left * mutableBitmap.width
                            val top = rect.top * mutableBitmap.height
                            val right = rect.right * mutableBitmap.width
                            val bottom = rect.bottom * mutableBitmap.height
                            canvas.drawRect(left, top, right, bottom, paint)
                        }
                    }
                    displayBitmap = mutableBitmap
                }

                Log.d("ImageAdapter", "Bind image: ${item.fullPath}")

                if (!displayBitmap.isRecycled) {
                    Glide.with(imageView.context)
                        .load(displayBitmap)
                        .override(400, 250)
                        .centerCrop()
                        .thumbnail(Glide.with(imageView.context).load(displayBitmap).override(40, 25))
                        .into(imageView)
                } else {
                    imageView.setImageDrawable(null)
                }
            } else if (item is DisplayItem.GroupItem) {
                imageView.setImageResource(R.drawable.folder_icon)
            }

            if (selectedItems.contains(item.fullPath)) {
                imageView.setColorFilter(Color.argb(150, 128, 128, 128))
                checkbox.visibility = View.VISIBLE
            } else {
                imageView.clearColorFilter()
                checkbox.visibility = View.GONE
            }

            if (startImagePath == item.fullPath) {
                imageView.setColorFilter(Color.parseColor("#80FFD700")) // Filtre doré pour image de départ
                checkbox.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                onImageSelected(item.fullPath)
            }

            itemView.setOnLongClickListener {
                onItemLongPress(item)
                true
            }
        }
    }
}
