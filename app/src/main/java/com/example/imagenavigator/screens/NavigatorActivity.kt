package com.example.imagenavigator

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.imagenavigator.databinding.ActivityNavigatorBinding
import com.example.imagenavigator.model.Adventure
import com.google.gson.Gson
import java.io.InputStreamReader

class NavigatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigatorBinding

    private lateinit var adventure: Adventure
    private lateinit var folderUri: Uri
    private var currentImageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Charger l'aventure (exemple : depuis un Intent avec l'URI du JSON)
        val jsonUri = intent.getParcelableExtra<Uri>("adventureJsonUri")
        if (jsonUri != null) {
            loadAdventure(jsonUri)
        } else {
            // TODO: Gérer le cas où aucun fichier JSON n'est fourni
        }
    }

    private fun loadAdventure(jsonUri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(jsonUri)
            val reader = InputStreamReader(inputStream)
            adventure = Gson().fromJson(reader, Adventure::class.java)
            reader.close()

            folderUri = Uri.parse(adventure.folderUri)
            // On démarre sur la première image de la liste
            currentImageName = adventure.images.firstOrNull()?.imageName

            showCurrentImage()

        } catch (e: Exception) {
            e.printStackTrace()
            // TODO: Gérer l'erreur (ex: afficher un message d'erreur)
        }
    }

    private fun showCurrentImage() {
        currentImageName?.let { imageName ->
            val imageUri = Uri.withAppendedPath(folderUri, Uri.encode(imageName))
            Glide.with(this)
                .load(imageUri)
                .into(binding.imageView)

            // TODO : mettre à jour OverlayView pour afficher les zones cliquables
        }
    }
}