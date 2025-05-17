package com.example.imagenavigator.adapters

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
import com.example.imagenavigator.model.ZoneData
import com.example.imagenavigator.screens.ImageClickSource
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.imagenavigator.screens.ZoneOverlayView
import kotlin.collections.addAll
import kotlin.text.clear

class ImageAdapter(
    private var rootGroups: List<ImageGroup>,
    private val onImageSelected: (String, ImageClickSource, Boolean) -> Unit,
    private val onItemLongPress: (DisplayItem) -> Unit,
    var imageFileMap: Map<String, DocumentFile>,
    private val layoutResId: Int = R.layout.item_image
) : ListAdapter<ImageAdapter.DisplayItem, RecyclerView.ViewHolder>(DiffCallback()) {

    private var showZones: Boolean = true
    private var showZoneThumbnails: Boolean = true
    private var layoutResIdModifiable: Int = layoutResId

    var startImagePath: String? = null

    private val expandedGroups = mutableSetOf<String>()
    private var displayItems = flattenGroups(rootGroups)

    private val selectedItems = mutableSetOf<String>()

    var imageZonesMap: Map<String, List<ZoneData>> = emptyMap()
    var highlightedPaths: Set<String> = emptySet()

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

    fun scrollToThumbnail(imagePath: String, recyclerView: RecyclerView?) {
        // Étendre le groupe si nécessaire
        expandGroupForImage(imagePath)

        // Recalculer la liste une fois le groupe étendu
        displayItems = flattenGroups(rootGroups)
        submitList(displayItems.toList())

        // Rechercher l'index dans la liste visible
        val index = currentList.indexOfFirst { it.fullPath == imagePath }
        if (index != -1 && recyclerView != null) {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val itemHeight = recyclerView.findViewHolderForAdapterPosition(index)?.itemView?.height ?: 120
            val recyclerViewHeight = recyclerView.height
            val offset = recyclerViewHeight / 2 - itemHeight / 2
            layoutManager.scrollToPositionWithOffset(index, offset)
        }
    }

    fun expandGroupForImage(imagePath: String) {
        val groupPath = imagePath.substringBeforeLast("/", missingDelimiterValue = "")
        val group = findGroupByPath(rootGroups, groupPath)
        if (group != null) {
            expandedGroups.add(group.fullPath ?: return)
        }
    }

    private fun findGroupByPath(groups: List<ImageGroup>, path: String): ImageGroup? {
        for (group in groups) {
            if (group.fullPath == path) return group
            val found = findGroupByPath(group.children, path)
            if (found != null) return found
        }
        return null
    }


    fun updateData(newGroups: List<ImageGroup>) {
        rootGroups = newGroups.toMutableList()
        if (rootGroups.any { it.name == "Racine" }) {
            expandedGroups.add("Racine")
        }
        displayItems = flattenGroups(rootGroups)
        submitList(displayItems)
    }

    fun refreshVisibleItems(newMap: Map<String, List<ZoneData>>) {
        // On soumet une copie pour forcer le diff et relancer les bind()
        Log.d("ImageAdapter", "refreshVisibleItems() → appel submitList(displayItems.toList())")
        imageZonesMap = newMap
        submitList(displayItems.toList())
    }

    /**
     * Met à jour la map des zones d'image et rafraîchit la vue.
     * À utiliser après avoir modifié imageZonesMap pour garantir le rafraîchissement immédiat.
     */
    fun updateImageZonesMapAndRefresh(newMap: Map<String, List<ZoneData>>) {
        Log.d("ImageAdapter", "updateImageZonesMapAndRefresh appelé, nouvelles zones pour ${newMap.size} images")
        refreshVisibleItems(newMap)
        // Ajout : notifier les items concernés
        newMap.keys.forEach { path ->
            val index = currentList.indexOfFirst { it.fullPath == path }
            if (index != -1) {
                Log.d("ImageAdapter", "notifyItemChanged forcé pour $path à l'index $index")
                notifyItemChanged(index)
            }
        }
    }

    fun addImage(fullPath: String) {
        val isInRoot = !fullPath.contains("/")
        val mainGroupName = if (isInRoot) "Racine" else fullPath.substringBefore("/")
        var group = rootGroups.find { it.name == mainGroupName }

        if (!isInRoot && mainGroupName == "Racine") {
            // Évite de créer un groupe "Racine" pour une image située dans un dossier nommé "Racine"
            return
        }

        if (group == null) {
            group = ImageGroup(name = mainGroupName, images = mutableListOf(), fullPath = mainGroupName)
            rootGroups = rootGroups + group
        }

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

            // Ignore totally empty groups (no images and no children)
            if (group.images.isEmpty() && group.children.isEmpty()) {
                continue  // Ignore les groupes totalement vides
            }
            result.add(DisplayItem.GroupItem(safeGroupName, groupKey))
            //Groupe Racine toujours étendu/ouvert
            val shouldExpand = expandedGroups.contains(groupKey)
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
            val view = inflater.inflate(layoutResIdModifiable, parent, false)
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
                    onItemLongPress(item)
                    true
                }
            }

            is ImageViewHolder -> {
                holder.bind(item as DisplayItem.ImageItem)
                if (highlightedPaths.contains(item.fullPath)) {
                    holder.imageView.setBackgroundResource(R.drawable.incoming_border)
                } else {
                    holder.imageView.setBackgroundResource(0)
                }
                holder.itemView.setOnLongClickListener {
                    onItemLongPress(item)
                    true
                }

                val imagePath = item.fullPath
                val zones = imageZonesMap[imagePath]
                    ?.filter { it.linkedImagePath != null }
                    ?: emptyList()

                (holder as? ImageViewHolder)?.overlayView?.let { view ->
                    if (view is ZoneOverlayView) {
                        view.zones = zones
                        view.invalidate() // Force redraw immediately
                    }
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
            textView.text = "📁 ${item.name}"
            textView.setTypeface(
                null,
                if (item.name == "Racine") android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL
            )
            textView.visibility = View.VISIBLE

            val isExpanded = expandedGroups.contains(item.fullPath)
            folderIcon.setImageResource(
                if (isExpanded) R.drawable.ic_arrow_down_02 else R.drawable.ic_arrow_left
            )

            itemView.setOnClickListener {
                if (expandedGroups.contains(item.fullPath)) {
                    expandedGroups.remove(item.fullPath)
                } else {
                    expandedGroups.add(item.fullPath)
                }

                displayItems = flattenGroups(rootGroups)
                submitList(displayItems.toList())  // met à jour currentList proprement

                // On force le rebind du groupe pour mettre à jour la flèche
                val index = displayItems.indexOfFirst {
                    it is DisplayItem.GroupItem && it.fullPath == item.fullPath
                }
                if (index != -1) notifyItemChanged(index)
            }
        }
    }

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_view)
        val overlayView: ZoneOverlayView = view.findViewById(R.id.zoneOverlay)
        private val checkbox: ImageView = view.findViewById(R.id.checkbox)  // La coche pour l'image
        fun bind(item: DisplayItem) {

            if (item is DisplayItem.ImageItem) {
                val zones = imageZonesMap[item.fullPath]?.filter { it.linkedImagePath != null } ?: emptyList()
                Log.d("BindDebug", "Image: ${item.fullPath} → ${zones.size} zone(s) liées")
                zones.forEachIndexed { i, zone ->
                    Log.d("BindDebug", "  Zone[$i] linkedImagePath = ${zone.linkedImagePath}")
                }
                overlayView?.visibility = if (zones.isNotEmpty()) View.VISIBLE else View.GONE
                overlayView?.zones = zones
                overlayView?.invalidate()

                // Log.d("ImageAdapter", "Bind image: ${item.fullPath}")

                val documentFile = imageFileMap[item.fullPath]
                if (documentFile != null && documentFile.exists()) {
                    val currentFullPath = item.fullPath
                    var retryCount = 0

                    fun loadImageWithRetry() {
                        val density = imageView.context.resources.displayMetrics.density
                        val isCompact = imageView.width < 150
                        val sizePx = if (isCompact) (80 * density).toInt() else 400

                        Glide.with(imageView.context)
                            .load(documentFile.uri)
                            .override(sizePx, sizePx)
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
                                        // ✅ Mettre à jour les zones une fois l'image chargée
                                        val zones = imageZonesMap[currentFullPath]?.filter { it.linkedImagePath != null } ?: emptyList()
                                        overlayView.visibility = if (zones.isNotEmpty()) View.VISIBLE else View.GONE
                                        overlayView.zones = zones
                                        overlayView.invalidate()
                                        return false // on laisse Glide continuer
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
                        onImageSelected(item.fullPath, ImageClickSource.SIDEBAR, false)
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

        // (removed setExpandedGroupPaths from here)


    }
    // --- Ajout : Méthode pour ouvrir/fermer tous les groupes ---
    fun toggleAllGroups(expand: Boolean) {
        if (expand) {
            rootGroups.forEach { collectGroupPaths(it, expandedGroups) }
        } else {
            expandedGroups.clear()
        }
        displayItems = flattenGroups(rootGroups)
        submitList(displayItems.toList())

        // 🔁 Forcer le rebind de tous les GroupItems pour mettre à jour les flèches
        displayItems.forEachIndexed { index, item ->
            if (item is DisplayItem.GroupItem) {
                notifyItemChanged(index)
            }
        }
    }

    private fun collectGroupPaths(group: ImageGroup, set: MutableSet<String>) {
        set.add(group.fullPath ?: group.name)
        for (child in group.children) {
            collectGroupPaths(child, set)
        }
    }

    // Set the expanded groups from an external set of paths
    fun setExpandedGroupPaths(paths: Set<String>) {
        expandedGroups.clear()
        expandedGroups.addAll(paths)
        displayItems = flattenGroups(rootGroups)
        submitList(displayItems.toList())
    }

    // --- Ajout des méthodes publiques pour showZones, showZoneThumbnails, layoutResIdModifiable ---
    fun setShowZones(value: Boolean) {
        showZones = value
        notifyDataSetChanged()
    }

    fun setShowZoneThumbnails(value: Boolean) {
        showZoneThumbnails = value
        notifyDataSetChanged()
    }

    fun setLayoutResId(newLayoutResId: Int) {
        layoutResIdModifiable = newLayoutResId
        notifyDataSetChanged()
    }
}
