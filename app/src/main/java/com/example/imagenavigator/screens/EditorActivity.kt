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
import kotlinx.coroutines.*
import java.io.InputStream
import android.graphics.BitmapFactory

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private val imageDataMap = mutableMapOf<String, MutableList<com.example.imagenavigator.model.Zone>>()
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()
    private var currentImageName: String? = null
    private var adventureName = "Nom de l'aventure"
    private val images = mutableListOf<Pair<Bitmap, String>>()

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { loadImagesFromFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()
        setupTitleEditor()

        binding.recyclerViewThumbnails.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewThumbnails.setHasFixedSize(true)
        binding.recyclerViewThumbnails.adapter = ImageAdapter(emptyList()) { _, _ -> }

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

    private fun loadImagesFromFolder(uri: Uri) {
        val skippedFiles = mutableListOf<String>()

        binding.loadingOverlay.isVisible = true
        images.clear()

        CoroutineScope(Dispatchers.IO).launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch

            fun traverse(file: DocumentFile) {
                if (file.isDirectory) {
                    file.listFiles().forEach { traverse(it) }
                } else {
                    val mimeType = contentResolver.getType(file.uri)
                    if (mimeType?.startsWith("image/") == true &&
                        file.name?.lowercase()?.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|gif)$")) == true) {

                        val inputStreamCheck: InputStream? = contentResolver.openInputStream(file.uri)
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(inputStreamCheck, null, options)
                        inputStreamCheck?.close()

                        if (options.outWidth > 0 && options.outHeight > 0) {
                            val inputStream: InputStream? = contentResolver.openInputStream(file.uri)
                            val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                            if (bitmap != null) {
                                val name = file.name!!
                                imageBitmapMap[name] = bitmap
                                images.add(bitmap to name)
                                imageDataMap[name] = mutableListOf()
                            } else {
                                skippedFiles.add("${file.name} : bitmap décodé nul")
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

            withContext(Dispatchers.Main) {
                binding.recyclerViewThumbnails.adapter = ImageAdapter(images) { bitmap, name ->
                    currentImageName = name
                    binding.drawingView.imageBitmap = bitmap
                    val zones = imageDataMap[name] ?: mutableListOf()
                    binding.drawingView.zones.clear()
                    binding.drawingView.zones.addAll(zones)
                    binding.drawingView.invalidate()
                }
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