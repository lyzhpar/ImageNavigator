package com.example.imagenavigator.screens

import java.util.concurrent.Semaphore

//import com.example.imagenavigator.BuildConfig

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
import android.widget.Button
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
import android.graphics.drawable.Drawable
import com.example.imagenavigator.utils.ThumbnailLoader

import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton


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

    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false

    private lateinit var imagesInfoText: TextView
    //private lateinit var loadingTextView: TextView
    private lateinit var worldsInfoText: TextView
    private lateinit var selectedImagesCount: TextView
    private lateinit var selectedWorldsCount: TextView
    private lateinit var selectionInfoContainer: View

    private lateinit var deleteZonesButton: ImageButton

    private val imageLoadingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imagesPerBatch = 5
    private var totalImagesToLoad = 0
    private var loadedImagesCount = 0

    private var currentFolderUri: Uri? = null
    private lateinit var loadingProgressBar: ProgressBar

    private val debugLogs = true

    private var startImagePath: String? = null
    private var adventure: Adventure? = null
    private var currentAdventureJsonUri: Uri? = null
    private var isBusy = false
    private var hasJustSaved = false
    private var areGroupsExpanded = false

    private val prefs by lazy { getSharedPreferences("ImageNavigatorPrefs", Context.MODE_PRIVATE) }


    private fun scrollToCurrentImageThumbnail() {
        currentImageName?.let { imageName ->
            imageAdapter.scrollToThumbnail(imageName, binding.recyclerViewThumbnails)

            val index = imageAdapter.currentList.indexOfFirst { it.fullPath == imageName }
            if (index >= 0) {
                binding.recyclerViewThumbnails.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                        if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                            recyclerView.removeOnScrollListener(this)
                            val viewHolder = recyclerView.findViewHolderForAdapterPosition(index)
                            viewHolder?.itemView?.apply {
                                setBackgroundColor(ContextCompat.getColor(this@EditorActivity, R.color.highlight))
                                Handler(Looper.getMainLooper()).postDelayed({
                                    setBackgroundColor(ContextCompat.getColor(this@EditorActivity, android.R.color.transparent))
                                }, 1000)
                            }
                        }
                    }
                })
            }
        }
    }

    private fun saveLastFolderUri(uri: Uri) {
        prefs.edit().putString("lastFolderUri", uri.toString()).apply()
    }

    private fun getLastFolderUri(): Uri? {
        val uriString = prefs.getString("lastFolderUri", null)
        return if (uriString != null) Uri.parse(uriString) else null
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
        imageAdapter.imageZonesMap = imageDataMap.mapValues { it.value.map { it.toZoneData() } }
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
        Log.d("EditorActivity", "Caches Glide mémoire nettoyés")
        CoroutineScope(Dispatchers.IO).launch { Glide.get(this@EditorActivity).clearDiskCache() }
        Log.d("EditorActivity", "Caches Glide disque nettoyés")


        if (clearData) {
            imageDataMap.clear()
            imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
            imageFileMap.clear()
        }

        isBusy = true
        currentFolderUri = uri
        Log.d("LoadImagesFromFolder", "Entrée !!!")
        loadImagesFromFolder(uri, clearData)
        // Bloc ajouté pour restaurer automatiquement l’image de départ après chargement
        startImagePath?.let { imagePath ->
            Handler(Looper.getMainLooper()).postDelayed({
                onImageSelected(imagePath)
            }, 500)
        }
        imageAdapter.imageZonesMap = imageDataMap.mapValues { it.value.map { it.toZoneData() } }
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
    private fun onImageSelected(fullPath: String, scrollToThumbnail: Boolean = true) {
        logDebug("onImageSelected", "onImageSelected appelé !")
        if (isBusy) {
            showSnackbar("Chargement en cours, patiente un instant…")
            return
        }

        currentImageName?.let { oldImageName ->
            imageDataMap[oldImageName] = binding.drawingView.getAllZones().toMutableList()
        }

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

        val layoutManager = binding.recyclerViewThumbnails.layoutManager as LinearLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val offset = layoutManager.findViewByPosition(firstVisiblePosition)?.top ?: 0

        imageAdapter.imageZonesMap =
            imageDataMap.mapValues { it.value.map { zone -> zone.toZoneData() } }
        val index = imageAdapter.currentList.indexOfFirst { it.fullPath == fullPath }
        if (index >= 0) {
            imageAdapter.notifyItemChanged(index)
        }
        layoutManager.scrollToPositionWithOffset(firstVisiblePosition, offset)
        if (scrollToThumbnail) {
            scrollToCurrentImageThumbnail()}
    }


    private fun getUriForImage(path: String): Uri? {
        val adventureFolder = File(
            filesDir,
            "adventures"
        ) // Assurez-vous que ce dossier existe et contient les images
        val imageFile = File(adventureFolder, path)
        return if (imageFile.exists()) {
            Uri.fromFile(imageFile)
        } else {
            null
        }
    }


    private fun loadAdventureData(name: String) {
        val file = File(filesDir, "${name}_zones.json")
        if (file.exists()) {
            val json = file.readText()
            val adventureData = GsonBuilder().create().fromJson(json, AdventureData::class.java)

            imageDataMap.clear()
            adventureData.images.forEach { image ->
                val zones = image.zones.map { it.toZone() }.toMutableList()
                imageDataMap[image.imageName] = zones
            }

            startImagePath = adventureData.startImagePath
            currentAdventureName = adventureData.adventureTitle
            adventureNameTextView.text = currentAdventureName

            val folderUriString = adventureData.folderUri
            if (folderUriString.isNullOrEmpty()) {
                showSnackbar("Le dossier initial est manquant. Merci de le re-sélectionner.")
                openFolderPicker()
                return
            }
            currentFolderUri = Uri.parse(folderUriString)

            currentFolderUri?.let {
                requestFolderAccess(it)
            }

            // Synchronisation du dossier après chargement des zones et images
            lifecycleScope.launch(Dispatchers.IO) {
                synchronizeFolder()
            }
        } else {
            showSnackbar("Fichier d’aventure introuvable.")
            promptAdventureName()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lastUri = getLastFolderUri()
        if (lastUri != null) {
            if (hasPersistedPermission(lastUri)) {
                logDebug("TestFlow", ">>> Permission OK au redémarrage")
            } else {
                logDebug("TestFlow", ">>> Permission PERDUE au redémarrage")
            }
        }
        currentFolderUri = lastUri
        Log.d("EditorActivity", "Restored last folder URI: $lastUri")


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
            // Ajout du log juste après deleteSelectedZones()
            Log.d("DeleteZones", "deleteSelectedZones() appelé, zones supprimées.")
            currentImageName?.let { imageName ->
                imageDataMap[imageName] = binding.drawingView.getAllZones().toMutableList()
                // Ajout du log juste après la mise à jour imageDataMap
                Log.d("DeleteZones", "imageDataMap mis à jour pour $imageName : ${imageDataMap[imageName]?.size} zones")
                val imageZonesMap = imageDataMap.mapValues { entry ->
                    entry.value.map { it.toZoneData() }
                }
                // Ajout du log juste avant updateImageZonesMapAndRefresh
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
            onImageSelected = { fullPath, _ ->
                Log.d("EditorActivity", "Vignette cliquée: $fullPath")
                Log.d("ZoneLink", "selectedZone: ${binding.drawingView.selectedZone}")
                val selectedZone = binding.drawingView.selectedZone
                if (selectedZone != null) {
                    linkSelectedZoneToImage(fullPath)
                } else {
                    onImageSelected(fullPath, false)
                }
            },

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

        // Mettre à jour les zones dans l'adapter après configuration de imageAdapter
        /*val imageZonesMap = imageDataMap.mapValues { entry ->
            entry.value.map { it.toZoneData() }
        }
        imageAdapter.imageZonesMap = imageZonesMap
        imageAdapter.notifyDataSetChanged()*/

        // 🛠 Accès propre aux éléments du header
        adventureNameTextView = binding.headerAdventure.adventureNameTextView


        // Bottom bar
        val bottomBarView = binding.bottomBar.root
        imagesInfoText = bottomBarView.findViewById(R.id.textImageCount)
        worldsInfoText = bottomBarView.findViewById(R.id.textWorldCount)
        selectedImagesCount = bottomBarView.findViewById(R.id.selectedImagesCount)
        selectedWorldsCount = bottomBarView.findViewById(R.id.selectedWorldsCount)
        selectionInfoContainer = bottomBarView.findViewById(R.id.selectionInfoContainer)

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
            // Lancer une tentative de chargement différée (le temps que les images soient bien mappées)
            Handler(Looper.getMainLooper()).postDelayed({
                onImageSelected(imageFromIntent)
            }, 1000)
        }

        // 📂 Boutons pour ouvrir/fermer tous les groupes dans la sidebar
        val buttonExpandAll = binding.bottomBar.root.findViewById<MaterialButton>(R.id.buttonExpandAll)
        val buttonCollapseAll = binding.bottomBar.root.findViewById<MaterialButton>(R.id.buttonCollapseAll)

        buttonExpandAll.setOnClickListener {
            imageAdapter.toggleAllGroups(true)
        }

        buttonCollapseAll.setOnClickListener {
            imageAdapter.toggleAllGroups(false)
        }

        // Accès aux boutons dans la BottomBar
        val buttonSave = binding.bottomBar.buttonSave
        //val buttonRenameAdventure = binding.bottomBar.buttonRenameAdventure

        // Ajout du bouton Refresh juste après le bouton Menu (Aventure)
        /*val buttonRefresh = Button(this).apply {
            text = "Refresh"
            setOnClickListener {
                if (isBusy) {
                    showSnackbar("Patiente, chargement en cours…")
                    return@setOnClickListener
                }
                Glide.get(this@EditorActivity).clearMemory()
                CoroutineScope(Dispatchers.IO).launch { Glide.get(applicationContext).clearDiskCache() }
                saveZones()
                Toast.makeText(this@EditorActivity, "Données sauvegardées, images rechargées…", Toast.LENGTH_SHORT).show()
                //currentFolderUri?.let { requestFolderAccess(it)
            }
        }*/
        val bottomBar = binding.bottomBar.root
        //val indexMenu = bottomBar.indexOfChild(findViewById(R.id.buttonMenu))
        //bottomBar.addView(buttonRefresh, indexMenu + 1)

        // Listeners sur les boutons
        buttonSave.setOnClickListener {
            Log.d("EDITOR", "Bouton sauvegarder cliqué")
            Toast.makeText(this, "Sauvegarde cliquée", Toast.LENGTH_SHORT).show()
            saveZones()
            hasJustSaved = true
        }


        // DrawingView cliquable
        binding.drawingView.isClickable = true
        binding.drawingView.onTapListener = {
            scrollToCurrentImageThumbnail()
        }
        binding.drawingView.setOnClickListener {
            scrollToCurrentImageThumbnail()
        }

        binding.drawingView.onZoneSelected = {
            updateDeleteButtonVisibilityForZones()
            refreshThumbnailZones()
        }

        // Ajout d'une zone nouvellement créée à imageDataMap pour l'image courante
        binding.drawingView.onZoneCreated = { zone ->
            currentImageName?.let { imageName ->
                imageDataMap[imageName]?.add(zone)

                val imageZonesMap = imageDataMap.mapValues { it.value.map { z -> z.toZoneData() } }
                imageAdapter.updateImageZonesMapAndRefresh(imageZonesMap)

                val index = imageAdapter.currentList.indexOfFirst { it.fullPath == imageName }
                if (index >= 0) {
                    val vh = binding.recyclerViewThumbnails.findViewHolderForAdapterPosition(index)
                    val updatedZones = imageZonesMap[imageName] ?: emptyList()
                    if (::imageAdapter.isInitialized && vh is ImageAdapter.ImageViewHolder) {
                        vh.overlayView.zones = updatedZones
                        vh.overlayView.invalidate()
                        Log.d("ZoneUpdate", "Zones mises à jour dans overlayView pour $imageName")
                    }
                }
            }
        }

        // Ajout des boutons Menu et StartAdventure
        findViewById<MaterialButton>(R.id.buttonMenu).setOnClickListener {
            saveZones()
            showSnackbar("Aventure sauvegardée")
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }, 500)
        }

        findViewById<MaterialButton>(R.id.buttonStartAdventure).setOnClickListener {
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
                //intent.putExtra("adventureId", currentAdventureName)
                intent.putExtra("adventureJsonUri", fileUri)
                intent.putExtra("folderUri", folderUri?.toString())
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Très important pour donner accès au fichier
                startActivity(intent)
                finish()
            }, 500)
        }

        // Remplacement du bouton d'import dossier par un bouton de synchronisation
        val buttonSyncFolder = binding.bottomBar.root.findViewById<MaterialButton>(R.id.buttonSyncFolder)
        buttonSyncFolder.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                synchronizeFolder()
            }
        }
    }

// --- FONCTIONS UTILITAIRES ---

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
        logDebug("SaveZones", "Sauvegarde des zones dans le fichier ${file.absolutePath}")
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
        if (isSelectionMode) {
            val images = selectedItems.count { !isGroupPath(it) }
            val folders = selectedItems.count { isGroupPath(it) }
            selectionInfoContainer.isVisible = true
            selectedImagesCount.text = getString(R.string.images_count, images)
            //selectedWorldsCount.text = getString(R.string.folders_count, folders)
            imagesInfoText.visibility = View.GONE
            //worldsInfoText.visibility = View.GONE
        } else {
            selectionInfoContainer.isVisible = false
            imagesInfoText.visibility = View.VISIBLE
            worldsInfoText.visibility = View.VISIBLE
            imagesInfoText.text = getString(R.string.images_count, imageDataMap.size)
            imagesInfoText.textSize = 16f
            updateWorldAndUnlinkedCounts()
        }
    }

    private fun updateWorldAndUnlinkedCounts() {
        if (!::imageRootNode.isInitialized) {
            Log.w("EditorActivity", "imageRootNode non initialisé → on saute updateWorldAndUnlinkedCounts()")
            return
        }
        val worldCount = imageRootNode.children.count { it.name != "Racine" }
        val linkedImageNames = imageDataMap
            .flatMap { it.value }
            .mapNotNull { it.linkedImagePath }
            .toSet()
        val unlinkedCount = imageFileMap.keys.count { it !in linkedImageNames }
        worldsInfoText.text = getString(R.string.worlds_count, worldCount)
        worldsInfoText.textSize = 16f
        //findViewById<TextView>(R.id.textUnlinkedCount).text = getString(R.string.unlinked_count, unlinkedCount)
    }

    private fun isGroupPath(fullPath: String): Boolean {
        fun findNode(node: ImageGroupNode): Boolean {
            if (node.fullPath == fullPath) return true
            return node.children.any { findNode(it) }
        }
        return findNode(imageRootNode)
    }

    // removeGroupAndImages supprimée




    private fun loadImagesFromFolder(uri: Uri, clearData: Boolean = true) {
        logDebug("LoadImages", "Début loadImagesFromFolder(uri=$uri, clearData=$clearData)")
        isBusy = true
        if (debugLogs) Log.d("EditorActivity", "Loading images from folder: $uri")

        if (clearData) {
            imageDataMap.clear()
            imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
        }

        var firstImageLoaded = false
        val skippedFiles = mutableListOf<String>()
        imageLoadingScope.launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val allImageFiles = mutableListOf<Pair<DocumentFile, String>>()
            val seenPaths = mutableSetOf<String>()
            val imageFiles = mutableMapOf<String, DocumentFile>()

            withContext(Dispatchers.Main) {
                if (!::imagesInfoText.isInitialized) {
                    Log.w("EditorActivity", "imagesInfoText non initialisé, on saute la mise à jour UI.")
                } else {
                    loadingProgressBar.visibility = View.VISIBLE
                    //loadingTextView.visibility = View.VISIBLE
                    imagesInfoText.text = getString(R.string.loading_progress)
                    imagesInfoText.textSize = 18f

                }
                loadingProgressBar.visibility = View.VISIBLE
                imagesInfoText.text = getString(R.string.loading_progress)
                imagesInfoText.textSize = 22f
                //loadingTextView.visibility = View.VISIBLE
                // Faire disparaître textWorldCount pendant le chargement
                if (::worldsInfoText.isInitialized) {
                    worldsInfoText.visibility = View.GONE
                    // Masquer les boutons de la bottom bar
                    binding.bottomBar.buttonSave.visibility = View.GONE
                    binding.bottomBar.buttonMenu.visibility = View.GONE
                    binding.bottomBar.buttonStartAdventure.visibility = View.GONE
                    binding.bottomBar.buttonSyncFolder.visibility = View.GONE
                }
            }

            // Ajout: clear les listes globales si clearData demandé (une seule fois au début)
            if (clearData) {
                allImageFiles.clear()
                seenPaths.clear()
                imageFileMap.clear()
            }

            fun traverse(file: DocumentFile, path: String = "") {
                Log.d("EditorActivity", "Nombre total d’images trouvées : ${allImageFiles.size}")
                if (file.isDirectory) {
                    val newPath = if (path.isEmpty()) file.name ?: "" else "$path/${file.name}"
                    file.listFiles()?.forEach { traverse(it, newPath) }
                } else {
                    val name = file.name ?: return
                    val fullPath = if (path.isEmpty()) name else "$path/$name"
                    if (isValidImage(file)) {
                        // Nouveau filtre doublon : n'ajoute que si seenPaths.add(fullPath) == true
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
            logDebug("AppDebug", "Nombre total d'images trouvées : ${allImageFiles.size}")

            logDebug("EditorActivity", "allImageFiles final: ${allImageFiles.map { it.second }}")
            allImageFiles.sortWith(compareBy({ it.second.count { c -> c == '/' } }, { it.second }))

            // Dédoublonnage de la liste avant le traitement en batch
            val dedupedImageFiles = allImageFiles.distinctBy { it.second }.filter { seenPaths.add(it.second) }
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
                            Log.d("LoadImages", "Image déjà en cache → skip : $fullPath")
                            semaphore.release()
                            return@async
                        }
                        try {
                            // Nouveau : vérifie l'absence dans imageBitmapMap avant ajout
                            if (!imageFileMap.containsKey(fullPath)) {
                                imageFileMap[fullPath] = file
                            }
                            withContext(Dispatchers.Main) {
                                // Vérifie si l'image est déjà présente dans l'adapter avant d'ajouter (toujours)
                                if (!imageAdapter.currentList.any { it.fullPath == fullPath }) {
                                    imageAdapter.addImage(fullPath)
                                }
                            }
                            if (debugLogs) Log.d("EditorActivity", "Image trouvée: $fullPath")
                            loadedImagesCount = minOf(loadedImagesCount + 1, totalImagesToLoad)
                            if (loadedImagesCount % 10 == 0) {
                                logDebug("EditorActivity", "Chargées : $loadedImagesCount / $totalImagesToLoad")
                            }
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
                // Ajout de la mise à jour de la progression après chaque batch
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
                binding.bottomBar.buttonSave.visibility = View.VISIBLE
                binding.bottomBar.buttonMenu.visibility = View.VISIBLE
                binding.bottomBar.buttonStartAdventure.visibility = View.VISIBLE
                binding.bottomBar.buttonSyncFolder.visibility = View.VISIBLE
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
                logDebug("LoadImages", "Chargement des images terminé → éditeur prêt")
                isBusy = false
            }
        }
        logDebug("AppDebug", "Chargement des images terminé → éditeur prêt")
    }
    // Vérifie si une image est déjà présente dans l'adapter
    private fun isImageAlreadyInAdapter(fullPath: String): Boolean {
        return imageAdapter.currentList.any { it.fullPath == fullPath }
    }


    private fun updateLoadingProgress() {
        try {
            if (!::imagesInfoText.isInitialized) return
            if (totalImagesToLoad > 0) {
                val safeLoadedCount = minOf(loadedImagesCount, totalImagesToLoad)
                val progressPercent = (loadedImagesCount * 100) / totalImagesToLoad
                //loadingProgressBar.progress = progressPercent
                //imagesInfoText.text = getString(R.string.loading_progress, safeLoadedCount, totalImagesToLoad)
                imagesInfoText.text = getString(R.string.loading_progress)

            }
            binding.bottomBar.buttonSave.isEnabled = true
            //loadingProgressBar.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("EditorActivity", "Erreur UI update: ${e.message}")
        }
        Log.d("EditorActivity", "Progression : $loadedImagesCount/$totalImagesToLoad")
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

    // Affiche ou masque le bouton de suppression des zones selon la sélection
    fun updateDeleteButtonVisibilityForZones() {
        val hasSelection = binding.drawingView.hasSelectedZones()
        logDebug("EditorActivity", "updateDeleteButtonVisibilityForZones() → hasSelection=$hasSelection")
        deleteZonesButton.visibility = if (hasSelection) View.VISIBLE else View.GONE
    }

    // Permet à DrawingView de masquer le bouton de suppression des zones
    fun hideDeleteZonesButton() {
        deleteZonesButton.visibility = View.GONE
        logDebug("EditorActivity", "hideDeleteZonesButton() appelé → on cache le bouton")
    }


    // --- Synchronisation du dossier ---
    private suspend fun synchronizeFolder() {
        val uri = currentFolderUri
        logDebug("SyncFolder", "Début de synchronizeFolder pour URI: $uri")
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
                        logDebug("Sync", "Erreur chargement $path : ${e.message}")
                    }
                }
            }

            val bitmapPathPairs = imageDataMap.map { (path, _) -> Pair(null, path) }
            val paths = imageDataMap.keys.toList()
            imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(paths)
            imageDataMap.forEach { (imageName, zones) ->
                logDebug("SyncFolder", "Après synchro - Image: $imageName → Zones count: ${zones.size}")
            }
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
        logDebug("SyncFolder", message)
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
                val layoutManager = binding.recyclerViewThumbnails.layoutManager as LinearLayoutManager
                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                val offset = layoutManager.findViewByPosition(firstVisiblePosition)?.top ?: 0

                val imageZonesMap = imageDataMap.mapValues { entry ->
                    entry.value.map { it.toZoneData() }
                }
                imageAdapter.updateImageZonesMapAndRefresh(imageZonesMap)
                val index = imageAdapter.currentList.indexOfFirst { it.fullPath == linkedImagePath }
                if (index >= 0) {
                    imageAdapter.notifyItemChanged(index)
                }
                layoutManager.scrollToPositionWithOffset(firstVisiblePosition, offset)
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
        logDebug("EnterEditMode", "Entrée dans le mode édition pour $adventureName")
        currentAdventureName = adventureName
        logDebug("EnterEditMode", "currentAdventureName défini à $currentAdventureName")
        currentAdventureJsonUri = Uri.fromFile(File(filesDir, "$currentAdventureName.json"))
        logDebug("EnterEditMode", "currentAdventureJsonUri défini à $currentAdventureJsonUri")
        val file = File(filesDir, "${adventureName}_zones.json")
        logDebug("EnterEditMode", "Vérification du fichier : ${file.absolutePath}")
        if (file.exists()) {
            val json = file.readText()
            logDebug("EnterEditMode", "Fichier trouvé, parsing JSON pour $adventureName")
            val adventureData = GsonBuilder().create().fromJson(json, AdventureData::class.java)
            val folderUriString = adventureData.folderUri
            // Patch: check folderUriString starts with content://
            currentFolderUri = if (!folderUriString.isNullOrEmpty() && folderUriString.startsWith("content://")) Uri.parse(folderUriString) else null

            // Ajout du test pour éviter de demander à l'utilisateur de resélectionner si déjà autorisé
            logDebug("EnterEditMode", "Check persisted permissions avant reload")
            if (currentFolderUri != null && hasPersistedPermission(currentFolderUri!!)) {
                logDebug("TestFlow", ">>> Appel enterEditMode($adventureName)")
                // Déplacement ici de la définition de startImagePath juste avant requestFolderAccess
                adventureNameTextView.text = adventureData.adventureTitle
                logDebug("EnterEditMode", "Titre affiché mis à jour : ${adventureData.adventureTitle}")
                startImagePath = adventureData.startImagePath
                imageAdapter.startImagePath = startImagePath
                // Chargement des zones pour chaque image
                imageDataMap.clear()
                adventureData.images.forEach { image ->
                    val zones = image.zones.map { it.toZone() }.toMutableList()
                    imageDataMap[image.imageName] = zones
                }
                logDebug("EnterEditMode", "Chargement des images et zones terminé, images=${imageDataMap.size}")
                logDebug("EnterEditMode", "Zones chargées avant le chargement des images.")
                // Ajout : log du nombre de zones pour chaque image
                imageDataMap.forEach { (imageName, zones) ->
                    logDebug("EnterEditMode", "Image: $imageName → Zones count: ${zones.size}")
                    zones.forEach { zone ->
                        logDebug("EnterEditMode", "   Zone rect: ${zone.rect}, linkedImagePath: ${zone.linkedImagePath}")
                    }
                }
                // Fix: always initialize imageRootNode to a valid node, not a String
                imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
                logDebug("EnterEditMode", "currentFolderUri vérifié : $currentFolderUri")
                imageAdapter.updateData(emptyList())
                startImagePath = adventureData.startImagePath
                requestFolderAccess(currentFolderUri!!, clearData = false)
            } else {
                openFolderPicker()
                return
            }

            if (currentFolderUri != null) {
                logDebug("EnterEditMode", "FolderUri récupéré : $currentFolderUri")
            }

            logDebug("EnterEditMode", "Mode édition prêt, synchro non encore lancée")
            // Affiche automatiquement la première image et ses zones après chargement de l'aventure

        } else {
            currentFolderUri = null
            logDebug("EnterEditMode", "Aventure introuvable, création d’une nouvelle aventure")
            showSnackbar("Aventure introuvable, création d’une nouvelle.")
            logDebug("EnterEditMode", "promptAdventureName() appelé car fichier introuvable")
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

    // handleDeleteSelectedItems supprimée



}
