package com.example.imagenavigator.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imagenavigator.adapters.ImageAdapter
import com.example.imagenavigator.databinding.ActivityEditorBinding
import kotlinx.coroutines.*
import java.io.InputStream

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
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
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
        binding.loadingOverlay.isVisible = true
        images.clear()

        CoroutineScope(Dispatchers.IO).launch {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch

            suspend fun traverse(file: DocumentFile) {
                if (file.isDirectory) {
                    file.listFiles().forEach { traverse(it) }
                } else if (file.name?.lowercase()?.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|gif)$")) == true) {
                    val inputStream: InputStream? = contentResolver.openInputStream(file.uri)
                    val bitmap = inputStream?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    bitmap?.let {
                        val name = file.name!!
                        imageBitmapMap[name] = it
                        images.add(it to name)
                        imageDataMap[name] = mutableListOf()
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
            }
        }
    }
}