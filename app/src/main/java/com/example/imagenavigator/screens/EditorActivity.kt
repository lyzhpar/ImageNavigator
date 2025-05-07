package com.example.imagenavigator.screens

import java.util.concurrent.Semaphore

//import com.example.imagenavigator.BuildConfig

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
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
import com.example.imagenavigator.model.Adventure
import com.example.imagenavigator.model.ImageData
import com.example.imagenavigator.model.toZone
import com.example.imagenavigator.model.toZoneData
import android.widget.ProgressBar

import androidx.lifecycle.lifecycleScope
import kotlin.toString


class EditorActivity : BaseActivity() {


    // --- Déclarations ---

    private lateinit var binding: ActivityEditorBinding
    private lateinit var imageAdapter: ImageAdapter

    private val groupedImages = mutableListOf<ImageGroup>()
    var imageDataMap = mutableMapOf<String, MutableList<Zone>>()
    var imageBitmapMap = mutableMapOf<String, Bitmap>()
    private lateinit var imageRootNode: ImageGroupNode
    private var currentImageName: String? = null

    // Map pour retrouver le DocumentFile correspondant à chaque image
    private val imageFileMap = mutableMapOf<String, DocumentFile>()

    private lateinit var adventureNameTextView: TextView
    private var currentAdventureName: String = ""

    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false
    private lateinit var deleteButton: Button
    private lateinit var selectionModeIndicator: TextView

    private lateinit var imagesInfoText: TextView
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


    private fun logDebug(tag: String, message: String) {
        // Paramètre tag inutilisé, log supprimé pour respecter consigne
        if (debugLogs) {
            Log.d(tag, message)
        }
    }

    private fun logBitmapCache() {
        if (!debugLogs) return
        Log.d("BitmapCache", "--- Contenu de imageBitmapMap ---")
        imageBitmapMap.forEach { (imageName, bitmap) ->
            Log.d("BitmapCache", "Image: $imageName → bitmap: ${bitmap.width}x${bitmap.height}")
        }
        Log.d("BitmapCache", "---------------------------------")
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
    private fun requestFolderAccess(uri: Uri) {
        // Avant de charger, reset l'adapter et l'arbre racine et recycle les bitmaps
        if (!hasPersistedPermission(uri)) {
            showSnackbar("Permission expirée sur le dossier sélectionné.")
            return
        }
        imageAdapter.updateData(emptyList())
        imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
        imageBitmapMap.values.forEach { if (!it.isRecycled) it.recycle() }
        imageBitmapMap.clear()
        // Charger directement les images depuis le dossier sans relancer de sélecteur
        // En mode édition, il ne faut pas effacer imageDataMap (clearData = false)
        loadImagesFromFolder(uri, clearData = false)
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
                    // Ajout: prendre la permission persistante sur le dossier sélectionné
                    val flags = result.data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (flags != null) {
                        contentResolver.takePersistableUriPermission(uri, flags)
                    }
                    currentFolderUri = uri
                    val adventureFromIntent = intent.getStringExtra("adventureName")
                    if (adventureFromIntent != null) {
                        enterEditMode(adventureFromIntent)
                    }
                    else {
                        loadImagesFromFolder(uri, true)
                    }
                }
            }
        }

    // Quand une image est sélectionnée
    private fun onImageSelected(
        /*bitmap: Bitmap,*/ fullPath: String
    ) {
        // Avant de remplacer le bitmap et les zones, enregistrer les zones de l'image courante
        currentImageName?.let { oldImageName ->
            imageDataMap[oldImageName] = binding.drawingView.getAllZones().toMutableList()
        }

        logBitmapCache()

        // Remplacement : accès direct au bitmap chargé
        binding.drawingView.imageBitmap = imageBitmapMap[fullPath]
        binding.drawingView.setZonesForCurrentImage(imageDataMap[fullPath] ?: emptyList())
        currentImageName = fullPath

        // Toujours synchroniser le cache des bitmaps avec le DrawingView
        binding.drawingView.imageBitmapMap = imageBitmapMap

        // --- Empêcher le saut de scroll lors du rafraîchissement des vignettes ---
        val layoutManager = binding.recyclerViewThumbnails.layoutManager as LinearLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val offset = layoutManager.findViewByPosition(firstVisiblePosition)?.top ?: 0

        // Mettre à jour les zones dans l'adapter (pour les vignettes)
        val imageZonesMap = imageDataMap.mapValues { entry ->
            entry.value.map { it.toZoneData() }
        }
        imageAdapter.imageZonesMap = imageZonesMap
        imageAdapter.notifyDataSetChanged()

        layoutManager.scrollToPositionWithOffset(firstVisiblePosition, offset)

        logDebug("ImageDataMap", "--- Contenu de imageDataMap ---")
        imageDataMap.forEach { (imageName, zones) ->
            logDebug("ImageDataMap", "Image: $imageName → Zones: ${zones.size}")
            zones.forEach { zone ->
                logDebug("ImageDataMap", "   Zone rect: ${zone.rect} linkedImagePath: ${zone.linkedImagePath}")
            }
        }
        logDebug("ImageDataMap", "-------------------------------")
    }

    // Quand l'utilisateur demande de renommer un groupe
    private fun onGroupRenameRequested(
        /*updatedItem: ImageAdapter.DisplayItem.GroupItem*/
    ) {
        // Tu peux afficher une boîte de dialogue pour demander un nouveau nom
        AlertDialog.Builder(this)
            .setTitle("Renommer le groupe")
            .setMessage("Renommer les groupes est à implémenter.")
            .setPositiveButton("OK", null)
            .show()
    }

    // Quand l'utilisateur demande de supprimer un groupe
    private fun onGroupDeleteRequested(
        /*itemToDelete: ImageAdapter.DisplayItem.GroupItem*/
    ) {
        // Tu peux supprimer le groupe directement ou demander confirmation
        AlertDialog.Builder(this)
            .setTitle("Supprimer le groupe ?")
            .setMessage("Veux-tu vraiment supprimer ce groupe et toutes ses images ?")
            .setPositiveButton("Supprimer") { _, _ ->
                handleDeleteSelectedItems() // Tu peux aussi faire une fonction spéciale
            }
            .setNegativeButton("Annuler", null)
            .show()
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

    //private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? {
    //    val screenSize =
    //        resources.displayMetrics.widthPixels.coerceAtLeast(resources.displayMetrics.heightPixels)
    //    return try {
    //        withContext(Dispatchers.IO) {
    //            Glide.with(this@EditorActivity)
    //                .asBitmap()
    //                .load(uri)
    //                .apply(
    //                    RequestOptions()
    //                        .override(screenSize / 2)
    //                        .diskCacheStrategy(DiskCacheStrategy.ALL)
    //                )
    //                .submit()
    //                .get()
    //        }
    //    } catch (e: Exception) {
    //        Log.e(
    //            "EditorActivity",
    //            "Erreur lors du chargement de l'image à partir de l'URI : $uri",
    //            e
    //        )
    //        null
    //    }
    //}

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


        Log.d("onCreate", "Contenu de imageDataMap : ${imageDataMap.keys}")


        if (binding.syncOverlay.visibility == View.VISIBLE) {
            Log.d("SyncFolder", "Synchronisation déjà en cours, on ignore l’appel.")
            return
        }

        // --- Initialisation du bouton Supprimer (deleteButton) ---
        deleteButton = Button(this).apply {
            text = "Supprimer"
            visibility = View.GONE
            isEnabled = false
            setOnClickListener { handleDeleteSelectedItems(this) }
        }
        binding.bottomBar.root.addView(deleteButton)

        deleteZonesButton = findViewById(R.id.deleteZonesButton)
        deleteZonesButton.setOnClickListener {
            if (debugLogs) Log.d("DeleteZones", "Suppression demandée via bouton")
            binding.drawingView.deleteSelectedZones()
            currentImageName?.let { imageName ->
                imageDataMap[imageName] = binding.drawingView.getAllZones().toMutableList()
                if (debugLogs) {
                    Log.d(
                        "DeleteZones",
                        "Zones restantes pour $imageName : ${imageDataMap[imageName]?.size}"
                    )
                }
            }
            deleteZonesButton.visibility = View.GONE
        }

        loadingProgressBar = findViewById(R.id.progressBarLoading)

        // Adapter images
        imageAdapter = ImageAdapter(
            rootGroups = groupedImages,
            onImageSelected = { fullPath ->
                val selectedZone = binding.drawingView.selectedZone
                if (selectedZone != null) {
                    linkSelectedZoneToImage(fullPath)
                } else {
                    onImageSelected(fullPath)
                }
            },
            onGroupRenameRequested = { onGroupRenameRequested() },
            onGroupDeleteRequested = { onGroupDeleteRequested() },
            onItemLongPress = { item -> setStartImage(item.fullPath) },
            getSelectedItems = { imageAdapter.getSelectedItems() },
            exitSelectionMode = { exitSelectionMode() }
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
        val imageZonesMap = imageDataMap.mapValues { entry ->
            entry.value.map { it.toZoneData() }
        }
        imageAdapter.imageZonesMap = imageZonesMap
        imageAdapter.notifyDataSetChanged()

        // 🛠 Accès propre aux éléments du header
        adventureNameTextView = binding.headerAdventure.adventureNameTextView

        // CONFIG START - Header Config Button
        // --- Ajout bouton Config dans le header ---
        // Ajoute un bouton config à droite du titre, et le clic sur le titre déclenche aussi la config
        // binding.headerAdventure.adventureNameTextView.setCompoundDrawablesWithIntrinsicBounds(
        //    0, 0, android.R.drawable.ic_menu_preferences, 0
        //)
        // binding.headerAdventure.adventureNameTextView.setOnClickListener { showConfigDialog() }
        // CONFIG END - Header Config Button

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

        // Accès aux boutons dans la BottomBar
        val buttonSave = binding.bottomBar.buttonSave
        //val buttonRenameAdventure = binding.bottomBar.buttonRenameAdventure

        // Listeners sur les boutons
        buttonSave.setOnClickListener {
            Log.d("EDITOR", "Bouton sauvegarder cliqué")
            Toast.makeText(this, "Sauvegarde cliquée", Toast.LENGTH_SHORT).show()
            saveZones()
        }


        // DrawingView cliquable
        binding.drawingView.isClickable = true
        binding.drawingView.onTapListener = {
            if (isSelectionMode) {
                exitSelectionMode()
                updateDeleteButtonVisibility(deleteButton)
                updateBottomBarInfo()
                deleteButton.isEnabled = false
            }
        }
        // Ajout d'une zone nouvellement créée à imageDataMap pour l'image courante
        binding.drawingView.onZoneCreated = { zone ->
            currentImageName?.let { imageName ->
                imageDataMap[imageName]?.add(zone)
            }
        }

        // Ajout des boutons Menu et StartAdventure
        findViewById<Button>(R.id.buttonMenu).setOnClickListener {
            saveZones()
            showSnackbar("Aventure sauvegardée")
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }, 1500)
        }

        findViewById<Button>(R.id.buttonStartAdventure).setOnClickListener {
            val intent = Intent(this, NavigatorActivity::class.java)
            intent.putExtra("adventureId", currentAdventureName)
            startActivity(intent)
        }

        // CONFIG START - FloatingActionButton
        // Ajout du FloatingActionButton en bas à droite (toujours visible)
        /*val fab = FloatingActionButton(this).apply {
            setImageResource(R.drawable.ic_baseline_settings_24) // icône roue dentée moderne
            setOnClickListener { showConfigDialog() }
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = 32
            bottomMargin = 32
        }
        (binding.root as ViewGroup).addView(fab, params)*/
        // CONFIG END - FloatingActionButton

        // Remplacement du bouton d'import dossier par un bouton de synchronisation
        val buttonSyncFolder = binding.bottomBar.root.findViewById<Button>(R.id.buttonSyncFolder)
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
            val name = input.text.toString().trim().replace("\n", "")
            if (name.isEmpty() || adventureFileExists(name)) {
                Toast.makeText(this, "Nom invalide ou existant.", Toast.LENGTH_SHORT).show()
                promptAdventureName()
            } else {
                currentAdventureName = name
                adventureNameTextView.text = currentAdventureName
                currentAdventureJsonUri = Uri.fromFile(File(filesDir, "$currentAdventureName.json"))
                openFolderPicker()
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val name = input.text.toString().trim().replace("\n", "")
                if (name.isEmpty() || adventureFileExists(name)) {
                    Toast.makeText(this, "Nom invalide ou existant.", Toast.LENGTH_SHORT).show()
                    promptAdventureName()
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

    /*private fun saveZones() {
        val file = File(filesDir, "${currentAdventureName}_zones.json")
        logDebug("SaveZones", "Enregistrement de l’aventure : $currentAdventureName → ${file.absolutePath}")
        if (currentAdventureName.isEmpty()) {
            currentAdventureName = "AventureSansNom"
        }

        // Ajout de toutes les images, même celles sans zones
        val adventureData = generateAdventureData()

        // Vérifie qu'il y a bien des images à sauvegarder
        if (adventureData.images.isEmpty()) {
            Log.w("SaveZones", "Aucune image à sauvegarder → opération annulée.")
            Snackbar.make(
                findViewById(android.R.id.content),
                "Rien à sauvegarder (aucune image chargée).",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(adventureData) // Convertir l'objet adventureData en JSON
        logDebug("SaveZones", "Contenu JSON enregistré:\n$json")

        // Écrire dans le fichier
        file.writeText(json)

        Log.d("SaveZones", "Aventure sauvegardée sous ${file.absolutePath}")
        Snackbar.make(
            findViewById(android.R.id.content),
            "Aventure sauvegardée : $currentAdventureName",
            Snackbar.LENGTH_SHORT
        ).show()
    }*/


    /*private fun saveZones() {
        if (currentFolderUri == null || imageBitmapMap.isEmpty()) {
            Log.w("AutoSave", "Pas de dossier ou d’images → autosave annulée.")
            return
        }
        val adventureData = generateAdventureData()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(adventureData)
        val file = File(filesDir, "${currentAdventureName}_zones.json")
        file.writeText(json)

        Snackbar.make(
            findViewById(android.R.id.content),
            "Aventure sauvegardée : $currentAdventureName",
            Snackbar.LENGTH_SHORT
        ).show()
    }*/


    private fun saveZones() {
        if (currentFolderUri == null || imageBitmapMap.isEmpty()) {
            Log.w("AutoSave", "Pas de dossier ou d’images → autosave annulée.")
            return
        }
        Log.d("SaveZones", "Contenu de imageBitmapMap : ${imageBitmapMap.keys}")
        Log.d("SaveZones", "Contenu de imageDataMap : ${imageDataMap.keys}")
        Log.d("SaveZones", "Dossier sélectionné : $currentFolderUri")
        Log.d("SaveZones", "Nombre d'images à sauvegarder : ${imageBitmapMap.size}")

        val adventureData = generateAdventureData()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(adventureData)

        Log.d("SaveZones", "Données d'aventure converties en JSON : $json")

        val file = File(filesDir, "${currentAdventureName}_zones.json")
        file.writeText(json)

        Snackbar.make(
            findViewById(android.R.id.content),
            "Aventure sauvegardée : $currentAdventureName",
            Snackbar.LENGTH_SHORT
        ).show()
        Log.d("SaveZones", "Aventure sauvegardée sous ${file.absolutePath}")
    }




    private fun toggleSelection(fullPath: String) {
        if (!isSelectionMode) {
            isSelectionMode = true
            selectedItems.clear()
        }
        if (selectedItems.contains(fullPath)) {
            selectedItems.remove(fullPath)
        } else {
            selectedItems.add(fullPath)
        }
        imageAdapter.setSelectionMode(isSelectionMode, selectedItems)
        updateDeleteButtonVisibility(deleteButton)
        updateBottomBarInfo()
    }

    private fun updateDeleteButtonVisibility(deleteButton: View) {
        deleteButton.visibility = if (selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
        deleteButton.isEnabled = selectedItems.isNotEmpty()
        selectionModeIndicator.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
    }

    private fun handleDeleteSelectedItems(deleteButton: View? = null/*, itemToDelete: ??? = null*/) {
        val itemsToDelete = selectedItems.toList()
        for (fullPath in itemsToDelete) {
            if (isGroupPath(fullPath)) removeGroupAndImages(fullPath)
            else removeImage(fullPath)
        }
        selectedItems.clear()
        exitSelectionMode()
        imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
        deleteButton?.let { updateDeleteButtonVisibility(it) }
        updateBottomBarInfo()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        imageAdapter.setSelectionMode(false, selectedItems)
        updateBottomBarInfo()
    }

    private fun updateBottomBarInfo(isLoading: Boolean = false) {
        if (!::imagesInfoText.isInitialized) return
        if (isSelectionMode) {
            val images = selectedItems.count { !isGroupPath(it) }
            val folders = selectedItems.count { isGroupPath(it) }
            selectionInfoContainer.isVisible = true
            selectedImagesCount.text = getString(R.string.images_count, images)
            selectedWorldsCount.text = getString(R.string.folders_count, folders)
            imagesInfoText.visibility = View.GONE
            worldsInfoText.visibility = View.GONE
        } else {
            selectionInfoContainer.isVisible = false
            imagesInfoText.visibility = View.VISIBLE
            worldsInfoText.visibility = View.VISIBLE
            if (isLoading) {
                imagesInfoText.text = getString(R.string.loading_images_count, imageBitmapMap.size)
            } else {
                imagesInfoText.text = getString(R.string.images_count, imageBitmapMap.size)
            }
            // Ajout de l'appel de la nouvelle fonction pour la mise à jour des mondes et non liées
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
        val unlinkedCount = imageBitmapMap.keys.count { it !in linkedImageNames }
        worldsInfoText.text = getString(R.string.worlds_count, worldCount)
        //findViewById<TextView>(R.id.textUnlinkedCount).text = getString(R.string.unlinked_count, unlinkedCount)
    }

    private fun isGroupPath(fullPath: String): Boolean {
        fun findNode(node: ImageGroupNode): Boolean {
            if (node.fullPath == fullPath) return true
            return node.children.any { findNode(it) }
        }
        return findNode(imageRootNode)
    }

    private fun removeGroupAndImages(fullPath: String) {
        fun removeRecursively(parent: ImageGroupNode): Boolean {
            val iterator = parent.children.iterator()
            while (iterator.hasNext()) {
                val child = iterator.next()
                if (child.fullPath == fullPath) {
                    child.images.forEach { (_, path) ->
                        imageBitmapMap.remove(path)
                        imageDataMap.remove(path)
                    }
                    iterator.remove()
                    return true
                } else if (removeRecursively(child)) {
                    return true
                }
            }
            return false
        }
        removeRecursively(imageRootNode)
    }

    private fun removeImage(fullPath: String) {
        imageBitmapMap[fullPath]?.recycle()
        imageBitmapMap.remove(fullPath)
        imageDataMap.remove(fullPath)
        removeImageFromNode(imageRootNode, fullPath)
    }

    private fun removeImageFromNode(node: ImageGroupNode, fullPath: String) {
        node.images.removeAll { it.second == fullPath }
        node.children.forEach { removeImageFromNode(it, fullPath) }
    }

    private fun loadImagesFromFolder(uri: Uri, clearData: Boolean = true) {
        if (debugLogs) Log.d("EditorActivity", "Loading images from folder: $uri")

        var firstImageLoaded = false
        val skippedFiles = mutableListOf<String>()
        imageLoadingScope.launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val allImageFiles = mutableListOf<Pair<DocumentFile, String>>()
            val seenPaths = mutableSetOf<String>()
            val imageFiles = mutableMapOf<String, DocumentFile>()

            // Afficher la barre de progression ET le texte d'initialisation juste avant traverse()
            withContext(Dispatchers.Main) {
                // Ajout du guard pour éviter crash si non initialisé
                if (!::imagesInfoText.isInitialized) {
                    Log.w("EditorActivity", "imagesInfoText non initialisé, on saute la mise à jour UI.")
                } else {
                    loadingProgressBar.visibility = View.VISIBLE
                    imagesInfoText.text = "Initialisation..."
                }
                loadingProgressBar.visibility = View.VISIBLE
                imagesInfoText.text = "Initialisation..."
            }

            // Fonction pour traverser le dossier
            fun traverse(file: DocumentFile, path: String = "") {
                if (file.isDirectory) {
                    val newPath = if (path.isEmpty()) file.name ?: "" else "$path/${file.name}"
                    file.listFiles()?.forEach { traverse(it, newPath) }
                } else {
                    val name = file.name ?: return
                    val fullPath = if (path.isEmpty()) name else "$path/$name"
                    if (isValidImage(file) && fullPath !in seenPaths) {
                        allImageFiles.add(file to fullPath)
                        seenPaths.add(fullPath)
                        logDebug("EditorActivity", "Image trouvée: $fullPath")
                        // Démarrer immédiatement le chargement de la première image trouvée
                        if (!firstImageLoaded) {
                            firstImageLoaded = true
                            imageFileMap[fullPath] = file
                            imageLoadingScope.launch {
                                try {
                                    val bitmap = withContext(Dispatchers.IO) {
                                        Glide.with(this@EditorActivity)
                                            .asBitmap()
                                            .load(file.uri)
                                            .apply(
                                                RequestOptions().diskCacheStrategy(
                                                    DiskCacheStrategy.ALL
                                                )
                                            )
                                            .submit()
                                            .get()
                                    }
                                    imageBitmapMap[fullPath] = bitmap
                                    if (clearData && !imageDataMap.containsKey(fullPath)) {
                                        imageDataMap[fullPath] = mutableListOf()
                                    }
                                    loadedImagesCount++
                                    withContext(Dispatchers.Main) {
                                        imageAdapter.addImage(bitmap, fullPath)
                                        updateLoadingProgress()
                                    }
                                } catch (e: Exception) {
                                    Log.e(
                                        "EditorActivity",
                                        "Erreur au chargement anticipé de la première image",
                                        e
                                    )
                                }
                            }
                        }
                    }
                }
            }

            folder.listFiles()?.forEach { traverse(it) }

            // Autres logs de validation
            Log.d("EditorActivity", "Chargement des fichiers terminé. Nombre d'images à traiter : ${allImageFiles.size}")
            Log.d("EditorActivity", "Chargement terminé. Nombre d'images chargées : $loadedImagesCount")

            // Tri par profondeur puis ordre alphabétique
            allImageFiles.sortWith(
                compareBy(
                { it.second.count { c -> c == '/' } },
                { it.second }
            ))

            totalImagesToLoad = allImageFiles.size
            loadedImagesCount = 0

            // Initialiser les maps pour les images et les zones
            if (clearData) {
                imageBitmapMap.clear()
                imageDataMap.clear()
        imageRootNode = ImageGroupNode("Racine", null, mutableListOf(), mutableListOf())
            }


            allImageFiles.sortBy { it.second }

            // Ajout du sémaphore pour limiter le nombre de chargements simultanés
            val semaphore = Semaphore(5)

            // Chargement en parallèle par lot
            val batches = allImageFiles.chunked(imagesPerBatch)
            for (batch in batches) {
                val deferreds = batch.map { (file, fullPath) ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            if (!imageBitmapMap.containsKey(fullPath)) {
                                imageFileMap[fullPath] = file
                                val bitmap = withContext(Dispatchers.IO) {
                                    Glide.with(this@EditorActivity)
                                        .asBitmap()
                                        .load(file.uri)
                                        .apply(
                                            RequestOptions().override(800, 600)
                                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        )
                                        .submit()
                                        .get()
                                }
                                imageBitmapMap[fullPath] = bitmap
                            }
                            // Ne pas initialiser imageDataMap[fullPath] ici pour ne pas écraser les zones déjà présentes
                            val bitmap = imageBitmapMap[fullPath]
                            logDebug("LoadImages", "Image chargée: $fullPath, taille: ${bitmap?.width}x${bitmap?.height}")
                            // Synchroniser le cache à chaque chargement d'image
                            withContext(Dispatchers.Main) {
                                binding.drawingView.imageBitmapMap = imageBitmapMap
                            }
                            if (debugLogs) Log.d("EditorActivity", "Image trouvée: $fullPath")
                            loadedImagesCount = minOf(loadedImagesCount + 1, totalImagesToLoad) // Incrémenter pour l'affichage du chargement, sans dépasser total
                            if (loadedImagesCount % 10 == 0) {
                                logDebug("EditorActivity", "Chargées : $loadedImagesCount / $totalImagesToLoad")
                            }
                        } catch (e: Exception) {
                            // Initialiser les zones vides uniquement en cas d'échec
                            imageDataMap[fullPath] = mutableListOf()
                            skippedFiles.add(fullPath)
                            Log.e(
                                "EditorActivity",
                                "Failed to load image: $fullPath",
                                e
                            )
                            loadedImagesCount = minOf(loadedImagesCount + 1, totalImagesToLoad) // Même si échec, on incrémente pour la barre de chargement, sans dépasser total
                        } finally {
                            semaphore.release()
                        }
                    }
                }
                deferreds.awaitAll()
                withContext(Dispatchers.Main) {
                    // Reconstruction dynamique de l’arborescence
                    imageRootNode =
                        ImageGroupTreeBuilder.buildImageGroupTree(imageBitmapMap.map { (path, bmp) -> bmp to path })
                    imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
                    updateLoadingProgress()
                }
            }

            withContext(Dispatchers.Main) {
                loadingProgressBar.visibility = View.GONE
                if (skippedFiles.isNotEmpty()) {
                    Toast.makeText(
                        this@EditorActivity,
                        "Certaines images n'ont pas été chargées.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateBottomBarInfo(isLoading = false)  // Mise à jour de la barre inférieure
                binding.bottomBar.buttonSave.isEnabled = true
                logDebug("LoadImages", "Chargement des images terminé → éditeur prêt")
            }
        }
    }


    private fun updateLoadingProgress() {
        try {
            if (!::imagesInfoText.isInitialized) return
            if (totalImagesToLoad > 0) {
                val safeLoadedCount = minOf(loadedImagesCount, totalImagesToLoad)
                val progressPercent = (loadedImagesCount * 100) / totalImagesToLoad
                loadingProgressBar.progress = progressPercent
                imagesInfoText.text = getString(R.string.loading_progress, loadedImagesCount, totalImagesToLoad)
            }
            binding.bottomBar.buttonSave.isEnabled = true
            loadingProgressBar.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("EditorActivity", "Erreur UI update: ${e.message}")
        }
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
                zones = if (zones.isEmpty()) emptyList() else zones.map { it.toZoneData() }
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
        saveZones()  // Sauvegarde automatique aussi quand l’activité passe en arrière-plan
    }

    override fun onDestroy() {
        saveZones()  // Sauvegarde automatique avant destruction
        imageBitmapMap.values.forEach { if (!it.isRecycled) it.recycle() }
        imageBitmapMap.clear()
        super.onDestroy()
        imageLoadingScope.cancel()
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
        logDebug("SyncFolder", "Fin de synchronizeFolder, mise à jour de l’UI")
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
        if (totalSize > 1_000_000_000L) { // 1 Go
            withContext(Dispatchers.Main) {
                showSnackbar("⚠ Attention : ce dossier dépasse 1 Go, risque de ralentissements ou plantage.")
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
            val previousImagePaths = imageBitmapMap.keys.toSet()

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
                imageBitmapMap.remove(path)
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
                        val bitmap = Glide.with(this@EditorActivity)
                            .asBitmap()
                            .load(file.uri)
                            .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                            .submit()
                            .get()
                        imageBitmapMap[path] = bitmap
                        imageDataMap[path] = mutableListOf()
                    } catch (e: Exception) {
                        logDebug("Sync", "Erreur chargement $path : ${e.message}")
                    }
                }
            }

            imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(
                imageBitmapMap.map { (path, bmp) -> bmp to path }
            )
            imageDataMap.forEach { (imageName, zones) ->
                logDebug("SyncFolder", "Après synchro - Image: $imageName → Zones count: ${zones.size}")
            }
            groupedImages.clear()
            groupedImages.addAll(ImageGroup.fromTree(imageRootNode))
        }

        withContext(Dispatchers.Main) {
            imageAdapter.updateData(groupedImages)
            binding.recyclerViewThumbnails.adapter = imageAdapter
            currentImageName?.let { path ->
                imageBitmapMap[path]?.let { bitmap ->
                    binding.drawingView.imageBitmap = bitmap
                    binding.drawingView.setZonesForCurrentImage(imageDataMap[path] ?: emptyList())
                }
            }
            imageAdapter.notifyDataSetChanged()
            updateBottomBarInfo()

            // Ajout calcul linkedImages et orphanImages AVANT detailedSummary
            val linkedImages = imageDataMap.flatMap { it.value }.mapNotNull { it.linkedImagePath }.toSet()
            val orphanImages = imageBitmapMap.keys.filter { image ->
                (imageDataMap[image]?.isEmpty() ?: true) && image !in linkedImages
            }

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
        }
    }

    // Lie l'image à la zone sélectionnée
    private fun linkSelectedZoneToImage(linkedImagePath: String) {
        val selectedZone = binding.drawingView.selectedZone
        if (selectedZone != null) {
            if (linkedImagePath == currentImageName) {
                showSnackbar("Impossible de lier une zone à sa propre image.")
                return
            }
            selectedZone.linkedImagePath = linkedImagePath
            binding.drawingView.selectedZone = null
            binding.drawingView.invalidate()
            hideDeleteZonesButton()
            currentImageName?.let { imageName ->
                imageDataMap[imageName] = binding.drawingView.getAllZones().toMutableList()
                val layoutManager = binding.recyclerViewThumbnails.layoutManager as LinearLayoutManager
                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                val offset = layoutManager.findViewByPosition(firstVisiblePosition)?.top ?: 0

                val imageZonesMap = imageDataMap.mapValues { entry ->
                    entry.value.map { it.toZoneData() }
                }
                imageAdapter.imageZonesMap = imageZonesMap
                val fullPath = imageName
                val index = imageAdapter.currentList.indexOfFirst { it.fullPath == fullPath }
                if (index >= 0) {
                    imageAdapter.notifyItemChanged(index)
                }
                layoutManager.scrollToPositionWithOffset(firstVisiblePosition, offset)
            }
            logDebug("LinkZone", "Zone liée: ${selectedZone.rect}, image: $linkedImagePath")
            logDebug("LinkZone", "ImageDataMap après liaison: $imageDataMap")
            logDebug("LinkZone", "ImageBitmapMap contient: ${imageBitmapMap.keys}")
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    // checkFolderConsistencyAndProceed supprimée (plus utilisée)

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

            if (currentFolderUri == null) {
                showSnackbar("Erreur : dossier manquant. Merci de le sélectionner.")
                openFolderPicker()
                return
            }

            if (!hasPersistedPermission(currentFolderUri!!)) {
                showSnackbar("Permission expirée, merci de re-sélectionner le dossier.")
                openFolderPicker()
                return
            }

            if (currentFolderUri != null) {
                logDebug("EnterEditMode", "FolderUri récupéré : $currentFolderUri")
            }

            adventureNameTextView.text = adventureData.adventureTitle
            logDebug("EnterEditMode", "Titre affiché mis à jour : ${adventureData.adventureTitle}")
            startImagePath = adventureData.startImagePath

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
            // Bloc refait pour gestion du dossier et suppression de la synchro prématurée
            if (currentFolderUri != null) {
                try {
                    val folder = DocumentFile.fromTreeUri(this, currentFolderUri!!)
                    if (folder == null) {
                        logDebug("EnterEditMode", "DocumentFile.fromTreeUri a retourné null")
                        showSnackbar("Erreur d’accès au dossier. Merci de le sélectionner à nouveau.")
                        openFolderPicker()
                        return
                    }
                    if (!hasPersistedPermission(currentFolderUri!!)) {
                        logDebug("EnterEditMode", "Permission persistante manquante")
                        showSnackbar("Permission expirée, merci de re-sélectionner le dossier.")
                        openFolderPicker()
                        return
                    }
                    if (!folder.exists()) {
                        logDebug("EnterEditMode", "Le dossier n’existe plus sur le stockage")
                        showSnackbar("Le dossier d’aventure a été supprimé ou déplacé. Merci de le sélectionner à nouveau.")
                        openFolderPicker()
                        return
                    }
                    logDebug("EnterEditMode", "Dossier accessible → chargement des images")
                    groupedImages.clear()
                    imageAdapter.updateData(emptyList())
                    requestFolderAccess(currentFolderUri!!)
                } catch (e: Exception) {
                    logDebug("EnterEditMode", "Erreur inattendue lors de l’accès au dossier: ${e.message}")
                    showSnackbar("Erreur inattendue. Merci de re-sélectionner le dossier.")
                    openFolderPicker()
                }
            } else {
                logDebug("EnterEditMode", "Pas de dossier → demander à l’utilisateur")
                showSnackbar("Veuillez sélectionner un dossier d’images.")
                openFolderPicker()
            }
            logDebug("EnterEditMode", "Mode édition prêt, synchro non encore lancée")
            // Affiche automatiquement la première image et ses zones après chargement de l'aventure
            lifecycleScope.launch(Dispatchers.Main) {
                val firstImagePath = imageDataMap.keys.firstOrNull()
                if (firstImagePath != null) {
                    // Patch: skip if bitmap not ready
                    if (!imageBitmapMap.containsKey(firstImagePath)) {
                        logDebug("EnterEditMode", "Le bitmap pour $firstImagePath n’est pas encore chargé → on saute l’affichage initial")
                        return@launch
                    }
                    onImageSelected(firstImagePath)
                }
            }
        } else {
            imageBitmapMap.values.forEach { if (!it.isRecycled) it.recycle() }
            imageBitmapMap.clear()
            logDebug("EnterEditMode", "Aventure introuvable, création d’une nouvelle aventure")
            showSnackbar("Aventure introuvable, création d’une nouvelle.")
            logDebug("EnterEditMode", "promptAdventureName() appelé car fichier introuvable")
            promptAdventureName()
        }
    }


    private fun hasPersistedPermission(uri: Uri): Boolean {
        val persistedUris = contentResolver.persistedUriPermissions
        return persistedUris.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
    }

}