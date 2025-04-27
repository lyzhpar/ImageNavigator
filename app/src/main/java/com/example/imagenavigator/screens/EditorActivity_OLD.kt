package com.example.imagenavigator.screens

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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

class EditorActivity_OLD : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var imageAdapter: ImageAdapter

    private val groupedImages = mutableListOf<ImageGroup>()
    private val imageDataMap = mutableMapOf<String, MutableList<Zone>>()
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()
    private lateinit var imageRootNode: ImageGroupNode
    private var currentImageName: String? = null

    // Variables aventure
    private lateinit var adventureNameTextView: TextView
    private lateinit var buttonSaveAdventure: ImageButton
    private lateinit var buttonRenameAdventure: ImageButton
    private var currentAdventureName: String = ""

    // Sélection multiple
    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false
    private lateinit var deleteButton: Button
    private lateinit var selectionModeIndicator: TextView

    // Infos bottom bar
    private lateinit var imagesInfoText: TextView
    private lateinit var worldsInfoText: TextView
    private lateinit var selectedImagesCount: TextView
    private lateinit var selectedWorldsCount: TextView
    private lateinit var selectionInfoContainer: View

    // Lazy loading
    private val imageLoadingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadedImagesCount = 0
    private val imagesPerBatch = 10
    private var isLoadingBatch = false

    // Sélecteur de dossier
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { loadImagesFromFolder(it) }
    }

    private val imageLoadingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Quand une image est sélectionnée
    private fun onImageSelected(bitmap: Bitmap, fullPath: String) {
        // Action à faire quand l'utilisateur clique sur une image
        currentImageName = fullPath
        binding.drawingView.setImageBitmap(bitmap)
    }

    // Quand l'utilisateur demande de renommer un groupe
    private fun onGroupRenameRequested(updatedItem: ImageGroup) {
        // Tu peux afficher une boîte de dialogue pour demander un nouveau nom
        AlertDialog.Builder(this)
            .setTitle("Renommer le groupe")
            .setMessage("Renommer les groupes est à implémenter.")
            .setPositiveButton("OK", null)
            .show()
    }

    // Quand l'utilisateur demande de supprimer un groupe
    private fun onGroupDeleteRequested(itemToDelete: ImageGroup) {
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



    // Ajoute ou enlève un élément de la sélection (utilise le set local)
    private fun toggleSelection(fullPath: String) {
        if (!isSelectionMode) {
            isSelectionMode = true
            selectedItems.clear()  // Quand on entre en sélection, on vide l’ancienne
        }

        if (selectedItems.contains(fullPath)) {
            selectedItems.remove(fullPath)
            Log.d("EditorActivity", "Désélectionné : $fullPath")
        } else {
            selectedItems.add(fullPath)
            Log.d("EditorActivity", "Sélectionné : $fullPath")
        }

        imageAdapter.setSelectionMode(isSelectionMode, selectedItems)
        updateDeleteButtonVisibility(deleteButton)
        deleteButton.isEnabled = selectedItems.isNotEmpty()
        updateBottomBarInfo()
    }

    // Affiche ou masque le bouton "Supprimer" selon la sélection
    private fun updateDeleteButtonVisibility(deleteButton: View) {
        Log.d("EditorActivity", "updateDeleteButtonVisibility: selectedItems=${selectedItems}")
        deleteButton.visibility = if (selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
        deleteButton.isEnabled = selectedItems.isNotEmpty()


        // MAJ du texte "Mode sélection"
        selectionModeIndicator.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
    }

    // Supprime tous les éléments sélectionnés (dossiers et images)
    private fun handleDeleteSelectedItems(deleteButton: View? = null) {
        val itemsToDelete: List<String> = selectedItems.toList()
        Log.d("EditorActivity", "handleDeleteSelectedItems: $itemsToDelete")
        for (fullPath in itemsToDelete) {
            if (isGroupPath(fullPath)) {
                Log.d("EditorActivity", "Suppression d'un groupe: $fullPath")
                removeGroupAndImages(fullPath)
            } else {
                Log.d("EditorActivity", "Suppression d'une image: $fullPath")
                removeImage(fullPath)
            }
        }
        selectedItems.clear()
        exitSelectionMode()
        imageAdapter.notifyDataSetChanged()
        if (deleteButton != null) updateDeleteButtonVisibility(deleteButton)
        updateBottomBarInfo()
    }


    // Sort du mode sélection multiple et réinitialise la sélection
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        imageAdapter.setSelectionMode(false, selectedItems)
        updateBottomBarInfo()
    }
    // --- Bottom bar info update logic
    private fun updateBottomBarInfo() {
        if (!::imagesInfoText.isInitialized || !::worldsInfoText.isInitialized ||
            !::selectedImagesCount.isInitialized || !::selectedWorldsCount.isInitialized ||
            !::selectionInfoContainer.isInitialized
        ) return
        if (isSelectionMode) {
            val imageCount = selectedItems.count { !isGroupPath(it) }
            val folderCount = selectedItems.count { isGroupPath(it) }
            selectionInfoContainer.visibility = View.VISIBLE
            selectedImagesCount.text = if (imageCount == 1) "Image sélectionnée : 1" else "Images sélectionnées : $imageCount"
            selectedWorldsCount.text = if (folderCount == 1) "Dossier sélectionné : 1" else "Dossiers sélectionnés : $folderCount"
            imagesInfoText.visibility = View.GONE
            worldsInfoText.visibility = View.GONE
        } else {
            selectionInfoContainer.visibility = View.GONE
            val totalImages = imageBitmapMap.size
            val totalWorlds = countTotalGroups(imageRootNode)
            imagesInfoText.visibility = View.VISIBLE
            worldsInfoText.visibility = View.VISIBLE
            imagesInfoText.text = "Images : $totalImages"
            worldsInfoText.text = "Mondes : $totalWorlds"
        }
    }

    // Helper pour compter les groupes (mondes)
    private fun countTotalGroups(node: ImageGroupNode, isRoot: Boolean = true): Int {
        var count = 0
        for (child in node.children) {
            if (child.images.isNotEmpty() || child.children.isNotEmpty()) {
                count += 1
                count += countTotalGroups(child, isRoot = false)
            }
        }
        return count
    }

    // Retourne true si le fullPath correspond à un dossier dans l'arbre
    private fun isGroupPath(fullPath: String): Boolean {
        fun findNode(node: ImageGroupNode): Boolean {
            if (node.fullPath == fullPath) return true
            return node.children.any { findNode(it) }
        }
        return findNode(imageRootNode)
    }

    // Supprime un groupe (dossier) et ses images/sous-dossiers
    private fun removeGroupAndImages(fullPath: String) {
        Log.d("EditorActivity", "removeGroupAndImages appelé avec fullPath=$fullPath")
        fun removeGroupRecursively(parent: ImageGroupNode): Boolean {
            val iterator = parent.children.iterator()
            while (iterator.hasNext()) {
                val child = iterator.next()
                if (child.fullPath == fullPath) {
                    fun removeImagesAndSubgroups(node: ImageGroupNode) {
                        node.images.forEach { (_, path) ->
                            imageBitmapMap.remove(path)
                            imageDataMap.remove(path)
                        }
                        node.children.forEach { removeImagesAndSubgroups(it) }
                    }

                    iterator.remove()
                    return true
                } else if (removeGroupRecursively(child)) {
                    return true
                }
            }
            return false
        }
        removeGroupRecursively(imageRootNode)
    }

    // Supprime une image unique
    private fun removeImage(fullPath: String) {
        Log.d("EditorActivity", "removeImage appelé avec fullPath=$fullPath")
        imageBitmapMap.remove(fullPath)
        imageDataMap.remove(fullPath)
        removeImageFromNode(imageRootNode, fullPath)
    }

    // Déplacée hors de removeImage pour usage global
    private fun removeImageFromNode(node: ImageGroupNode, fullPath: String) {
        node.images.removeAll { it.second == fullPath }
        node.children.forEach { removeImageFromNode(it, fullPath) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Header aventure
        adventureNameTextView = binding.adventureNameTextView
        buttonSaveAdventure = binding.buttonSaveAdventure
        buttonRenameAdventure = binding.buttonRenameAdventure

        buttonSaveAdventure.setOnClickListener { saveZones() }
        buttonRenameAdventure.setOnClickListener { showRenameAdventureDialog() }

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

        // DeleteButton pour la sélection multiple
        deleteButton = Button(this).apply {
            text = "Supprimer"
            visibility = View.GONE
            isEnabled = false
            setOnClickListener { handleDeleteSelectedItems(this) }
        }
        binding.bottomBar.root.addView(deleteButton)

        // Indicateur mode sélection
        selectionModeIndicator = TextView(this).apply {
            text = "Mode sélection"
            visibility = View.GONE
            textSize = 16f
            setPadding(16, 0, 16, 0)
            setOnClickListener {
                exitSelectionMode()
                updateDeleteButtonVisibility(deleteButton)
            }
        }
        binding.bottomBar.root.addView(selectionModeIndicator)

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
            layoutManager = LinearLayoutManager(this@EditorActivity_OLD)
            setHasFixedSize(true)
            adapter = imageAdapter
        }

        // Liaison des vues de la bottom bar
        val bottomBarView = binding.bottomBar.root
        imagesInfoText = bottomBarView.findViewById(R.id.textImageCount)
        worldsInfoText = bottomBarView.findViewById(R.id.textWorldCount)
        selectedImagesCount = bottomBarView.findViewById(R.id.selectedImagesCount)
        selectedWorldsCount = bottomBarView.findViewById(R.id.selectedWorldsCount)
        selectionInfoContainer = bottomBarView.findViewById(R.id.selectionInfoContainer)

        // Boutons importation
        bottomBarView.findViewById<Button>(R.id.buttonImportFolder).setOnClickListener {
            folderPickerLauncher.launch(null)
        }
        bottomBarView.findViewById<Button>(R.id.buttonImportImage).setOnClickListener {
            Toast.makeText(this, "Import d'une seule image à compléter", Toast.LENGTH_SHORT).show()
        }

        // Listener d'appui long pour RecyclerView (debug)
        binding.recyclerViewThumbnails.setOnLongClickListener {
            Log.d("EditorActivity", "Appui long détecté sur RecyclerView")
            true
        }

        // Préparation initiale
        groupedImages.clear()
        imageBitmapMap.clear()
        imageDataMap.clear()

        // Lancement prompt pour nom d'aventure
        promptAdventureName()

        hideSystemUI()
    }



    private fun promptAdventureName() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nouvelle aventure")

        val input = EditText(this)
        input.hint = "Nom de l'aventure"
        builder.setView(input)

        builder.setCancelable(false)

        builder.setPositiveButton("Valider") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Le nom ne peut pas être vide.", Toast.LENGTH_SHORT).show()
                promptAdventureName()
            } else if (adventureFileExists(name)) {
                Toast.makeText(this, "Ce nom existe déjà, choisis un autre.", Toast.LENGTH_SHORT).show()
                promptAdventureName()
            } else {
                currentAdventureName = name
                adventureNameTextView.text = currentAdventureName
                folderPickerLauncher.launch(null)
            }
        }

        builder.show()
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
            "Aventure sauvegardée : ${currentAdventureName}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showRenameAdventureDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Renommer l'aventure")

        val input = EditText(this)
        input.hint = "Nouveau nom de l'aventure"
        builder.setView(input)

        builder.setPositiveButton("Renommer") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "Le nom ne peut pas être vide.", Toast.LENGTH_SHORT).show()
            } else if (adventureFileExists(newName)) {
                Toast.makeText(this, "Ce nom existe déjà.", Toast.LENGTH_SHORT).show()
            } else {
                renameAdventure(newName)
            }
        }

        builder.setNegativeButton("Annuler", null)

        builder.show()
    }

    private fun renameAdventure(newName: String) {
        val oldFile = File(filesDir, "${currentAdventureName}_zones.json")
        val newFile = File(filesDir, "${newName}_zones.json")
        if (oldFile.exists()) oldFile.renameTo(newFile)

        currentAdventureName = newName
        adventureNameTextView.text = newName
        Toast.makeText(this, "Aventure renommée en $newName", Toast.LENGTH_SHORT).show()
    }



        // Ajoute un onTapListener pour désactiver le mode sélection si tap sur la DrawingView
        binding.drawingView.onTapListener = {
            if (isSelectionMode) {
                exitSelectionMode()
                updateDeleteButtonVisibility(deleteButton)
                updateBottomBarInfo()
                deleteButton.isEnabled = false
            }
        }

        binding.recyclerViewThumbnails.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewThumbnails.setHasFixedSize(true)
        binding.recyclerViewThumbnails.adapter = imageAdapter


        // Liaison des nouveaux boutons
        val bottomBarView = binding.bottomBar.root
        val buttonImportFolder = bottomBarView.findViewById<Button>(R.id.buttonImportFolder)
        val buttonImportImage = bottomBarView.findViewById<Button>(R.id.buttonImportImage)

        // --- Liaison des vues info bottom bar
        imagesInfoText = bottomBarView.findViewById(R.id.textImageCount)
        worldsInfoText = bottomBarView.findViewById(R.id.textWorldCount)
        selectedImagesCount = bottomBarView.findViewById(R.id.selectedImagesCount)
        selectedWorldsCount = bottomBarView.findViewById(R.id.selectedWorldsCount)
        selectionInfoContainer = bottomBarView.findViewById(R.id.selectionInfoContainer)

        // Afficher le nombre d'images et de mondes dès le début
        // (Suppression de l'appel direct à updateBottomBarInfo ici pour éviter le crash si imageRootNode n'est pas encore initialisé)


        // Ajout du listener d'appui long dans le RecyclerView
        binding.recyclerViewThumbnails.setOnLongClickListener {
            Log.d("EditorActivity", "Appui long détecté sur RecyclerView")
            true
        }






        buttonImportFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        buttonImportImage.setOnClickListener {
            // À compléter : code pour importer une seule image
            // Suggestion : ouvrir un sélecteur de document pour choisir une image
            // (À compléter) : lancer un ActivityResultContracts.OpenDocument
            AlertDialog.Builder(this)
                .setTitle("Import d'image")
                .setMessage("Fonction d'import d'une seule image à compléter.")
                .setPositiveButton("OK", null)
                .show()
        }




            val adventureData = generateAdventureData()
            Log.d("DEBUG_SAVE", "✅ AdventureData généré avec titre = ${adventureData.adventureTitle}")

            saveAdventureToFileWithCheck(adventureData)
        }

        // Réinitialiser uniquement au premier chargement
        groupedImages.clear()
        imageBitmapMap.clear()
        imageDataMap.clear()
        // folderPickerLauncher.launch(null)
    }

    /*// Fonction pour forcer la mise à jour du titre d'aventure si l'utilisateur est en train d'éditer
    private fun updateAdventureTitleIfNeeded() {
        if (binding.adventureTitleEdit.visibility == View.VISIBLE) {
            val newTitle = binding.adventureTitleEdit.text.toString().trim()
            binding.adventureNameTextView.text = newTitle
            binding.adventureNameTextView.visibility = View.VISIBLE
            binding.adventureTitleEdit.visibility = View.GONE
        }
    }*/

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
    }


    // Fonction centralisée pour valider qu'un DocumentFile est une image (MIME type ET extension), version concise et optimale
    private fun isValidImage(file: DocumentFile): Boolean {
        val name = file.name?.lowercase() ?: return false
        val validExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        val ext = name.substringAfterLast('.', "").lowercase()
        val mimeType = contentResolver.getType(file.uri)
        return (mimeType?.startsWith("image/") == true) && ext in validExtensions
    }

    // Fonction principale pour charger les images depuis un dossier avec lazy loading et Glide asynchrone
    private val imageLoadingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadedImagesCount = 0
    private val imagesPerBatch = 10 // Nombre d'images à charger à la fois pour lazy loading

    // Nouvelle version de la fonction loadImagesFromFolder avec gestion des doublons, async et lazy loading
    private fun loadImagesFromFolder(uri: Uri) {
        Log.d("DEBUG", "Chargement du dossier : $uri")

        val skippedFiles = mutableListOf<String>()
        binding.loadingOverlay.isVisible = true

        imageLoadingScope.launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val allImageFiles = mutableListOf<Pair<DocumentFile, String>>() // (file, fullPath)
            val seenFullPaths = mutableSetOf<String>()

            // Parcours récursif pour collecter tous les fichiers images valides sans doublons
            fun traverse(file: DocumentFile, currentPath: String = "") {
                if (file.isDirectory) {
                    val newPath = if (currentPath.isEmpty()) file.name ?: "" else "$currentPath/${file.name}"
                    file.listFiles().forEach { traverse(it, newPath) }
                } else {
                    val name = file.name ?: return
                    val fullPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    if (isValidImage(file)) {
                        if (!seenFullPaths.contains(fullPath)) {
                            allImageFiles.add(Pair(file, fullPath))
                            seenFullPaths.add(fullPath)
                        } else {
                            skippedFiles.add("$fullPath : ignorée (doublon de chemin)")
                            Log.d("DEBUG", "Image ignorée (doublon de chemin): $fullPath")
                        }
                    } else {
                        skippedFiles.add("$fullPath : type MIME non image ou extension incorrecte")
                        Log.d("DEBUG", "Fichier ignoré (type MIME non image ou extension incorrecte): $fullPath")
                    }
                }
            }
            folder.listFiles().forEach { traverse(it) }

            allImageFiles.sortBy { it.second }

            val initialBatch = allImageFiles.take(imagesPerBatch)
            val remainingBatches = allImageFiles.drop(imagesPerBatch)

            loadedImagesCount = 0
            // Suppression des clear() pour conserver les images déjà chargées
            // imageBitmapMap.clear()
            // imageDataMap.clear()

            // Fonction refactorisée et claire pour charger une image, avec gestion d'erreur explicite
            suspend fun loadImageFile(pair: Pair<DocumentFile, String>) {
                val (file, fullPath) = pair
                // Ne pas écraser si déjà présente
                if (imageBitmapMap.containsKey(fullPath) || imageDataMap.containsKey(fullPath)) {
                    skippedFiles.add("$fullPath : déjà chargée, ignorée")
                    Log.d("DEBUG", "Image ignorée (déjà chargée dans map): $fullPath")
                    return
                }
                try {
                    val inputStreamCheck = contentResolver.openInputStream(file.uri)
                    inputStreamCheck.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        if (options.outWidth <= 0 || options.outHeight <= 0) {
                            skippedFiles.add("$fullPath : dimensions invalides")
                            Log.d("DEBUG", "Image ignorée (dimensions invalides): $fullPath")
                            return
                        }
                    }
                    val bitmap = withContext(Dispatchers.IO) {
                        Glide.with(this@EditorActivity)
                            .asBitmap()
                            .load(file.uri)
                            .apply(RequestOptions().override(800, 600))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .submit()
                            .get()
                    }
                    imageBitmapMap[fullPath] = bitmap
                    imageDataMap[fullPath] = mutableListOf()
                    Log.d("DEBUG", "Image ajoutée : $fullPath")
                } catch (e: Exception) {
                    Log.e("ImageLoading", "Erreur lors du traitement de l'image : $fullPath", e)
                    skippedFiles.add("$fullPath : erreur lors du chargement")
                    Log.d("DEBUG", "Image ignorée (erreur lors du chargement): $fullPath")
                }
            }

            // Chargement batch initial avec affichage progressif (async/lazy)
            val initialJobs = initialBatch.map { pair ->
                async {
                    loadImageFile(pair)
                    withContext(Dispatchers.Main) {
                        loadedImagesCount++
                        val allImages = imageBitmapMap.map { (path, bitmap) -> bitmap to path }
                        imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(allImages)
                        imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
                        updateBottomBarInfo()
                    }
                }
            }
            initialJobs.awaitAll()

            // Chargement batch par batch du reste, lazy: affiche au fur et à mesure
            for (batch in remainingBatches.chunked(imagesPerBatch)) {
                val jobs = batch.map { pair ->
                    async {
                        loadImageFile(pair)
                    }
                }
                jobs.awaitAll()
                withContext(Dispatchers.Main) {
                    loadedImagesCount += batch.size
                    val allImages = imageBitmapMap.map { (path, bitmap) -> bitmap to path }
                    imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(allImages)
                    imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
                    updateBottomBarInfo()
                }
            }

            withContext(Dispatchers.Main) {
                binding.loadingOverlay.isVisible = false
                if (skippedFiles.isNotEmpty()) {
                    AlertDialog.Builder(this@EditorActivity)
                        .setTitle("Fichiers ignorés")
                        .setMessage(skippedFiles.joinToString("\n"))
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
        // Ajout du lazy loading sur le RecyclerView
        setupRecyclerViewLazyLoading()
    }

    // Ajoute un OnScrollListener pour charger plus d'images quand on atteint la fin de la liste (lazy loading)
    private fun setupRecyclerViewLazyLoading() {
        binding.recyclerViewThumbnails.clearOnScrollListeners()
        binding.recyclerViewThumbnails.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                // Si on approche de la fin, charger le prochain batch
                if (totalItemCount - lastVisibleItemPosition <= 3) {
                    loadNextImageBatchIfNeeded()
                }
            }
        })
    }

    // Gère le chargement paresseux (lazy loading) des images suivantes
    private var isLoadingBatch = false
    private fun loadNextImageBatchIfNeeded() {
        if (isLoadingBatch) return
        isLoadingBatch = true
        // Ici, l'implémentation est un hook pour extension future si lazy loading plus fin.
        isLoadingBatch = false
    }



    // 1. Génère l'objet AdventureData à partir des données existantes
// 1. Génère l'objet AdventureData à partir des données existantes
    private fun generateAdventureData(): AdventureData {
        val imagesList = imageDataMap.map { (fullPath: String, zones: MutableList<com.example.imagenavigator.model.Zone>) ->
            ImageData(
                imageName = fullPath,
                zones = zones
            )
        }
        return AdventureData(
            adventureTitle = currentAdventureName,
            images = imagesList
        )
    }

    }
