package com.example.imagenavigator.screens

import java.util.concurrent.Semaphore

//import com.example.imagenavigator.BuildConfig

import androidx.recyclerview.widget.RecyclerView
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.imagenavigator.R
import com.example.imagenavigator.adapters.ImageAdapter
import com.example.imagenavigator.databinding.ActivityEditorBinding
import com.example.imagenavigator.model.AdventureData
import com.example.imagenavigator.model.Zone
import com.example.imagenavigator.utils.ImageGroup
import com.example.imagenavigator.utils.ImageGroupTreeBuilder
import com.example.imagenavigator.utils.ImageGroupNode
import com.google.gson.GsonBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.File
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.example.imagenavigator.model.Adventure
import com.example.imagenavigator.model.ImageData
import com.example.imagenavigator.model.toZone
import com.example.imagenavigator.model.toZoneData
import android.widget.ProgressBar
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.DataSource
import com.example.imagenavigator.utils.ThumbnailLoader

import androidx.lifecycle.lifecycleScope
import com.example.imagenavigator.model.ZoneData
import com.google.android.material.button.MaterialButton


enum class ImageClickSource {
    DRAWING_VIEW,
    SIDEBAR,
    INCOMING_LINKS,
    ZONE_THUMBNAIL,
    ZONE_LINKING
}


class EditorActivity : BaseActivity() {


    // --- Déclarations ---

    private lateinit var binding: ActivityEditorBinding
    private lateinit var imageAdapter: ImageAdapter

    var imageDataMap = mutableMapOf<String, MutableList<Zone>>()
    private lateinit var imageRootNode: ImageGroupNode
    private var currentImageName: String? = null

    // Map pour retrouver le DocumentFile correspondant à chaque image
    val imageFileMap = mutableMapOf<String, DocumentFile>()

    private lateinit var adventureNameTextView: TextView
    private var currentAdventureName: String = ""

    private lateinit var imagesInfoText: TextView
    private lateinit var worldsInfoText: TextView
    private lateinit var unlinkedInfoText: TextView

    private lateinit var deleteZonesButton: ImageButton

    private val imageLoadingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imagesPerBatch = 5
    private var totalImagesToLoad = 0
    private var loadedImagesCount = 0

    private var currentFolderUri: Uri? = null
    private lateinit var loadingProgressBar: ProgressBar

    private val debugLogs = true

    private var startImagePath: String? = null
    private var currentAdventureJsonUri: Uri? = null
    private var isBusy = false
    private var hasJustSaved = false
    private lateinit var incomingLinksAdapter: ImageAdapter

    private val prefs by lazy { getSharedPreferences("ImageNavigatorPrefs", Context.MODE_PRIVATE) }

    private fun scrollToCurrentImageThumbnail() {
        currentImageName?.let { imageName ->
            imageAdapter.scrollToThumbnail(imageName, binding.recyclerViewThumbnails)

            val index = imageAdapter.currentList.indexOfFirst { it.fullPath == imageName }
            if (index >= 0) {
                binding.recyclerViewThumbnails.postDelayed({
                    val viewHolder = binding.recyclerViewThumbnails.findViewHolderForAdapterPosition(index)
                    viewHolder?.itemView?.apply {
                        setBackgroundColor(ContextCompat.getColor(this@EditorActivity, R.color.highlight))
                        postDelayed({
                            setBackgroundColor(ContextCompat.getColor(this@EditorActivity, android.R.color.transparent))
                        }, 800)
                    }
                }, 500) // Laisse le temps au RecyclerView de se mettre à jour après l'expansion
            }
        }
    }

    private fun saveLastFolderUri(uri: Uri) {
        prefs.edit().putString("lastFolderUri", uri.toString()).apply()    }

    private fun getLastFolderUri(): Uri? {
        val uriString = prefs.getString("lastFolderUri", null)
        return uriString?.let { Uri.parse(it) }
    }


    private fun logDebug(tag: String, message: String) {
        // Paramètre tag inutilisé, log supprimé pour respecter consigne
        if (debugLogs) {
            Log.d(tag, message)
        }
    }


    fun updateImageDataMap(updatedZones: List<Zone>) {
        currentImageName?.let { imageName ->
            imageDataMap[imageName] = updatedZones.toMutableList()
        }
    }

    fun refreshThumbnailZones() {
        val imageZonesMap = getCurrentImageZonesMap()
        imageAdapter.imageZonesMap = imageZonesMap
        imageAdapter.notifyDataSetChanged()
    }

    // Demander l'accès au dossier
    private fun requestFolderAccess(uri: Uri, clearData: Boolean = false) {
        logDebug("FolderAccess", "Début requestFolderAccess(uri=$uri, clearData=$clearData)")
        /*if (uri == currentFolderUri && !clearData) {
            logDebug(
                "RequestFolderAccess",
                "Même dossier déjà chargé → on saute le reset et le reload"
            )
            return
        }*/

        if (!hasPersistedPermission(uri)) {
            showSnackbar("⚠ Attention : permission non persistante, tentative quand même.")
            logDebug("RequestFolderAccess", "Pas de permission persistante, mais on continue")
        }

        Glide.get(this).clearMemory()
        CoroutineScope(Dispatchers.IO).launch { Glide.get(this@EditorActivity).clearDiskCache() }


        if (clearData) {
            imageDataMap.clear()
            imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
            imageFileMap.clear()
        }

        isBusy = true
        currentFolderUri = uri
        // Log conservé uniquement si debugLogs, sinon supprimé
        if (debugLogs) Log.d("LoadImagesFromFolder", "Entrée !!!")
        loadImagesFromFolder(uri, clearData)
        // Bloc ajouté pour restaurer automatiquement l’image de départ après chargement
        startImagePath?.let { imagePath ->
            Handler(Looper.getMainLooper()).postDelayed({
                onImageSelected(imagePath, ImageClickSource.SIDEBAR, allowScroll = false)
            }, 500)
        }
        imageAdapter.imageZonesMap = getCurrentImageZonesMap()
        imageAdapter.notifyDataSetChanged()
    }

    private fun setStartImage(fullPath: String) {
        startImagePath = fullPath
        Toast.makeText(this, "Image de départ sélectionnée", Toast.LENGTH_SHORT).show()
        imageAdapter.startImagePath = fullPath
        imageAdapter.notifyDataSetChanged()
    }

    // Sélecteur de dossier
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    val flags =
                        result.data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (flags != null) {
                        contentResolver.takePersistableUriPermission(uri, flags)
                    }
                    saveLastFolderUri(uri)
                    currentFolderUri = uri
                    val adventureFromIntent = intent.getStringExtra("adventureName")
                    if (adventureFromIntent != null) {
                        enterEditMode(adventureFromIntent)
                    } else {
                        loadImagesFromFolder(uri, true)
                    }
                }
            }
        }

    // Quand une image est sélectionnée
    fun onImageSelected(
        fullPath: String,
        source: ImageClickSource,
        allowScroll: Boolean = false
    ) {
        logDebug("onImageSelected", "onImageSelected appelé !")
        val selectedZone = binding.drawingView.selectedZone
        if (selectedZone != null) {
            // Ne change pas l’image affichée — on ne fait que lier
            linkSelectedZoneToImage(fullPath)
            return
        }
        if (isBusy) {
            showSnackbar("Chargement en cours, patiente un instant…")
            return
        }

        currentImageName?.let { oldImageName ->
            imageDataMap[oldImageName] = binding.drawingView.getAllZones().toMutableList()
        }

        val incomingPaths = imageDataMap.filter { (_, zones) ->
            zones.any { it.linkedImagePath == fullPath }
        }.keys.toSet()

        val incomingItems = imageAdapter.currentList.filter { it.fullPath in incomingPaths }
        incomingLinksAdapter.highlightedPaths = incomingPaths
        incomingLinksAdapter.submitList(incomingItems)

        val maxRetries = 3
        var attempt = 0

        fun loadImageWithRetry() {
            attempt++
            Log.d("EditorActivity", "Chargement image (tentative $attempt/$maxRetries): $fullPath")

            // Clear Glide memory and disk cache before each attempt to avoid cache issues
            Glide.get(this@EditorActivity).clearMemory()
            CoroutineScope(Dispatchers.IO).launch {
                Glide.get(applicationContext).clearDiskCache()
            }

            val file = imageFileMap[fullPath]
            if (file == null) {
                Log.e("EditorActivity", "Image introuvable dans imageFileMap: $fullPath")
                showSnackbar("Image introuvable.")
                return
            }

            Glide.with(this@EditorActivity)
                .asBitmap()
                .load(file.uri)
                .listener(object : RequestListener<Bitmap> {
                    override fun onResourceReady(
                        resource: Bitmap,
                        model: Any,
                        target: Target<Bitmap>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d("EditorActivity", "Image chargée avec succès: $fullPath (tentative $attempt)")
                        currentImageName = fullPath
                        binding.drawingView.loadImage(resource)
                        binding.drawingView.clearLinkedThumbnails()
                        binding.drawingView.setZonesForCurrentImage(imageDataMap[fullPath] ?: emptyList())
                        binding.drawingView.reloadLinkedThumbnailsForCurrentImage()
                        Log.d(
                            "EditorActivity",
                            "Zones définies pour image: $fullPath, zones count: ${imageDataMap[fullPath]?.size ?: 0}"
                        )
                        Log.d("EditorActivity", "currentImageName mis à jour: $currentImageName")
                        Log.d(
                            "Glide",
                            "onResourceReady appelé pour: $fullPath, bitmap size: ${resource.width}x${resource.height}"
                        )
                        binding.drawingView.isBitmapReady = true
                        binding.drawingView.invalidate()
                        return false
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.drawingView.isBitmapReady = false
                        Log.e("Glide", "onLoadFailed pour $fullPath à tentative $attempt", e)
                        if (attempt < maxRetries) {
                            Log.w("Glide", "Nouvelle tentative de chargement ($attempt/$maxRetries) pour $fullPath")
                            Handler(Looper.getMainLooper()).post {
                                Snackbar.make(
                                    findViewById(android.R.id.content),
                                    "Erreur de chargement. Nouvelle tentative ($attempt/$maxRetries)...",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            Handler(Looper.getMainLooper()).postDelayed({
                                loadImageWithRetry()
                            }, 500)
                        } else {
                            Log.e(
                                "Glide",
                                "Echec critique: Impossible de charger l'image $fullPath après $attempt tentatives"
                            )
                            Handler(Looper.getMainLooper()).post {
                                Snackbar.make(
                                    findViewById(android.R.id.content),
                                    "Erreur critique au chargement de l'image après $attempt tentatives.",
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        }
                        return false
                    }
                })
                .submit()
        }

        loadImageWithRetry()

        imageAdapter.imageZonesMap = getCurrentImageZonesMap()
        val index = imageAdapter.currentList.indexOfFirst { it.fullPath == fullPath }
        if (index >= 0) {
            imageAdapter.notifyItemChanged(index)
        }
        if (allowScroll && binding.drawingView.selectedZone == null) {
            Log.d("SCROLL_TRACE", "Scroll autorisé depuis $source")
            scrollToCurrentImageThumbnail()
        } else {
            Log.d("SCROLL_TRACE", "Scroll bloqué depuis $source")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val incomingLinksRecycler = findViewById<RecyclerView>(R.id.incomingLinksRecycler)
        incomingLinksAdapter = ImageAdapter(
            rootGroups = emptyList(),
            onImageSelected = { fullPath, _ -> onImageSelected(fullPath, ImageClickSource.SIDEBAR, allowScroll = false) },
            onItemLongPress = {},
            imageFileMap = imageFileMap,
            layoutResId = R.layout.item_image_compact
        )
        incomingLinksRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        incomingLinksRecycler.adapter = incomingLinksAdapter


        val lastUri = getLastFolderUri()
        if (lastUri != null) {
            if (hasPersistedPermission(lastUri)) {
                logDebug("TestFlow", ">>> Permission OK au redémarrage")
            } else {
                logDebug("TestFlow", ">>> Permission PERDUE au redémarrage")
            }
        }
        currentFolderUri = lastUri
        // Log supprimé (restauration du dernier folder URI)


        if (binding.syncOverlay.visibility == View.VISIBLE) {
            Log.d("SyncFolder", "Synchronisation déjà en cours, on ignore l’appel.")
            return
        }

        // --- Initialisation du bouton Supprimer (deleteButton) ---
        // Suppression du bouton Supprimer (deleteButton) et de son listener

        deleteZonesButton = findViewById(R.id.deleteZonesButton)
        deleteZonesButton.setOnClickListener {
            if (debugLogs) Log.d("DeleteZones", "Suppression demandée via bouton")
            binding.drawingView.deleteSelectedZones()
            // Log utile pour traçabilité : suppression effective
            Log.d("DeleteZones", "deleteSelectedZones() appelé, zones supprimées.")
            currentImageName?.let { imageName ->
                imageDataMap[imageName] = binding.drawingView.getAllZones().toMutableList()
                // Log utile pour traçabilité : imageDataMap mis à jour
                Log.d("DeleteZones", "imageDataMap mis à jour pour $imageName : ${imageDataMap[imageName]?.size} zones")

                val imageZonesMap = getCurrentImageZonesMap()
                imageAdapter.imageZonesMap = imageZonesMap

                // Log utile pour traçabilité : updateImageZonesMapAndRefresh
                Log.d("DeleteZones", "Appel updateImageZonesMapAndRefresh...")
                imageAdapter.updateImageZonesMapAndRefresh(imageZonesMap)
            }
            deleteZonesButton.visibility = View.GONE
        }

        loadingProgressBar = findViewById(R.id.loadingOverlay)
        //loadingTextView = findViewById(R.id.LoadingText)

        // Adapter images
        imageAdapter = ImageAdapter(
            rootGroups = emptyList(),
            onImageSelected = { fullPath, _ -> onImageSelected(fullPath, ImageClickSource.SIDEBAR, allowScroll = false) },
            onItemLongPress = { item -> setStartImage(item.fullPath) },
            imageFileMap = imageFileMap
        )

        binding.recyclerViewThumbnails.apply {
            layoutManager = LinearLayoutManager(this@EditorActivity)
            setHasFixedSize(true)
            adapter = imageAdapter
        }
        // Désactive les animations de changement pour éviter le clignotement
        val animator = binding.recyclerViewThumbnails.itemAnimator
        if (animator is androidx.recyclerview.widget.SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }


        // 🛠 Accès propre aux éléments du header
        adventureNameTextView = binding.headerAdventure.adventureNameTextView


        // --- Initialisation centralisée de la bottom bar et de ses composants ---
        val bottomBarView = binding.bottomBar.root
        imagesInfoText = bottomBarView.findViewById(R.id.textImageCount)
        worldsInfoText = bottomBarView.findViewById(R.id.textWorldCount)
        unlinkedInfoText = bottomBarView.findViewById(R.id.textUnlinkedCount)
        val buttonSave = binding.bottomBar.buttonSave
        val buttonMenu = binding.bottomBar.buttonMenu
        val buttonStartAdventure = binding.bottomBar.buttonStartAdventure
        val buttonSyncFolder = binding.bottomBar.buttonSyncFolder
        val buttonExpandAll = binding.bottomBar.buttonExpandAll
        val buttonCollapseAll = binding.bottomBar.buttonCollapseAll

        val adventureFromIntent = intent.getStringExtra("adventureName")
        if (adventureFromIntent != null) {
            enterEditMode(adventureFromIntent)
            currentFolderUri?.let {
                if (!hasPersistedPermission(it)) {
                    showSnackbar("Permission expirée, merci de re-sélectionner le dossier.")
                    openFolderPicker()
                }
            }
            if (intent.getBooleanExtra("editMode", false)) {
                showSnackbar("Mode édition activé pour $adventureFromIntent")
            }
        } else {
            promptAdventureName()
        }

        // Ajout : gestion de l'image à afficher automatiquement depuis l'intent ("imagePath")
        val imageFromIntent = intent.getStringExtra("imagePath")
        if (imageFromIntent != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                onImageSelected(imageFromIntent, ImageClickSource.SIDEBAR, allowScroll = false)
            }, 1000)
        }

        // 📂 Boutons pour ouvrir/fermer tous les groupes dans la sidebar
        buttonExpandAll.setOnClickListener {
            imageAdapter.toggleAllGroups(true)
        }
        buttonCollapseAll.setOnClickListener {
            imageAdapter.toggleAllGroups(false)
        }
        // Listeners sur les boutons de la bottom bar
        buttonSave.setOnClickListener {
            Log.d("EDITOR", "Bouton sauvegarder cliqué")
            Toast.makeText(this, "Sauvegarde cliquée", Toast.LENGTH_SHORT).show()
            saveZones()
            hasJustSaved = true
        }

        // DrawingView cliquable
        binding.drawingView.isClickable = true
        binding.drawingView.onTapListener = {
            // Ne scroll que si aucune zone n'est sélectionnée
            if (binding.drawingView.selectedZone == null && currentImageName != null) {
                onImageSelected(currentImageName!!, ImageClickSource.DRAWING_VIEW, allowScroll = true)
            }
        }

        binding.drawingView.onZoneSelected = {
            updateDeleteButtonVisibilityForZones()
            refreshThumbnailZones()
            // Ne pas scroller lorsqu'une zone est sélectionnée
            // (le scroll est déjà bloqué côté DrawingView via onTouchEvent)
            binding.drawingView.clearFocus()
        }

        // Ajout d'une zone nouvellement créée à imageDataMap pour l'image courante
        binding.drawingView.onZoneCreated = { zone ->
            currentImageName?.let { imageName ->
                imageDataMap[imageName]?.add(zone)

                val imageZonesMap = getCurrentImageZonesMap()
                imageAdapter.imageZonesMap = imageZonesMap
                imageAdapter.updateImageZonesMapAndRefresh(imageZonesMap)
                updateWorldAndUnlinkedCounts()

                val index = imageAdapter.currentList.indexOfFirst { it.fullPath == imageName }
                if (index >= 0) {
                    val vh = binding.recyclerViewThumbnails.findViewHolderForAdapterPosition(index)
                    val updatedZones = imageZonesMap[imageName] ?: emptyList()
                    if (::imageAdapter.isInitialized && vh is ImageAdapter.ImageViewHolder) {
                        vh.overlayView.zones = updatedZones
                        vh.overlayView.invalidate()
                // Log supprimé : overlayView zones update (trop verbeux)
                    }
                }
            }
        }

        // Ajout des boutons Menu et StartAdventure
        buttonMenu.setOnClickListener {
            saveZones()
            showSnackbar("Aventure sauvegardée")
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }, 500)
        }
        buttonStartAdventure.setOnClickListener {
            saveZones()
            showSnackbar("Aventure sauvegardée")
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, NavigatorActivity::class.java)
                val adventureData = generateAdventureData()
                val folderUri = adventureData.folderUri
                val file = File(filesDir, "${currentAdventureName}_zones.json")
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                intent.putExtra("adventureJsonUri", fileUri)
                intent.putExtra("folderUri", folderUri?.toString())
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
                finish()
            }, 500)
        }
        // Ajout Bouton de synchronisation
        buttonSyncFolder.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                synchronizeFolder()
            }
        }
    }

// --- FONCTIONS UTILITAIRES ---
    // --- Centralisation de la gestion de visibilité des boutons de la bottom bar ---
    private fun setBottomBarButtonsVisible(visible: Boolean) {
        binding.bottomBar.buttonCollapseAll.visibility = if (visible) View.VISIBLE else View.GONE
        binding.bottomBar.buttonExpandAll.visibility = if (visible) View.VISIBLE else View.GONE
        binding.bottomBar.buttonSave.visibility = if (visible) View.VISIBLE else View.GONE
        binding.bottomBar.buttonMenu.visibility = if (visible) View.VISIBLE else View.GONE
        binding.bottomBar.buttonStartAdventure.visibility = if (visible) View.VISIBLE else View.GONE
        binding.bottomBar.buttonSyncFolder.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerLauncher.launch(intent)
    }

    private fun promptAdventureName() {
        currentFolderUri = null
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nouvelle aventure")

        val input = EditText(this)
        input.hint = "Nom de l'aventure"
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.requestFocus()

        builder.setView(input)
        builder.setCancelable(false)

        builder.setPositiveButton("Valider") { _, _ ->
            var name = input.text.toString().trim().replace("\n", "")
            if (name.isEmpty()) {
                name = generateUniqueAdventureName()
            }

            if (adventureFileExists(name)) {
                Toast.makeText(this, "Ce nom existe déjà. Essayez-en un autre.", Toast.LENGTH_SHORT).show()
            } else {
                currentAdventureName = name
                adventureNameTextView.text = currentAdventureName
                currentAdventureJsonUri = Uri.fromFile(File(filesDir, "$currentAdventureName.json"))
                openFolderPicker()
            }
        }

        builder.setNegativeButton("Annuler") { _, _ ->
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                var name = input.text.toString().trim().replace("\n", "")
                if (name.isEmpty()) {
                    name = generateUniqueAdventureName()
                }

                if (adventureFileExists(name)) {
                    Toast.makeText(this, "Ce nom existe déjà. Essayez-en un autre.", Toast.LENGTH_SHORT).show()
                } else {
                    currentAdventureName = name
                    adventureNameTextView.text = currentAdventureName
                    currentAdventureJsonUri = Uri.fromFile(File(filesDir, "$currentAdventureName.json"))
                    openFolderPicker()
                    dialog.dismiss()
                }
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    private fun adventureFileExists(name: String): Boolean {
        val file = File(filesDir, "${name}_zones.json")
        return file.exists()
    }

    private fun generateUniqueAdventureName(baseName: String = "Aventure"): String {
        var name = baseName
        var index = 2
        while (adventureFileExists(name)) {
            name = "$baseName $index"
            index++
        }
        return name
    }

    private fun saveZones() {
        val file = File(filesDir, "${currentAdventureName}_zones.json")
        logDebug("SaveZones", "Enregistrement de l’aventure : $currentAdventureName → ${file.absolutePath}")
        if (imageDataMap.isEmpty()) {
            Log.w("SaveZones", "Aucune image à sauvegarder → opération annulée.")
            Snackbar.make(
                findViewById(android.R.id.content),
                "Rien à sauvegarder (aucune image chargée).",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        if (currentAdventureName.isEmpty()) {
            currentAdventureName = "AventureSansNom"
        }
        val adventureData = generateAdventureData()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(adventureData)
        file.writeText(json)

        Log.d("SaveZones", "Aventure sauvegardée sous ${file.absolutePath}")
        Snackbar.make(
            findViewById(android.R.id.content),
            "Aventure sauvegardée : $currentAdventureName",
            Snackbar.LENGTH_SHORT
        ).show()
        hasJustSaved = true
    }


    private fun updateBottomBarInfo(isLoading: Boolean = false) {
        if (!::imagesInfoText.isInitialized) return
            imagesInfoText.visibility = View.VISIBLE
            worldsInfoText.visibility = View.VISIBLE
            unlinkedInfoText.visibility = View.VISIBLE
            imagesInfoText.text = getString(R.string.images_count, imageDataMap.size)
            imagesInfoText.textSize = 18f
            updateWorldAndUnlinkedCounts()
    }

    private fun updateWorldAndUnlinkedCounts() {
        if (!::imageRootNode.isInitialized) {
            Log.w("EditorActivity", "imageRootNode non initialisé → on saute updateWorldAndUnlinkedCounts()")
            return
        }
        val worldCount = imageRootNode.children.count { it.name != "Racine" }
        findViewById<TextView>(R.id.textWorldCount).text = getString(R.string.worlds_count, worldCount)

        // Comptage des images sans aucune zone liée à une image cible
        val imagesWithoutZonesCount = imageDataMap.count { zones ->
            zones.value.none { it.linkedImagePath != null }
        }

        // Comptage des images non ciblées (jamais utilisées comme linkedImagePath)
        val linkedImagePaths = imageDataMap
            .flatMap { it.value }
            .mapNotNull { it.linkedImagePath }
            .toSet()
        val imagesNotTargetsCount = imageFileMap.keys.count { it !in linkedImagePaths }

        // Affichage dans un seul champ, plus informatif
        findViewById<TextView>(R.id.textUnlinkedCount).text =
            "🟦 Sans zone : $imagesWithoutZonesCount   🟥 Non ciblées : $imagesNotTargetsCount"
    }

    private fun isGroupPath(fullPath: String): Boolean {
        fun findNode(node: ImageGroupNode): Boolean {
            if (node.fullPath == fullPath) return true
            return node.children.any { findNode(it) }
        }
        return findNode(imageRootNode)
    }

    private fun loadImagesFromFolder(uri: Uri, clearData: Boolean = true) {
        logDebug("LoadImages", "Début loadImagesFromFolder(uri=$uri, clearData=$clearData)")
        isBusy = true
        if (debugLogs) Log.d("EditorActivity", "Loading images from folder: $uri")

        if (clearData) {
            imageDataMap.clear()
            imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
        }

        val skippedFiles = mutableListOf<String>()
        imageLoadingScope.launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val allImageFiles = mutableListOf<Pair<DocumentFile, String>>()
            val seenPaths = mutableSetOf<String>()

            withContext(Dispatchers.Main) {
                if (!::imagesInfoText.isInitialized) {
                    Log.w("EditorActivity", "imagesInfoText non initialisé, on saute la mise à jour UI.")
                } else {
                    loadingProgressBar.visibility = View.VISIBLE
                    imagesInfoText.text = getString(R.string.loading_progress)
                    imagesInfoText.textSize = 18f
                }
                loadingProgressBar.visibility = View.VISIBLE
                imagesInfoText.text = getString(R.string.loading_progress)
                imagesInfoText.textSize = 22f
                // Faire disparaître textWorldCount pendant le chargement
                if (::worldsInfoText.isInitialized) {
                    worldsInfoText.visibility = View.GONE}
                    if (::unlinkedInfoText.isInitialized) {
                        unlinkedInfoText.visibility = View.GONE}
                // Masquer tous les boutons de la bottom bar de façon centralisée
                setBottomBarButtonsVisible(false)
            }

            // Ajout: clear les listes globales si clearData demandé (une seule fois au début)
            if (clearData) {
                allImageFiles.clear()
                seenPaths.clear()
                imageFileMap.clear()
            }

            fun traverse(file: DocumentFile, path: String = "") {
                // Log supprimé : nombre total d’images trouvées (trop verbeux)
                if (file.isDirectory) {
                    val newPath = if (path.isEmpty()) file.name ?: "" else "$path/${file.name}"
                    file.listFiles()?.forEach { traverse(it, newPath) }
                } else {
                    val name = file.name ?: return
                    val fullPath = if (path.isEmpty()) name else "$path/$name"
                    if (isValidImage(file)) {
                        if (seenPaths.add(fullPath)) {
                            allImageFiles.add(file to fullPath)
                            imageFileMap[fullPath] = file
                            if (!imageDataMap.containsKey(fullPath)) {
                                imageDataMap[fullPath] = mutableListOf()
                            }
                        } else {
                            Log.v("EditorActivity", "Doublon ignoré: $fullPath")
                        }
                    }
                }
            }


            folder.listFiles()?.forEach { traverse(it) }
            // logDebug supprimé sauf pour hasPersistedPermission (voir plus bas)

            // logDebug supprimé (liste finale allImageFiles)
            allImageFiles.sortWith(compareBy({ it.second.count { c -> c == '/' } }, { it.second }))

            // Dédoublonnage de la liste avant le traitement en batch
            val dedupedImageFiles = allImageFiles.distinctBy { it.second }
            totalImagesToLoad = dedupedImageFiles.size
            loadedImagesCount = 0

            val semaphore = Semaphore(5)
            val batches = dedupedImageFiles.chunked(imagesPerBatch)

            for (batch in batches) {
                logDebug("LoadImages", "Début batch de ${batch.size} images")
                val deferreds = batch.map { (file, fullPath) ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        if (imageFileMap.containsKey(fullPath)) {
                            // Log supprimé : image déjà en cache
                            semaphore.release()
                            return@async
                        }
                        try {
                            if (!imageFileMap.containsKey(fullPath)) {
                                imageFileMap[fullPath] = file
                            }
                            withContext(Dispatchers.Main) {
                                if (!imageAdapter.currentList.any { it.fullPath == fullPath }) {
                                    imageAdapter.addImage(fullPath)
                                }
                            }
                            // Log supprimé : image trouvée (répétitif, trop verbeux)
                            loadedImagesCount = minOf(loadedImagesCount + 1, totalImagesToLoad)
                            // logDebug supprimé : progression toutes les 10 images
                        } catch (e: Exception) {
                            imageDataMap[fullPath] = mutableListOf()
                            skippedFiles.add(fullPath)
                            Log.e(
                                "EditorActivity",
                                "Failed to load image: $fullPath",
                                e
                            )
                            loadedImagesCount++ // Même si échec, on incrémente pour la barre de chargement
                        } finally {
                            semaphore.release()
                        }
                    }
                }
                deferreds.awaitAll()
                withContext(Dispatchers.Main) {
                    updateLoadingProgress()
                }
            }

            // UI update block: only once after all batches
            withContext(Dispatchers.Main) {
                imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(
                    imageDataMap.keys.toList()
                )
                // Ajout explicite du groupe Racine en haut de la liste
                val imageGroups = mutableListOf<ImageGroup>()
                val racineGroup = ImageGroup(
                    name = "Racine",
                    images = imageRootNode.images,
                    children = listOf(),
                    fullPath = null
                )
                imageGroups.add(racineGroup)
                imageGroups.addAll(ImageGroup.fromTree(imageRootNode))
                imageAdapter.updateData(imageGroups)
                imageAdapter.notifyDataSetChanged()
                updateLoadingProgress()
                loadingProgressBar.visibility = View.GONE
                setBottomBarButtonsVisible(true)
                //loadingTextView.visibility = View.GONE
                if (skippedFiles.isNotEmpty()) {
                    Toast.makeText(
                        this@EditorActivity,
                        "Certaines images n'ont pas été chargées.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateBottomBarInfo(isLoading = false)
                binding.bottomBar.buttonSave.isEnabled = true
                // logDebug supprimé : chargement terminé
                isBusy = false
            }
        }
        // logDebug supprimé : chargement des images terminé
    }
    // SUPPRIMÉE : fonction non utilisée


    private fun updateLoadingProgress() {
        try {
            if (!::imagesInfoText.isInitialized) return
            if (totalImagesToLoad > 0) {
                imagesInfoText.text = getString(R.string.loading_progress)
            }
            binding.bottomBar.buttonSave.isEnabled = true
        } catch (e: Exception) {
            Log.e("EditorActivity", "Erreur UI update: ${e.message}")
        }
        // Log supprimé : progression loading (trop verbeux)
    }

    private fun isValidImage(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val validExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        val ext = name.substringAfterLast('.', "").lowercase()
        val mimeType = contentResolver.getType(file.uri)
        return (mimeType?.startsWith("image/") == true) && ext in validExtensions
    }


    private fun generateAdventureData(): Adventure {
        val imagesList = imageDataMap.map { (path, zones) ->
            ImageData(
                imageName = path,
                zones = zones.map { it.toZoneData() }
            )
        }
        return Adventure(
            adventureTitle = currentAdventureName,
            folderUri = currentFolderUri?.toString() ?: "",
            images = imagesList,
            startImagePath = startImagePath
        )
    }

    private fun countTotalGroups(node: ImageGroupNode): Int {
        var count = 0
        for (child in node.children) {
            if (child.images.isNotEmpty() || child.children.isNotEmpty()) {
                count += 1
                count += countTotalGroups(child)
            }
        }
        return count
    }


    override fun onPause() {
        super.onPause()
        if (!hasJustSaved) {
            logDebug("SaveZones", "Auto-save déclenché dans onPause/onDestroy")
            saveZones()
            hasJustSaved = true
        }
    }

    override fun onDestroy() {
        if (!hasJustSaved) {
            saveZones()
            hasJustSaved = true
        }
        imageLoadingScope.cancel()
        Glide.get(this).clearMemory()
        lifecycleScope.launch(Dispatchers.IO) { Glide.get(applicationContext).clearDiskCache() }
        super.onDestroy()
    }

    // Permet à DrawingView de masquer le bouton de suppression des zones
    fun hideDeleteZonesButton() {
        deleteZonesButton.visibility = View.GONE
        logDebug("EditorActivity", "hideDeleteZonesButton() appelé → on cache le bouton")
    }


    // --- Synchronisation du dossier ---
    private suspend fun synchronizeFolder() {
        val uri = currentFolderUri
        // logDebug supprimé : début synchronizeFolder
        // --- Déclarations globales pour la fonction ---
        lateinit var newGroupImages: MutableMap<String, MutableList<String>>
        lateinit var previousGroupImages: MutableMap<String, MutableList<String>>
        isBusy = true
        if (uri == null) {
            withContext(Dispatchers.Main) {
            showSnackbar("Aucun dossier à synchroniser.")
            }
            return
        }

        // --- Calculer la taille totale du dossier ---
        var totalSize = 0L
        val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri)
        if (folder != null) {
            fun accumulateSize(file: DocumentFile) {
                if (file.isDirectory) {
                    file.listFiles()?.forEach { accumulateSize(it) }
                } else if (file.isFile) {
                    totalSize += file.length()
                }
            }
            folder.listFiles()?.forEach { accumulateSize(it) }
        }
        if (totalSize > 500_000_000L) { // 500Mo
            withContext(Dispatchers.Main) {
                showSnackbar("⚠ Attention : ce dossier dépasse 500Mo, risque de ralentissements ou plantage.")
            }
        }

        var detailedSummary = ""
        var addedGroups = emptySet<String>()
        var removedGroups = emptySet<String>()
        var addedImages = emptySet<String>()
        var removedImages = emptySet<String>()
        val removedLinkedImages = mutableListOf<String>()

        withContext(Dispatchers.Main) {
            binding.syncOverlay.visibility = View.VISIBLE
            Toast.makeText(this@EditorActivity, "Synchro en cours !...", Toast.LENGTH_SHORT).show()
        }

        withContext(Dispatchers.IO) {
            val previousImagePaths = imageFileMap.keys.toSet()

            previousGroupImages = mutableMapOf()
            for (path in previousImagePaths) {
                val segments = path.split("/")
                if (segments.isNotEmpty() && segments[0] != "Racine") {
                    val group = segments[0]
                    previousGroupImages.getOrPut(group) { mutableListOf() }.add(path)
                }
            }

            val newImagePaths = mutableSetOf<String>()
            val imageFiles = mutableMapOf<String, DocumentFile>()

            if (folder == null || !folder.exists()) {
                withContext(Dispatchers.Main) {
                    showSnackbar("Le dossier n'existe plus.")
                }
                return@withContext
            }

            fun traverse(file: DocumentFile, path: String = "") {
                if (file.isDirectory) {
                    file.listFiles()?.forEach { traverse(it, if (path.isEmpty()) file.name!! else "$path/${file.name}") }
                } else {
                    val name = file.name ?: return
                    val fullPath = if (path.isEmpty()) name else "$path/$name"
                    if (isValidImage(file)) {
                        newImagePaths.add(fullPath)
                        imageFiles[fullPath] = file
                    }
                }
            }

            folder.listFiles()?.forEach { traverse(it) }

            newGroupImages = mutableMapOf()
            for (path in newImagePaths) {
                val segments = path.split("/")
                if (segments.isNotEmpty() && segments[0] != "Racine") {
                    val group = segments[0]
                    newGroupImages.getOrPut(group) { mutableListOf() }.add(path)
                }
            }

            val filteredNewGroups = newGroupImages.filter { (_, images) ->
                images.size > 1 || images.any { !it.substringAfterLast('/').equals(it, ignoreCase = true) }
            }
            addedGroups = filteredNewGroups.keys.filter { it !in previousGroupImages.keys }.toSet()

            val filteredPreviousGroups = previousGroupImages.filter { (_, images) ->
                images.size > 1 || images.any { !it.substringAfterLast('/').equals(it, ignoreCase = true) }
            }
            removedGroups = filteredPreviousGroups.keys.filter { it !in newGroupImages.keys }.toSet()

            addedImages = newImagePaths - previousImagePaths
            removedImages = previousImagePaths - newImagePaths

            for (path in removedImages) {
                imageDataMap.remove(path)
            }

            for ((_, zones) in imageDataMap) {
                zones.removeAll { it.linkedImagePath in removedImages }
            }

            val linkedImagePaths = imageDataMap.flatMap { it.value }.mapNotNull { it.linkedImagePath }.toSet()
            removedLinkedImages.clear()
            removedLinkedImages.addAll(removedImages.filter { it in linkedImagePaths })


            for (path in addedImages) {
                val file = imageFiles[path]
                if (file != null && isValidImage(file)) {
                    try {
                        imageDataMap[path] = mutableListOf()
                    } catch (e: Exception) {
                        // logDebug supprimé : erreur chargement image lors de la synchro
                    }
                }
            }

            val bitmapPathPairs = imageDataMap.map { (path, _) -> Pair(null, path) }
            val paths = imageDataMap.keys.toList()
            imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(paths)
            // logDebug supprimé : zones count après synchro
        }

        addedGroups = addedGroups.distinct().toSet()
        removedGroups = removedGroups.distinct().toSet()
        addedImages = addedImages.distinct().toSet()
        removedImages = removedImages.distinct().toSet()
        logSyncSummary(addedGroups, removedGroups, addedImages, removedImages)


        withContext(Dispatchers.Main) {
            val imageGroups = ImageGroup.fromTree(imageRootNode)
            imageAdapter.updateData(imageGroups)
            binding.recyclerViewThumbnails.adapter = imageAdapter
            // Suppression de l'utilisation de imageBitmapMap ici
            imageAdapter.notifyDataSetChanged()
            updateBottomBarInfo()

            // Ajout calcul linkedImages et orphanImages AVANT detailedSummary
            val linkedImages = imageDataMap.flatMap { it.value }.mapNotNull { it.linkedImagePath }.toSet()
            val orphanImages = emptyList<String>() // imageBitmapMap supprimé

            detailedSummary = buildString {
                if (addedGroups.isNotEmpty()) {
                    append("📁 Dossiers ajoutés (${addedGroups.size}):\n")
                    addedGroups.forEach { append("   - $it (${newGroupImages[it]?.size ?: 0} images)\n") }
                }
                if (removedGroups.isNotEmpty()) {
                    append("📁 Dossiers supprimés (${removedGroups.size}):\n")
                    removedGroups.forEach { append("   - $it (${previousGroupImages[it]?.size ?: 0} images)\n") }
                }
                if (addedImages.isNotEmpty()) {
                    append("🖼️ Images ajoutées (${addedImages.size}):\n")
                    addedImages.forEach { append("   - $it\n") }
                }
                if (removedImages.isNotEmpty()) {
                    append("🖼️ Images supprimées (${removedImages.size}):\n")
                    removedImages.forEach { append("   - $it\n") }
                }
                if (removedLinkedImages.isNotEmpty()) {
                    append("⚠️ Zones cassées (${removedLinkedImages.size}):\n")
                    removedLinkedImages.forEach { append("   - $it\n") }
                }
                if (orphanImages.isNotEmpty()) {
                    append("⚠️ Images orphelines (${orphanImages.size}):\n")
                    orphanImages.forEach { append("   - $it\n") }
                }
                if (isEmpty()) append("✅ Dossier à jour, aucun changement.")
            }

            AlertDialog.Builder(this@EditorActivity)
                .setTitle("Résumé de la synchronisation")
                .setMessage(detailedSummary)
                .setPositiveButton("Fermer", null)
                .show()
            binding.syncOverlay.visibility = View.GONE
            isBusy = false
        }
    }

    private fun logSyncSummary(
        addedGroups: Set<String>,
        removedGroups: Set<String>,
        addedImages: Set<String>,
        removedImages: Set<String>
    ) {
        val message = "Résumé synchronisation : addedGroups=$addedGroups, removedGroups=$removedGroups, addedImages=$addedImages, removedImages=$removedImages"
        // logDebug supprimé : résumé synchronisation
    }

    // Lie l'image à la zone sélectionnée
    private fun linkSelectedZoneToImage(linkedImagePath: String) {
        val selectedZone = binding.drawingView.selectedZone
        if (selectedZone != null) {
            if (linkedImagePath == currentImageName) {
                showSnackbar("Impossible de lier une zone à sa propre image.")
                return
            }
            if (!imageDataMap.containsKey(linkedImagePath)) {
                showSnackbar("Impossible de lier à une image inconnue.")
                return
            }
            selectedZone.linkedImagePath = linkedImagePath
            // Ajout du log après mise à jour de selectedZone.linkedImagePath
            Log.d("LinkZone", "selectedZone.linkedImagePath mis à jour : ${selectedZone.linkedImagePath}")
            binding.drawingView.selectedZone = null
            binding.drawingView.invalidate()
            hideDeleteZonesButton()
            currentImageName?.let { imageName ->
                imageDataMap[imageName] = binding.drawingView.getAllZones().toMutableList()
                // Ajout du log après mise à jour de imageDataMap[imageName]
                Log.d("LinkZone", "imageDataMap[$imageName] → ${imageDataMap[imageName]?.size} zones")
                imageDataMap[imageName]?.forEachIndexed { index, zone ->
                    Log.d("LinkZone", "  zone[$index] = ${zone.rect}, linkedImagePath = ${zone.linkedImagePath}")
                }
                val imageZonesMap = getCurrentImageZonesMap()
                imageAdapter.imageZonesMap = imageZonesMap
                imageAdapter.updateImageZonesMapAndRefresh(imageZonesMap)
                updateWorldAndUnlinkedCounts()
                // Bloc supprimé : notification d'item changé qui pouvait provoquer un scroll
                // val index = imageAdapter.currentList.indexOfFirst { it.fullPath == linkedImagePath }
                // if (index >= 0) {
                //     imageAdapter.notifyItemChanged(index)
                // }
            }
            logDebug("LinkZone", "Zone liée: ${selectedZone.rect}, image: $linkedImagePath")
            logDebug("LinkZone", "ImageDataMap après liaison: $imageDataMap")
        }
        // Load the thumbnail only if the zone is now successfully linked
        if (selectedZone?.linkedImagePath != null) {
            imageFileMap[selectedZone.linkedImagePath!!]?.uri?.let { uri ->
                ThumbnailLoader.load(this, uri) { bitmap, _->
                    binding.drawingView.setLinkedThumbnailBitmap(selectedZone.toZoneData(), bitmap)
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    fun enterEditMode(adventureName: String) {
        // logDebug supprimé : entrée dans le mode édition
        currentAdventureName = adventureName
        // logDebug supprimé : currentAdventureName défini
        currentAdventureJsonUri = Uri.fromFile(File(filesDir, "$currentAdventureName.json"))
        // logDebug supprimé : currentAdventureJsonUri défini
        val file = File(filesDir, "${adventureName}_zones.json")
        // logDebug supprimé : vérification du fichier
        if (file.exists()) {
            val json = file.readText()
            // logDebug supprimé : parsing JSON
            val adventureData = GsonBuilder().create().fromJson(json, AdventureData::class.java)
            // Restaurer correctement startImagePath juste après parsing JSON
            startImagePath = adventureData.startImagePath
            // logDebug supprimé : startImagePath défini
            val folderUriString = adventureData.folderUri
            // Patch: check folderUriString starts with content://
            currentFolderUri = if (!folderUriString.isNullOrEmpty() && folderUriString.startsWith("content://")) Uri.parse(folderUriString) else null

            // Ajout du test pour éviter de demander à l'utilisateur de resélectionner si déjà autorisé
            // logDebug supprimé : check persisted permissions
            if (currentFolderUri != null && hasPersistedPermission(currentFolderUri!!)) {
                adventureNameTextView.text = adventureData.adventureTitle
                imageAdapter.startImagePath = startImagePath
                // Chargement des zones pour chaque image
                imageDataMap.clear()
                adventureData.images.forEach { image ->
                    val zones = image.zones.map { it.toZone() }.toMutableList()
                    imageDataMap[image.imageName] = zones
                }
                // Fix: always initialize imageRootNode to a valid node, not a String
                imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
                imageAdapter.updateData(emptyList())
                requestFolderAccess(currentFolderUri!!, clearData = false)
            } else {
                openFolderPicker()
                return
            }

            // Log supprimé : folderUri récupéré, mode édition prêt

        } else {
            currentFolderUri = null
            showSnackbar("Aventure introuvable, création d’une nouvelle.")
            promptAdventureName()
        }
    }


    // Modifiée : accepte read OU write (plus souple)
    private fun hasPersistedPermission(uri: Uri): Boolean {
        val persistedUris = contentResolver.persistedUriPermissions
        val hasPermission = persistedUris.any { it.uri == uri && (it.isReadPermission || it.isWritePermission) }
        if (debugLogs) Log.d("AppDebug", "hasPersistedPermission: $hasPermission pour $uri")
        return hasPermission
    }

    // Affiche ou masque le bouton de suppression des zones selon la sélection
    fun updateDeleteButtonVisibilityForZones() {
        deleteZonesButton.visibility =
            if (binding.drawingView.selectedZone != null) View.VISIBLE else View.GONE
    }

    private fun getCurrentImageZonesMap(): Map<String, List<ZoneData>> =
        imageDataMap.mapValues { entry -> entry.value.map { it.toZoneData() } }

}

