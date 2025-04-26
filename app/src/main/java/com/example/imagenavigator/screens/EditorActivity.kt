package com.example.imagenavigator.screens

import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imagenavigator.adapters.ImageAdapter
import com.example.imagenavigator.model.DisplayItem
import com.example.imagenavigator.databinding.ActivityEditorBinding
import com.example.imagenavigator.utils.ImageGroup
import com.example.imagenavigator.utils.ImageGroupTreeBuilder
import com.example.imagenavigator.utils.ImageGroupNode
import kotlinx.coroutines.*
import android.graphics.BitmapFactory
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Button
import com.example.imagenavigator.R



class EditorActivity : AppCompatActivity() {
    // Liste des éléments sélectionnés (images et dossiers)
    private val selectedItems = mutableSetOf<String>() // Utilise le fullPath comme identifiant unique
    private var isSelectionMode = false
    private lateinit var binding: ActivityEditorBinding
    private val imageDataMap = mutableMapOf<String, MutableList<com.example.imagenavigator.model.Zone>>()
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()
    private var currentImageName: String? = null
    private val groupedImages = mutableListOf<ImageGroup>()
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var imageRootNode: ImageGroupNode
    private lateinit var deleteButton: Button


    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { loadImagesFromFolder(it) }
    }

    // Ajoute ou enlève un élément de la sélection (utilise le set local)
    private fun toggleSelection(fullPath: String) {
        if (selectedItems.contains(fullPath)) {
            selectedItems.remove(fullPath)
            Log.d("EditorActivity", "Désélectionné : $fullPath")
        } else {
            selectedItems.add(fullPath)
            Log.d("EditorActivity", "Sélectionné : $fullPath")
        }
        imageAdapter.setSelectionMode(isSelectionMode, selectedItems)
    }

    // Affiche ou masque le bouton "Supprimer" selon la sélection
    private fun updateDeleteButtonVisibility(deleteButton: View) {
        Log.d("EditorActivity", "updateDeleteButtonVisibility: selectedItems=${selectedItems}")
        deleteButton.visibility = if (selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
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
    }


    // Sort du mode sélection multiple et réinitialise la sélection
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        imageAdapter.setSelectionMode(false, selectedItems)
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

    // (Suppression de la version dupliquée de loadImagesFromFolder)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Rendre la DrawingView cliquable pour capter les long press
        binding.drawingView.isClickable = true

        // Bouton dynamique "Supprimer" (créé ici, caché par défaut)
        deleteButton = Button(this).apply {
            text = "Supprimer"
            visibility = View.GONE
            isEnabled = false
            setOnClickListener {
                handleDeleteSelectedItems(this)
            }
        }

        // Ajoute le bouton à la bottom bar (ou autre layout approprié)
        binding.bottomBar.root.addView(deleteButton)

        binding.adventureNameTextView.setOnLongClickListener {
            binding.adventureNameTextView.visibility = View.GONE
            binding.adventureTitleEdit.visibility = View.VISIBLE
            binding.adventureTitleEdit.setText(binding.adventureNameTextView.text)
            binding.adventureTitleEdit.requestFocus()
            binding.adventureTitleEdit.setSelection(binding.adventureTitleEdit.text.length)
            true
        }

        binding.adventureTitleEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.adventureNameTextView.text = binding.adventureTitleEdit.text.toString().trim()
                binding.adventureNameTextView.visibility = View.VISIBLE
                binding.adventureTitleEdit.visibility = View.GONE
                true
            } else {
                false
            }
        }

        binding.adventureTitleEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.adventureNameTextView.text = binding.adventureTitleEdit.text.toString().trim()
                binding.adventureNameTextView.visibility = View.VISIBLE
                binding.adventureTitleEdit.visibility = View.GONE
            }
        }

        hideSystemUI()

        imageAdapter = ImageAdapter(
            rootGroups = groupedImages,
            onImageSelected = { bitmap: Bitmap, fullPath: String ->
                if (isSelectionMode) {
                    // Sélection/désélection en mode sélection multiple
                    toggleSelection(fullPath)
                    updateDeleteButtonVisibility(deleteButton)
                    deleteButton.isEnabled = selectedItems.isNotEmpty()
                } else {
                    // Sélection individuelle
                    Log.d("EditorActivity", "Nom reçu dans callback : $fullPath")
                    currentImageName = fullPath
                    Log.d("DrawingView", "Image demandée: $currentImageName")
                    Log.d("DrawingView", "Bitmap trouvé ? ${imageBitmapMap.containsKey(currentImageName)}")
                    binding.drawingView.imageBitmap = bitmap // Assigner le bitmap directement ici
                    val zones = imageDataMap[fullPath] ?: mutableListOf()
                    binding.drawingView.zones.clear()
                    binding.drawingView.zones.addAll(zones)
                    binding.drawingView.invalidate()
                }
            },
            onGroupRenameRequested = { updatedItem: ImageAdapter.DisplayItem.GroupItem ->
                fun updateGroupName(node: ImageGroupNode) {
                    if (node.fullPath == updatedItem.fullPath) {
                        node.name = updatedItem.name
                    } else {
                        node.children.forEach { updateGroupName(it) }
                    }
                }
                fun sortNodeRecursively(node: ImageGroupNode) {
                    node.children.sortBy { it.name }
                    node.children.forEach { sortNodeRecursively(it) }
                    node.images.sortBy { it.second }
                }
                updateGroupName(imageRootNode)
                sortNodeRecursively(imageRootNode)
                imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
            },
            onGroupDeleteRequested = { itemToDelete: ImageAdapter.DisplayItem.GroupItem ->
                if (isSelectionMode) return@ImageAdapter
                removeGroupAndImages(itemToDelete.fullPath)
                imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
            },
            onItemLongPress = { item: ImageAdapter.DisplayItem ->
                val fullPath = item.fullPath
                if (!isSelectionMode) {
                    // Si le mode sélection n'est pas encore activé, l'activer
                    isSelectionMode = true
                    selectedItems.clear()  // On vide les précédentes sélections
                    selectedItems.add(fullPath)

                } else {
                    // Si le mode sélection est déjà activé, on sélectionne ou désélectionne l'élément
                    if (selectedItems.contains(fullPath)) {
                        selectedItems.remove(fullPath)  // Désélectionner l'élément
                    } else {
                        selectedItems.add(fullPath)  // Sélectionner l'élément
                    }
                    if (selectedItems.isEmpty()) {
                        isSelectionMode = false
                    }
                }
                imageAdapter.setSelectionMode(isSelectionMode, selectedItems)
                updateDeleteButtonVisibility(deleteButton)
            },
            getSelectedItems = { imageAdapter.getSelectedItems() },
            exitSelectionMode = { exitSelectionMode() }
        )

        // Désactive le mode sélection multiple si clic en dehors (exemple : sur la DrawingView)
        binding.drawingView.setOnLongClickListener {
            Log.d("EditorActivity", "Long click capté sur DrawingView")
            if (isSelectionMode) {
                exitSelectionMode()
                updateDeleteButtonVisibility(deleteButton)
                deleteButton.isEnabled = false
            }
            true
        }

        binding.recyclerViewThumbnails.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewThumbnails.setHasFixedSize(true)
        binding.recyclerViewThumbnails.adapter = imageAdapter


        // Liaison des nouveaux boutons
        val bottomBarView = binding.bottomBar.root
        val buttonImportFolder = bottomBarView.findViewById<Button>(R.id.buttonImportFolder)
        val buttonImportImage = bottomBarView.findViewById<Button>(R.id.buttonImportImage)
        val buttonSave = bottomBarView.findViewById<Button>(R.id.buttonSave)


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

        buttonSave.setOnClickListener {
            // À compléter : code pour sauvegarder les données
        }

        // Réinitialiser uniquement au premier chargement
        groupedImages.clear()
        imageBitmapMap.clear()
        imageDataMap.clear()
        // folderPickerLauncher.launch(null)
    }

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
}

