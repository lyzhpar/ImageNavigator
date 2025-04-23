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
import com.bumptech.glide.Glide

class ImageAdapter(
    private var rootGroups: List<ImageGroup>,
    private val onImageSelected: (Bitmap, String) -> Unit,
    private val onGroupRenameRequested: (DisplayItem) -> Unit,
    private val onGroupDeleteRequested: (DisplayItem) -> Unit
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
        for (group in groups.sortedBy { it.name }) {
            Log.d("Adapter", "Ajout de groupe: ${group.name} | fullPath=${group.fullPath}")
            val safeGroupName = group.name.ifBlank { "[nom inconnu]" }
            result.add(DisplayItem(ItemType.GROUP, safeGroupName, level = level, fullPath = group.fullPath ?: safeGroupName))
            if (expandedGroups.contains(group.fullPath ?: safeGroupName)) {
                result.addAll(group.images.map { (bitmap, name) ->
                    Log.d("Adapter", "Ajout image: $name dans ${group.fullPath}")
                    DisplayItem(ItemType.IMAGE, name.ifBlank { "[image]" }, bitmap, name.ifBlank { "[image]" }, level + 1, fullPath = name.ifBlank { "[image]" })
                })
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
        private val editText: android.widget.EditText = view.findViewById(R.id.worldNameEditText)
        private val deleteIcon: ImageView = view.findViewById(R.id.deleteGroupIcon)

        fun bind(item: DisplayItem) {
            val key = item.fullPath
            textView.text = "${"  ".repeat(item.level)}📁 ${item.name}"
            editText.setText(item.name)
            textView.visibility = View.VISIBLE
            editText.visibility = View.GONE
            deleteIcon.visibility = View.GONE

            itemView.setOnClickListener {
                val key = item.fullPath
                if (expandedGroups.contains(key)) {
                    expandedGroups.remove(key)
                } else {
                    expandedGroups.add(key)
                }
                displayItems = flattenGroups(rootGroups)
                notifyDataSetChanged()
            }

            itemView.setOnLongClickListener {
                textView.visibility = View.GONE
                editText.visibility = View.VISIBLE
                deleteIcon.visibility = View.VISIBLE
                editText.requestFocus()
                editText.setSelection(editText.text.length)
                true
            }

            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    val newName = editText.text.toString().trim()
                    if (newName.isNotBlank() && newName != item.name) {
                        onGroupRenameRequested(item.copy(name = newName))
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
                onGroupDeleteRequested(item)
            }
        }
    }

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.image_view)

        fun bind(item: DisplayItem) {
            Glide.with(imageView.context)
                .load(item.bitmap)
                .override(400, 250)
                .centerCrop()
                .thumbnail(Glide.with(imageView.context).load(item.bitmap).override(40, 25))
                .into(imageView)
                itemView.setOnClickListener {
                item.bitmap?.let { onImageSelected(it, item.fullPath) }
            }
        }
    }
}
