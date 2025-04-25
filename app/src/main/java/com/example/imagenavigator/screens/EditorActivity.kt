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
    private lateinit var binding: ActivityEditorBinding
    private val imageDataMap = mutableMapOf<String, MutableList<com.example.imagenavigator.model.Zone>>()
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()
    private var currentImageName: String? = null
    private val groupedImages = mutableListOf<ImageGroup>()
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var imageRootNode: ImageGroupNode

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { loadImagesFromFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderPickerLauncher.launch(null)

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
            onImageSelected = { bitmap, name ->
                Log.d("EditorActivity", "Nom reçu dans callback : $name")
                currentImageName = name
                Log.d("DrawingView", "Image demandée: $currentImageName")
                Log.d("DrawingView", "Bitmap trouvé ? ${imageBitmapMap.containsKey(currentImageName)}")
                binding.drawingView.imageBitmap = imageBitmapMap[name]
                val zones = imageDataMap[name] ?: mutableListOf()
                binding.drawingView.zones.clear()
                binding.drawingView.zones.addAll(zones)
                binding.drawingView.invalidate()
            },
            onGroupRenameRequested = { updatedItem ->
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
            onGroupDeleteRequested = { itemToDelete ->

                fun removeGroupRecursively(parent: ImageGroupNode): Boolean {
                    val iterator = parent.children.iterator()
                    while (iterator.hasNext()) {
                        val child = iterator.next()
                        if (child.fullPath == itemToDelete.fullPath) {
                            iterator.remove()
                            child.images.forEach { (_, path) ->
                                Log.d("DELETE", "Supprime image $path")
                                if (imageBitmapMap.remove(path) != null) {
                                    Log.d("DELETE", "$path supprimée de imageBitmapMap")
                                }
                                if (imageDataMap.remove(path) != null) {
                                    Log.d("DELETE", "$path supprimée de imageDataMap")
                                }
                            }
                            return true
                        } else if (removeGroupRecursively(child)) {
                            return true
                        }
                    }
                    return false
                }

                removeGroupRecursively(imageRootNode)
                imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
            }
        )

        binding.recyclerViewThumbnails.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewThumbnails.setHasFixedSize(true)
        binding.recyclerViewThumbnails.adapter = imageAdapter

        // Liaison des nouveaux boutons
        val bottomBarView = binding.bottomBar.root
        val buttonImportFolder = bottomBarView.findViewById<Button>(R.id.buttonImportFolder)
        val buttonImportImage = bottomBarView.findViewById<Button>(R.id.buttonImportImage)
        val buttonSave = bottomBarView.findViewById<Button>(R.id.buttonSave)

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

    // Nouvelle version de la fonction loadImagesFromFolder avec gestion des doublons et batch optimisé
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
                if (imageBitmapMap.containsKey(fullPath)) {
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

            // Chargement batch initial
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

            // Chargement batch par batch du reste
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
