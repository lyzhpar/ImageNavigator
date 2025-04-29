package com.example.imagenavigator.screens

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.imagenavigator.databinding.ActivityNavigatorBinding
import com.example.imagenavigator.model.Adventure
import com.google.gson.Gson
import java.io.InputStreamReader
import android.content.Intent


class NavigatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigatorBinding

    private lateinit var adventure: Adventure
    private lateinit var folderUri: Uri
    private var currentImageName: String? = null
    private val historyStack = mutableListOf<String>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNavigatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Essayer de récupérer l'URI du fichier JSON
        val jsonUri = intent.getParcelableExtra<Uri>("adventureJsonUri")
        if (jsonUri != null) {
            loadAdventure(jsonUri)
        } else {
            Toast.makeText(this, "Erreur : Aucune aventure fournie.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.backButton.setOnClickListener {
            goBack()
        }
    }

    private fun loadAdventure(jsonUri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(jsonUri)
            val reader = InputStreamReader(inputStream)
            adventure = Gson().fromJson(reader, Adventure::class.java)
            reader.close()

            Log.d("NAVIGATOR", "Adventure loaded: ${adventure.adventureTitle}")
            Log.d("NAVIGATOR", "Folder URI: ${adventure.folderUri}")

            folderUri = Uri.parse(adventure.folderUri)

            // Redemander la permission persistante sur ce folderUri
            try {
                contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(this, "Impossible d'accéder au dossier.", Toast.LENGTH_SHORT).show()
                finish()
            }

            currentImageName = adventure.images.firstOrNull()?.imageName

            Log.d("NAVIGATOR", "First image to display: $currentImageName")

            showCurrentImage()

        } catch (e: Exception) {
            Log.e("NAVIGATOR", "Erreur loadAdventure", e)
            Toast.makeText(this, "Erreur de chargement de l'aventure.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showCurrentImage() {
        currentImageName?.let { imageName ->
         /*   val imageUri = Uri.withAppendedPath(folderUri, Uri.encode(imageName))

            Log.d("NAVIGATOR", "Trying to load image: $imageUri")

            Glide.with(this)
                .load(imageUri)
                .transition(withCrossFade(300))
                .into(binding.imageView)
        */

            val folder = DocumentFile.fromTreeUri(this, folderUri)
            val targetFile = folder?.findFile(imageName)

            if (targetFile != null && targetFile.exists()) {
                val imageUri = targetFile.uri

                Glide.with(this)
                    .load(imageUri)
                    .transition(withCrossFade(300))
                    .into(binding.imageView)
            } else {
                Toast.makeText(this, "Image non trouvée : $imageName", Toast.LENGTH_SHORT).show()
            }

            val currentImageData = adventure.images.find { it.imageName == imageName }
            val zones = currentImageData?.zones ?: emptyList()

            binding.overlayView.zones = zones
            binding.overlayView.onZoneClicked = { targetPath ->
                navigateToImage(targetPath)
            }
        }
    }

    private fun navigateToImage(targetPath: String) {
        currentImageName?.let { historyStack.add(it) }
        currentImageName = targetPath
        showCurrentImage()
    }

    private fun goBack() {
        if (historyStack.isNotEmpty()) {
            currentImageName = historyStack.removeAt(historyStack.size - 1)
            showCurrentImage()
        } else {
            finish()
        }
    }
}