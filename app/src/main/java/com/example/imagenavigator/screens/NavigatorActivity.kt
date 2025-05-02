package com.example.imagenavigator.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.imagenavigator.databinding.ActivityNavigatorBinding
import com.example.imagenavigator.model.Adventure
import com.google.gson.Gson
import java.io.InputStreamReader
import com.example.imagenavigator.R
import com.example.imagenavigator.model.ZoneData

class NavigatorActivity : BaseActivity() {

    private lateinit var binding: ActivityNavigatorBinding
    private lateinit var adventure: Adventure
    private lateinit var folderUri: Uri
    private var currentImageName: String? = null
    private val historyStack = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.overlayView.onLongClickAt = { x, y ->
            showContextMenu()
        }

        binding.overlayView.onZoneClicked = { targetPath ->
            navigateToImage(targetPath)
        }

        // Lancement de la sélection du dossier contenant les images
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)

        binding.backButton.setOnClickListener { goBack() }

        // Activation des clics longs et simples sur l'overlay
        binding.overlayView.isClickable = true
        binding.overlayView.isLongClickable = true

        // TODO: gérer loadingView et permissions
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == RESULT_OK) {
            folderUri = data?.data ?: return
            val jsonUri = intent.getParcelableExtra<Uri>("adventureJsonUri")
            if (jsonUri != null) {
                loadAdventure(jsonUri)
            } else {
                Toast.makeText(this, "Erreur : Aucune aventure fournie.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadAdventure(jsonUri: Uri) {
        try {
            binding.overlayView.showZonesOverlay = false

            val inputStream = contentResolver.openInputStream(jsonUri)
            val reader = InputStreamReader(inputStream)
            adventure = Gson().fromJson(reader, Adventure::class.java)
            reader.close()
            currentImageName = adventure.startImagePath ?: adventure.images.firstOrNull()?.imageName
            showCurrentImage()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur de chargement de l'aventure.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showCurrentImage() {
        currentImageName?.let { fullImagePath ->
            Log.d("NAVIGATOR", "showCurrentImage: loading $fullImagePath")
            val folderDocument = DocumentFile.fromTreeUri(this, folderUri)
            val imageFile = findFileRecursively(folderDocument, fullImagePath)
            if (imageFile != null) {
                Glide.with(this)
                    .load(imageFile.uri)
                    .transition(withCrossFade(300))
                    .override(2048, 2048)
                    .into(binding.imageView)

                val currentImageData = adventure.images.find { it.imageName.trim() == fullImagePath.trim() }
                val zones = currentImageData?.zones ?: emptyList()
                applyZones(zones)
                Log.d("NAVIGATOR", "showCurrentImage done: zones=${zones.size}, showOverlay=${binding.overlayView.showZonesOverlay}")
            } else {
                Log.d("NAVIGATOR", "showCurrentImage: image NOT found $fullImagePath")
                Toast.makeText(this, "Image non trouvée : $fullImagePath", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToImage(targetPath: String) {
        Log.d("NAVIGATOR", "navigateToImage: targetPath=$targetPath, currentImage=$currentImageName")
        if (currentImageName != targetPath) {
            currentImageName = targetPath
            showCurrentImage()
        } else {
            Log.d("NAVIGATOR", "navigateToImage: already at $targetPath")
        }
    }

    private fun goBack() {
        if (historyStack.isNotEmpty()) {
            currentImageName = historyStack.removeAt(historyStack.size - 1)
            showCurrentImage()
        } else {
            if (isFinishing) return
            finish()
        }
    }

    private fun applyZones(zones: List<ZoneData>) {
        Log.d("NAVIGATOR", "applyZones called with ${zones.size} zones")
        Log.d("NAVIGATOR", "applyZones: zones count = ${zones.size}")
        binding.overlayView.zones = zones
        binding.overlayView.postInvalidateOnAnimation()
    }

    private fun showContextMenu() {
        Log.d("NAVIGATOR", "showContextMenu: popup opened")
        val popupView = layoutInflater.inflate(R.layout.custom_popup_menu, null)
        val popupWindow = android.widget.PopupWindow(popupView, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, true)

        // TODO: améliorer popup menu

        popupWindow.showAtLocation(binding.root, 0, 0, 0) // TODO: afficher le popup menu au bon endroit

        popupView.findViewById<android.widget.TextView>(R.id.option1).setOnClickListener {
            Log.d("NAVIGATOR", "showContextMenu: option1 → reset to start image")
            currentImageName = adventure.startImagePath ?: adventure.images.firstOrNull()?.imageName
            historyStack.clear()
            binding.overlayView.showZonesOverlay = false
            showCurrentImage()
            popupWindow.dismiss()
        }
        popupView.findViewById<android.widget.TextView>(R.id.option2).setOnClickListener {
            val newShowOverlay = !binding.overlayView.showZonesOverlay
            Log.d("NAVIGATOR", "showContextMenu: option2 → toggle overlay to $newShowOverlay")
            binding.overlayView.showZonesOverlay = newShowOverlay
            binding.overlayView.postInvalidateOnAnimation()
            popupWindow.dismiss()
        }
    }

    // TODO: améliorer recherche fichiers
    private fun findFileRecursively(folder: DocumentFile?, relativePath: String): DocumentFile? {
        if (folder == null || !folder.isDirectory) return null
        val cleanRelativePath = relativePath.replace("\\", "/").replace(Regex("/+"), "/")
        val segments = cleanRelativePath.split('/')
        var currentFolder = folder
        for (i in 0 until segments.size - 1) {
            val segment = segments[i]
            currentFolder = currentFolder?.listFiles()?.firstOrNull { it.isDirectory && it.name == segment }
        }
        return currentFolder?.listFiles()?.firstOrNull { !it.isDirectory && it.name == segments.last() }
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 123
    }
}
