package com.example.imagenavigator

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.imagenavigator.databinding.ActivityNavigatorBinding
import com.example.imagenavigator.model.Adventure
import com.google.gson.Gson
import java.io.InputStreamReader

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

            folderUri = Uri.parse(adventure.folderUri)
            currentImageName = adventure.images.firstOrNull()?.imageName

            showCurrentImage()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur de chargement de l'aventure.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showCurrentImage() {
        currentImageName?.let { imageName ->
            val imageUri = Uri.withAppendedPath(folderUri, Uri.encode(imageName))

            Glide.with(this)
                .load(imageUri)
                .transition(withCrossFade(300))
                .into(binding.imageView)

            val currentImageData = adventure.images.find { it.imageName == imageName }
            val currentZones = currentImageData?.zones ?: emptyList()

            binding.overlayView.zones = currentZones
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