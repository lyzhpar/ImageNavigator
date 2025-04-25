package com.example.imagenavigator.screens

import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.request.transition.Transition
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
import java.io.InputStream
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Button
import com.example.imagenavigator.R
import android.content.Context

// Fonction utilitaire pour charger une miniature avec Glide
private fun loadThumbnail(imageView: ImageView, imageUri: Uri) {
    Log.d("ImageLoading", "Chargement de la miniature pour l'image $imageUri")
    Glide.with(imageView.context)
        .load(imageUri)
        .apply(RequestOptions().override(200, 200))
        .placeholder(R.drawable.placeholder)
        .error(R.drawable.error_image)
        .into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                imageView.setImageDrawable(resource)
                Log.d("ImageLoading", "Miniature chargée avec succès pour : $imageUri")
            }
            override fun onLoadCleared(placeholder: Drawable?) {
                // Rien à faire ici
            }
            override fun onLoadFailed(errorDrawable: Drawable?) {
                super.onLoadFailed(errorDrawable)
                Log.w("ImageLoading", "Échec du chargement de la miniature pour : $imageUri")
            }
        })
}


class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private val imageDataMap = mutableMapOf<String, MutableList<com.example.imagenavigator.model.Zone>>()
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()
    // Getter pour imageBitmapMap
    fun getImageBitmapMap(): MutableMap<String, Bitmap> {
        return imageBitmapMap
    }

    // Getter pour imageDataMap
    fun getImageDataMap(): MutableMap<String, MutableList<com.example.imagenavigator.model.Zone>> {
        return imageDataMap
    }
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


    private fun isValidImage(file: DocumentFile): Boolean {
        val mimeType = contentResolver.getType(file.uri)
        return mimeType?.startsWith("image/") == true &&
                file.name?.lowercase()?.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|gif)$")) == true
    }

    // Fonction principale pour charger les images depuis un dossier
    private fun loadImagesFromFolder(uri: Uri) {
        Log.d("DEBUG", "Chargement du dossier : $uri")

        val skippedFiles = mutableListOf<String>()
        Log.d("DEBUG", "Début du chargement avec uri : $uri")

        binding.loadingOverlay.isVisible = true

        CoroutineScope(Dispatchers.IO).launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val validImages = mutableListOf<Pair<Bitmap, String>>()
            val imagePaths = mutableListOf<String>()

            // Fonction pour parcourir tous les fichiers du dossier
            fun traverse(file: DocumentFile, currentPath: String = "") {
                Log.d("DEBUG", "Fichier détecté : ${file.name}")
                if (file.isDirectory) {
                    val newPath = if (currentPath.isEmpty()) file.name ?: "" else "$currentPath/${file.name}"
                    file.listFiles().forEach { traverse(it, newPath) }
                } else {
                    // Vérification du type MIME avant de charger l'image
                    val mimeType = contentResolver.getType(file.uri)
                    if (mimeType?.startsWith("image/") == true &&
                        file.name?.lowercase()?.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|gif)$")) == true) {
                        Log.d("ImageLoading", "Image valide détectée : ${file.name}")
                        try {
                            val inputStreamCheck: InputStream? = contentResolver.openInputStream(file.uri)
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(inputStreamCheck, null, options)
                            inputStreamCheck?.close()

                            if (options.outWidth > 0 && options.outHeight > 0) {
                                Log.d("ImageLoading", "Image $file a des dimensions valides : ${options.outWidth}x${options.outHeight}")
                                Glide.with(this@EditorActivity)
                                    .asBitmap()
                                    .load(file.uri)
                                    .apply(RequestOptions().override(800, 600)) // Redimensionner à 800x600 px
                                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Mise en cache
                                    .into(object : BitmapFullCustomTarget(this@EditorActivity, file, currentPath, validImages, imagePaths, skippedFiles) {})
                            } else {
                                Log.d("ImageLoading", "Image ignorée : ${file.name}")
                                skippedFiles.add("${file.name} : dimensions invalides")
                            }
                        } catch (e: Exception) {
                            Log.e("ImageLoading", "Erreur lors du traitement de l'image : ${file.name}", e)
                        }
                    } else {
                        Log.d("ImageLoading", "Image ignorée : ${file.name}")
                        skippedFiles.add("${file.name} : type MIME non image ou extension incorrecte")
                    }
                }
            }

            folder.listFiles().forEach { traverse(it) }
            Log.d("DEBUG", "Fin de la traversée. Images valides : ${validImages.size}, Ignorées : ${skippedFiles.size}")

            val allImages = imageBitmapMap.map { (path, bitmap) -> bitmap to path }
            imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(allImages)

            withContext(Dispatchers.Main) {
                imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
                binding.loadingOverlay.isVisible = false

                if (skippedFiles.isNotEmpty()) {
                    Log.d("DEBUG", "Affichage d’un message d’alerte avec ${skippedFiles.size} fichiers ignorés")
                    AlertDialog.Builder(this@EditorActivity)
                        .setTitle("Images ignorées")
                        .setMessage(skippedFiles.joinToString("\n"))
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
}

// Classe CustomTarget pour corriger l'implémentation de onLoadCleared
open class BitmapFullCustomTarget(
    private val context: Context,
    private val file: DocumentFile,
    private val currentPath: String,
    private val validImages: MutableList<Pair<Bitmap, String>>,
    private val imagePaths: MutableList<String>,
    private val skippedFiles: MutableList<String>
) : CustomTarget<Bitmap>() {
    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
        try {
            val fullBitmap = resource
            Log.d("ImageLoading", "Génération de la miniature pour l'image ${file.uri}")
            val name = file.name!!
            val fullPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
            val activity = context as? EditorActivity
            if (activity != null) {
                if (!activity.getImageBitmapMap().containsKey(fullPath)) {
                    activity.getImageBitmapMap()[fullPath] = fullBitmap
                    activity.getImageDataMap()[fullPath] = mutableListOf()
                    // Générer la miniature en arrière-plan
                    activity.generateThumbnailInBackground(file, fullPath, validImages, imagePaths)
                } else {
                    skippedFiles.add("$fullPath : image déjà chargée")
                }
            }
        } catch (e: Exception) {
            Log.w("ImageLoading", "Échec de la génération de la miniature pour ${file.uri}", e)
            skippedFiles.add("${file.name} : erreur lors de la génération de la miniature")
        }
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        // Nothing to do here
    }
}

// Fonction pour générer des miniatures en arrière-plan
fun EditorActivity.generateThumbnailInBackground(
    file: DocumentFile,
    fullPath: String,
    validImages: MutableList<Pair<Bitmap, String>>,
    imagePaths: MutableList<String>
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Charger l'image dans un thread en arrière-plan
            val thumbnail = Glide.with(this@generateThumbnailInBackground)
                .asBitmap()
                .load(file.uri)
                .submit(200, 200)
                .get()  // Appelé sur un thread en arrière-plan

            // Une fois la miniature chargée, revenir au thread principal pour l'ajouter à la map
            withContext(Dispatchers.Main) {
                if (!getImageBitmapMap().containsKey(fullPath)) {
                    // Normalement la map contient déjà l'image pleine, on ajoute la miniature à la liste pour affichage
                    validImages.add(Pair(thumbnail, fullPath))
                    imagePaths.add(fullPath)
                    Log.d("ImageLoading", "Miniature générée avec succès pour $fullPath")
                } else {
                    Log.w("ImageLoading", "$fullPath déjà chargée")
                }
            }
        } catch (e: Exception) {
            Log.e("ImageLoading", "Erreur lors de la génération de la miniature pour $fullPath", e)
        }
    }
}