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

        hideSystemUI()
        setupTitleEditor()

        imageAdapter = ImageAdapter(groupedImages) { bitmap, name ->

            Log.d("EditorActivity", "Nom reçu dans callback : $name")

            currentImageName = name

            Log.d("DrawingView", "Image demandée: $currentImageName")
            Log.d("DrawingView", "Bitmap trouvé ? ${imageBitmapMap.containsKey(currentImageName)}")

            binding.drawingView.imageBitmap = imageBitmapMap[name]
            val zones = imageDataMap[name] ?: mutableListOf()
            binding.drawingView.zones.clear()
            binding.drawingView.zones.addAll(zones)
            binding.drawingView.invalidate()
        }

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

    private fun setupTitleEditor() {
        val titleView: TextView = binding.adventureTitle
        titleView.text = adventureName
        titleView.setOnClickListener {
            val editText = EditText(this).apply {
                setText(adventureName)
                setSelection(adventureName.length)
            }
            AlertDialog.Builder(this)
                .setTitle("Changer le nom de l’aventure")
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    adventureName = editText.text.toString()
                    titleView.text = adventureName
                }
                .setNegativeButton("Annuler", null)
                .show()
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

        val skippedFiles = mutableListOf<String>()

        binding.loadingOverlay.isVisible = true
        groupedImages.clear()
        imageBitmapMap.clear()
        imageDataMap.clear()

        CoroutineScope(Dispatchers.IO).launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val validImages = mutableListOf<Pair<Bitmap, String>>()
            val imagePaths = mutableListOf<String>()

            fun traverse(file: DocumentFile, currentPath: String = "") {
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

                                imageBitmapMap[fullPath] = fullBitmap
                                imageDataMap[fullPath] = mutableListOf()
                                validImages.add(thumbnail to fullPath)
                                imagePaths.add(fullPath)
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

            imageRootNode = ImageGroupTreeBuilder.buildImageGroupTree(validImages)

            withContext(Dispatchers.Main) {
                imageAdapter.updateData(ImageGroup.fromTree(imageRootNode))
                binding.loadingOverlay.isVisible = false

                if (skippedFiles.isNotEmpty()) {
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
