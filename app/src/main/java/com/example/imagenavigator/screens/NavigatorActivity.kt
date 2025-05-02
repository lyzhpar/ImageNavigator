package com.example.imagenavigator.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.imagenavigator.databinding.ActivityNavigatorBinding
import com.example.imagenavigator.model.Adventure
import com.example.imagenavigator.model.ZoneData
import com.google.gson.Gson
import java.io.InputStreamReader
import androidx.appcompat.app.AlertDialog
import android.util.Log
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade


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

        // Lancer le picker pour choisir un dossier
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)

        binding.backButton.setOnClickListener {
            goBack()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            folderUri = uri

            // Essayer de récupérer l'aventure transmise
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
            val inputStream = contentResolver.openInputStream(jsonUri)
            val reader = InputStreamReader(inputStream)
            adventure = Gson().fromJson(reader, Adventure::class.java)
            reader.close()

            currentImageName = adventure.images.firstOrNull()?.imageName

            showCurrentImage()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur de chargement de l'aventure.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun findFileRecursively(folder: DocumentFile?, relativePath: String): DocumentFile? {
        if (folder == null || !folder.isDirectory) return null

        // Normalisation du chemin : remplace les antislashs par des slashs, et réduit les slashs multiples à un seul
        val cleanRelativePath = relativePath.replace("\\", "/").replace(Regex("/+"), "/")
        val segments = cleanRelativePath.split('/')

        var currentFolder = folder
        for (i in 0 until segments.size - 1)
        {            currentFolder = currentFolder?.listFiles()
                ?.firstOrNull { it.isDirectory && it.name == segments[i] }
        }

        return currentFolder?.listFiles()
            ?.firstOrNull { !it.isDirectory && it.name == segments.last() }
    }

private fun showCurrentImage() {
    currentImageName?.let { fullImagePath ->
        Log.d("NAVIGATOR", "Recherche des zones pour : $fullImagePath")

        val folderDocument = DocumentFile.fromTreeUri(this, folderUri)

        /*Log.d("NAVIGATOR", "On cherche l'image avec ce chemin : $fullImagePath")
        for (image in adventure.images) {
            Log.d("NAVIGATOR", "Image JSON présente : ${image.imageName}")
        }*/

        var imageFile = findFileRecursively(folderDocument, fullImagePath)
        var correctedPath = fullImagePath
        if (imageFile == null) {
            correctedPath = correctImagePathIfNeeded(folderDocument, fullImagePath)
            imageFile = findFileRecursively(folderDocument, correctedPath)
            if (imageFile != null) {
                currentImageName = correctedPath
            }
        } else {
            currentImageName = fullImagePath
        }

        if (imageFile != null) {
            Log.d("NAVIGATOR", "Image trouvée : ${imageFile.name}")
            Glide.with(this)
                .load(imageFile.uri)
                .transition(withCrossFade(300))
                .into(binding.imageView)
        } else {
            Log.e("NAVIGATOR", "Image non trouvée : $correctedPath")
            Toast.makeText(this, "Image non trouvée : $correctedPath", Toast.LENGTH_SHORT).show()
        }

        Log.d("NAVIGATOR", "On cherche l'image avec ce chemin : $correctedPath")
        for (image in adventure.images) {
            Log.d("NAVIGATOR", "Image JSON présente : ${image.imageName}")
        }

        // --- Bloc de debug pour comparer les chemins JSON et les fichiers réels ---
        val allFiles = mutableListOf<String>()
        fun listFilesRecursively(folder: DocumentFile?) {
            folder?.listFiles()?.forEach {
                if (it.isDirectory) {
                    listFilesRecursively(it)
                } else {
                    allFiles.add(it.name ?: "")
                }
            }
        }
        allFiles.clear()
        /*listFilesRecursively(DocumentFile.fromTreeUri(this, folderUri))

        Log.d("DEBUG", "---- Fichiers trouvés dans le dossier : ----")
        for (file in allFiles) {
            Log.d("DEBUG", file)
        }
        Log.d("DEBUG", "---- Images dans le JSON : ----")
        for (img in adventure.images) {
            Log.d("DEBUG", img.imageName)
        }
        */

        // --- Fin du bloc de debug ---

        // Ici on utilise directement correctedPath pour trouver les zones,
        // mais on s'assure de comparer sans espaces superflus.
        val currentImageData = adventure.images.find { it.imageName.trim() == correctedPath.trim() }
        if (currentImageData == null) {
            Log.e("NAVIGATOR", "Aucune image JSON trouvée pour ce nom : $correctedPath")
        }
        val zones = currentImageData?.zones ?: emptyList<ZoneData>()

        Log.d("NAVIGATOR", "Nombre de zones chargées : ${zones.size}")

        binding.overlayView.zones = zones
        binding.overlayView.onZoneClicked = { targetPath ->
            Log.d("NAVIGATOR", "Zone cliquée, cible = $targetPath")
            navigateToImage(targetPath)
        }
    }
}

private fun correctImagePathIfNeeded(folder: DocumentFile?, path: String): String {
    if (folder == null) return path

    val cleanPath = path.replace("\\", "/").replace(Regex("/+"), "/").trim()

    val segments = cleanPath.split("/")
    if (segments.isEmpty()) return path

    var currentFolder = folder
    for (i in 0 until segments.size - 1) {
        currentFolder = currentFolder?.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals(segments[i], ignoreCase = true) }
    }

    val targetName = segments.last()
    val matchingFile = currentFolder?.listFiles()
        ?.firstOrNull { !it.isDirectory && it.name.equals(targetName, ignoreCase = true) }

    return if (matchingFile != null) {
        // Reconstruire un chemin avec le bon nom de fichier
        val correctedSegments = segments.dropLast(1) + (matchingFile.name ?: targetName)
        correctedSegments.joinToString("/")
    } else {
        path
    }
}

    private fun navigateToImage(targetPath: String) {
        Log.d("NAVIGATOR", "Navigation vers l'image : $targetPath")
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

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 123
    }
}
