package com.example.imagenavigator.screens

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
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
import android.util.Log
import android.view.inputmethod.EditorInfo

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private val imageDataMap = mutableMapOf<String, MutableList<com.example.imagenavigator.model.Zone>>()
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()
    private var currentImageName: String? = null
    private var adventureName = "Nom de l'aventure"
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

        binding.saveButton.setOnClickListener {
            // Ajoutez ici la logique de sauvegarde si besoin
        }

        binding.resetButton.setOnClickListener {
            binding.drawingView.zones.clear()
            binding.drawingView.invalidate()
        }

        binding.buttonImportFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        // Réinitialiser uniquement au premier chargement
        groupedImages.clear()
        imageBitmapMap.clear()
        imageDataMap.clear()
        folderPickerLauncher.launch(null)
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


    private fun getThumbnail(uri: Uri, maxSize: Int = 200): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        var scale = 1
        while (options.outWidth / scale > maxSize || options.outHeight / scale > maxSize) {
            scale *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
        }

        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }

    private fun loadImagesFromFolder(uri: Uri) {
        Log.d("DEBUG", "Chargement du dossier : $uri")

        val skippedFiles = mutableListOf<String>()
        Log.d("DEBUG", "Début du chargement avec uri : $uri")

        binding.loadingOverlay.isVisible = true

        CoroutineScope(Dispatchers.IO).launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val validImages = mutableListOf<Pair<Bitmap, String>>()
            val imagePaths = mutableListOf<String>()

            fun traverse(file: DocumentFile, currentPath: String = "") {
                Log.d("DEBUG", "Fichier détecté : ${file.name}")
                if (file.isDirectory) {
                    val newPath = if (currentPath.isEmpty()) file.name ?: "" else "$currentPath/${file.name}"
                    file.listFiles().forEach { traverse(it, newPath) }
                } else {
                    val mimeType = contentResolver.getType(file.uri)
                    if (mimeType?.startsWith("image/") == true &&
                        file.name?.lowercase()?.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|gif)$")) == true) {

                        val inputStreamCheck: InputStream? = contentResolver.openInputStream(file.uri)
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(inputStreamCheck, null, options)
                        inputStreamCheck?.close()

                        if (options.outWidth > 0 && options.outHeight > 0) {
                            val fullBitmap = contentResolver.openInputStream(file.uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                            val thumbnail = getThumbnail(file.uri)
                            if (fullBitmap != null && thumbnail != null) {
                                val name = file.name!!
                                val fullPath = if (currentPath.isEmpty()) name else "$currentPath/$name"

                                Log.d("EditorActivity", "Ajout de l'image dans map : $fullPath")
                                Log.d("EditorActivity", "Image ajoutée - Nom : $name | FullPath : $fullPath | Bitmap : ${fullBitmap.width}x${fullBitmap.height}")

                                if (!imageBitmapMap.containsKey(fullPath)) {
                                    imageBitmapMap[fullPath] = fullBitmap
                                    imageDataMap[fullPath] = mutableListOf()
                                    validImages.add(thumbnail to fullPath)
                                    imagePaths.add(fullPath)
                                } else {
                                    skippedFiles.add("$fullPath : image déjà chargée")
                                }
                            } else {
                                skippedFiles.add("${file.name} : impossible de charger le bitmap complet ou la miniature")
                            }
                        } else {
                            skippedFiles.add("${file.name} : dimensions invalides")
                        }
                    } else {
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
