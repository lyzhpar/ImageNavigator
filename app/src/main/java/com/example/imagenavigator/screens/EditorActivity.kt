package com.example.imagenavigator.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.example.imagenavigator.model.ImageData
import com.example.imagenavigator.model.Zone
import com.example.imagenavigator.utils.ImageGroup
import com.example.imagenavigator.utils.ImageGroupTreeBuilder
import com.example.imagenavigator.utils.ImageGroupNode
import com.google.gson.GsonBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.File
import android.view.inputmethod.InputMethodManager

class EditorActivity : AppCompatActivity() {

    // --- Déclarations ---

    private lateinit var binding: ActivityEditorBinding
    private lateinit var imageAdapter: ImageAdapter

    private val groupedImages = mutableListOf<ImageGroup>()
    private val imageDataMap = mutableMapOf<String, MutableList<Zone>>()
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()
    private lateinit var imageRootNode: ImageGroupNode
    private var currentImageName: String? = null

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

    private val imageLoadingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imagesPerBatch = 10
    private var isLoadingBatch = false
    private var totalImagesToLoad = 0
    private var loadedImagesCount = 0

    private var currentFolderUri: Uri? = null


    // Demander l'accès au dossier
    private fun requestFolderAccess(uri: Uri) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        folderPickerLauncher.launch(intent)
    }

    // Sélecteur de dossier
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                // On a l'URI du dossier, on peut maintenant l'utiliser
                currentFolderUri = uri
                // Charger les images avec cette URI
                loadImagesFromFolder(uri)
            }
        }
    }

    // Quand une image est sélectionnée
    private fun onImageSelected(bitmap: Bitmap, fullPath: String) {
        // Action à faire quand l'utilisateur clique sur une image
        currentImageName = fullPath
        binding.drawingView.imageBitmap = bitmap
    }

    // Quand l'utilisateur demande de renommer un groupe
    private fun onGroupRenameRequested(updatedItem: ImageAdapter.DisplayItem.GroupItem) {
        // Tu peux afficher une boîte de dialogue pour demander un nouveau nom
        AlertDialog.Builder(this)
            .setTitle("Renommer le groupe")
            .setMessage("Renommer les groupes est à implémenter.")
            .setPositiveButton("OK", null)
            .show()
    }

    // Quand l'utilisateur demande de supprimer un groupe
    private fun onGroupDeleteRequested(itemToDelete: ImageAdapter.DisplayItem.GroupItem) {
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
        val adventureFolder = File(filesDir, "adventures") // Assurez-vous que ce dossier existe et contient les images
        val imageFile = File(adventureFolder, path)
        return if (imageFile.exists()) {
            Uri.fromFile(imageFile)
        } else {
            null
        }
    }

    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? {
        val screenSize = resources.displayMetrics.widthPixels.coerceAtLeast(resources.displayMetrics.heightPixels)
        return try {
            withContext(Dispatchers.IO) {
                Glide.with(this@EditorActivity)
                    .asBitmap()
                    .load(uri)
                    .apply(
                        RequestOptions()
                            .override(screenSize / 2)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                    )
                    .submit()
                    .get()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadAdventureData(name: String) {
        // Lire le fichier d'aventure
        val file = File(filesDir, "${name}_zones.json")
        if (file.exists()) {
            val json = file.readText()
            val adventureData = GsonBuilder().create().fromJson(json, AdventureData::class.java)

            // Mettre à jour le titre de l'aventure
            currentAdventureName = adventureData.adventureTitle
            adventureNameTextView.text = currentAdventureName

            // Charger l'URI du dossier à partir des données d'aventure
            val folderUriString = adventureData.folderUri
            if (folderUriString != null) {
                currentFolderUri = Uri.parse(folderUriString)
                // Charger les images depuis le dossier
                requestFolderAccess(currentFolderUri!!)  // Demander l'accès si l'URI est valide
            } else {
                Toast.makeText(this, "Dossier d'images non sauvegardé.", Toast.LENGTH_SHORT).show()
                return
            }

            // Charger les images depuis le fichier JSON
            val allImageFiles = adventureData.images.map { it.imageName }

            // Initialiser les maps pour les images et les zones
            imageBitmapMap.clear()
            imageDataMap.clear()

            // Charger les images dans l'interface
            imageLoadingScope.launch(Dispatchers.Main) {
                for (path in allImageFiles) {
                    val uri = getUriForImage(path)  // Fonction qui retourne l'URI de chaque image
                    if (uri != null) {
                        val bitmap = loadBitmapFromUri(uri)  // Charger l'image en bitmap
                        if (bitmap != null) {
                            imageBitmapMap[path] = bitmap
                            imageDataMap[path] = mutableListOf()  // Ajouter les zones après
                        }
                    }
                }

                // Recréer l'arbre d'images et mettre à jour l'adapter
                val allImages = imageBitmapMap.map { (path, bitmap) -> bitmap to path }
                imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(allImages)
                imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
                updateBottomBarInfo()  // Mettre à jour les informations de la barre inférieure
            }
        } else {
            // Fichier d'aventure non trouvé, demander à l'utilisateur de créer un nom
            Toast.makeText(this, "Fichier d'aventure introuvable.", Toast.LENGTH_SHORT).show()
            promptAdventureName()
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Adapter images
        imageAdapter = ImageAdapter(
            rootGroups = groupedImages,
            onImageSelected = { bitmap, fullPath -> onImageSelected(bitmap, fullPath) },
            onGroupRenameRequested = { updatedItem -> onGroupRenameRequested(updatedItem) },
            onGroupDeleteRequested = { itemToDelete -> onGroupDeleteRequested(itemToDelete) },
            onItemLongPress = { item -> toggleSelection(item.fullPath) },
            getSelectedItems = { imageAdapter.getSelectedItems() },
            exitSelectionMode = { exitSelectionMode() }
        )

        binding.recyclerViewThumbnails.apply {
            layoutManager = LinearLayoutManager(this@EditorActivity)
            setHasFixedSize(true)
            adapter = imageAdapter
        }

        // 🛠 Accès propre aux éléments du header
        adventureNameTextView = binding.headerAdventure.adventureNameTextView

        val adventureFromIntent = intent.getStringExtra("adventureName")
        if (adventureFromIntent != null) {
            currentAdventureName = adventureFromIntent
            binding.headerAdventure.adventureNameTextView.text = currentAdventureName
            loadAdventureData(adventureFromIntent) // 🆕 nouvelle fonction à créer juste en dessous
            return
        }

        // Accès aux boutons dans la BottomBar
        val buttonSaveAdventure = binding.bottomBar.buttonSaveAdventure
        val buttonRenameAdventure = binding.bottomBar.buttonRenameAdventure

        // Listeners sur les boutons
        buttonSaveAdventure.setOnClickListener { saveZones() }
        buttonRenameAdventure.setOnClickListener { showRenameAdventureDialog() }

        // Initialisation : on attend que l'utilisateur donne un nom
        promptAdventureName()

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

        // Bottom bar
        val bottomBarView = binding.bottomBar.root
        imagesInfoText = bottomBarView.findViewById(R.id.textImageCount)
        worldsInfoText = bottomBarView.findViewById(R.id.textWorldCount)
        selectedImagesCount = bottomBarView.findViewById(R.id.selectedImagesCount)
        selectedWorldsCount = bottomBarView.findViewById(R.id.selectedWorldsCount)
        selectionInfoContainer = bottomBarView.findViewById(R.id.selectionInfoContainer)

        bottomBarView.findViewById<Button>(R.id.buttonImportFolder).setOnClickListener {
            folderPickerLauncher.launch(null)
        }
        bottomBarView.findViewById<Button>(R.id.buttonImportImage).setOnClickListener {
            Toast.makeText(this, "Import d'une seule image à compléter", Toast.LENGTH_SHORT).show()
        }

        // Bouton Supprimer
        deleteButton = Button(this).apply {
            text = "Supprimer"
            visibility = View.GONE
            isEnabled = false
            setOnClickListener { handleDeleteSelectedItems(this) }
        }
        binding.bottomBar.root.addView(deleteButton)

        // Indicateur Mode sélection
        selectionModeIndicator = TextView(this).apply {
            text = "Mode sélection"
            visibility = View.GONE
            textSize = 16f
            setPadding(16, 0, 16, 0)
            setOnClickListener {
                exitSelectionMode()
                updateDeleteButtonVisibility(deleteButton)
                visibility = View.GONE
            }
        }
        binding.bottomBar.root.addView(selectionModeIndicator)

        hideSystemUI()
    }

// --- FONCTIONS UTILITAIRES ---

    private fun promptAdventureName() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nouvelle aventure")
        val input = EditText(this)
        input.hint = "Nom de l'aventure"

        input.requestFocus() // 🆕 Met le focus sur le champ

        builder.setView(input)
        builder.setCancelable(false)
        builder.setPositiveButton("Valider") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isEmpty() || adventureFileExists(name)) {
                Toast.makeText(this, "Nom invalide ou existant.", Toast.LENGTH_SHORT).show()
                promptAdventureName()
            } else {
                currentAdventureName = name
                adventureNameTextView.text = currentAdventureName
                folderPickerLauncher.launch(null)
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            // 🆕 Force aussi l'ouverture du clavier au bon moment
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }

    private fun adventureFileExists(name: String): Boolean {
        val file = File(filesDir, "${name}_zones.json")
        return file.exists()
    }

    private fun saveZones() {

        val adventureData = generateAdventureData()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(adventureData)
        val file = File(filesDir, "${currentAdventureName}_zones.json")
        file.writeText(json)

        Snackbar.make(findViewById(android.R.id.content),
            "Aventure sauvegardée : $currentAdventureName",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showRenameAdventureDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Renommer l'aventure")
        val input = EditText(this)
        input.hint = "Nouveau nom"

        input.requestFocus() // 🆕 Focus automatique sur le champ

        builder.setView(input)
        builder.setPositiveButton("Renommer") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isEmpty() || adventureFileExists(newName)) {
                Toast.makeText(this, "Nom invalide ou existant.", Toast.LENGTH_SHORT).show()
            } else {
                renameAdventure(newName)
            }
        }
        builder.setNegativeButton("Annuler", null)

        val dialog = builder.create()

        dialog.setOnShowListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }

    private fun renameAdventure(newName: String) {
        val oldFile = File(filesDir, "${currentAdventureName}_zones.json")
        val newFile = File(filesDir, "${newName}_zones.json")

        if (oldFile.exists()) oldFile.renameTo(newFile)
        currentAdventureName = newName
        adventureNameTextView.text = newName
        Toast.makeText(this, "Aventure renommée en $newName", Toast.LENGTH_SHORT).show()
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

    private fun handleDeleteSelectedItems(deleteButton: View? = null) {
        val itemsToDelete = selectedItems.toList()
        for (fullPath in itemsToDelete) {
            if (isGroupPath(fullPath)) removeGroupAndImages(fullPath)
            else removeImage(fullPath)
        }
        selectedItems.clear()
        exitSelectionMode()
        imageAdapter.notifyDataSetChanged()
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
            selectedImagesCount.text = "Images : $images"
            selectedWorldsCount.text = "Dossiers : $folders"
            imagesInfoText.visibility = View.GONE
            worldsInfoText.visibility = View.GONE
        } else {
            selectionInfoContainer.isVisible = false
            imagesInfoText.visibility = View.VISIBLE
            worldsInfoText.visibility = View.VISIBLE
            if (isLoading) {
                imagesInfoText.text = "Chargement : ${imageBitmapMap.size} images"
            } else {
                imagesInfoText.text = "Images : ${imageBitmapMap.size}"
            }
        }
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
        imageBitmapMap.remove(fullPath)
        imageDataMap.remove(fullPath)
        removeImageFromNode(imageRootNode, fullPath)
    }

    private fun removeImageFromNode(node: ImageGroupNode, fullPath: String) {
        node.images.removeAll { it.second == fullPath }
        node.children.forEach { removeImageFromNode(it, fullPath) }
    }

    private fun loadImagesFromFolder(uri: Uri) {
        Log.d("EditorActivity", "Loading images from folder: $uri")
        val skippedFiles = mutableListOf<String>()
        binding.loadingOverlay.isVisible = true
        imageLoadingScope.launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val allImageFiles = mutableListOf<Pair<DocumentFile, String>>()
            val seenPaths = mutableSetOf<String>()

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
                    }
                }
            }

            folder.listFiles()?.forEach { traverse(it) }
            allImageFiles.sortBy { it.second }

            totalImagesToLoad = allImageFiles.size
            loadedImagesCount = 0

            // Initialiser les maps pour les images et les zones
            imageBitmapMap.clear()
            imageDataMap.clear()

            // Charger les images en batch
            val batches = allImageFiles.chunked(imagesPerBatch)
            for (batch in batches) {
                batch.forEach { (file, fullPath) ->
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            Glide.with(this@EditorActivity)
                                .asBitmap()
                                .load(file.uri)
                                .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                                .submit()
                                .get()
                        }
                        imageBitmapMap[fullPath] = bitmap
                        imageDataMap[fullPath] = mutableListOf()  // Initialiser les zones vides
                    } catch (e: Exception) {
                        skippedFiles.add(fullPath)
                    }
                }
                withContext(Dispatchers.Main) {
                    val allImages = imageBitmapMap.map { (path, bitmap) -> bitmap to path }
                    imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(allImages)
                    imageAdapter.updateData(ImageGroup.fromTree(imageRootNode)) // Mettre à jour l'adaptateur
                    updateLoadingProgress() // Mettre à jour le pourcentage de chargement
                }
            }

            withContext(Dispatchers.Main) {
                binding.loadingOverlay.isVisible = false
                if (skippedFiles.isNotEmpty()) {
                    Toast.makeText(this@EditorActivity, "Certaines images n'ont pas été chargées.", Toast.LENGTH_SHORT).show()
                }
                updateBottomBarInfo(isLoading = false)  // Mise à jour de la barre inférieure
            }
        }
    }


    // 🆕 Nouvelle fonction :
    private fun updateLoadingProgress() {
        imagesInfoText.text = "Chargement : $loadedImagesCount/$totalImagesToLoad images"
    }
    private fun isValidImage(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val validExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        val ext = name.substringAfterLast('.', "").lowercase()
        val mimeType = contentResolver.getType(file.uri)
        return (mimeType?.startsWith("image/") == true) && ext in validExtensions
    }

    private fun setupRecyclerViewLazyLoading() {
        binding.recyclerViewThumbnails.clearOnScrollListeners()
        binding.recyclerViewThumbnails.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (totalItemCount - lastVisibleItem <= 3) {
                    loadNextImageBatchIfNeeded()
                }
            }
        })
    }

    private fun loadNextImageBatchIfNeeded() {
        if (isLoadingBatch) return
        isLoadingBatch = true
        // Future extension possible
        isLoadingBatch = false
    }

    private fun generateAdventureData(): AdventureData {
        val imagesList = imageDataMap.map { (path, zones) ->
            ImageData(imageName = path, zones = zones)
        }
        return AdventureData(
            adventureTitle = currentAdventureName,
            images = imagesList,
            folderUri = currentFolderUri?.toString()
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

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }}