package com.example.imagenavigator.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color // Ajout de l'import pour Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView // Ajout de l'import pour ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.imagenavigator.databinding.ActivityEditorBinding
import com.example.imagenavigator.model.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding

    private val imageDataMap: MutableMap<String, MutableList<Zone>> = mutableMapOf()
    private var currentImageName: String? = null
    private val REQUEST_CODE_OPEN_FOLDER = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ouvrir automatiquement la fenêtre de sélection de dossier lors de l'ouverture de l'éditeur
        openFolderPicker()

        binding.resetButton.setOnClickListener {
            currentImageName?.let { name ->
                imageDataMap[name]?.clear()
                binding.drawingView.zones.clear()
                binding.drawingView.invalidate()
            }
        }

        binding.saveButton.setOnClickListener {
            // TODO : sauvegarder imageDataMap dans un fichier JSON
        }
    }

    private fun openFolderPicker() {
        // Lancer la fenêtre de sélection du dossier
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_OPEN_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FOLDER && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return

            // Demander une permission persistante pour l'URI
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                // Charger les images du dossier sélectionné
                loadImagesFromFolder(treeUri)
            } catch (e: SecurityException) {
                Log.e("EditorActivity", "Erreur lors de la prise de la permission persistante pour l'URI", e)
                // Gérer l'erreur ici, comme afficher un message à l'utilisateur
            }
        }
    }

    private fun loadImagesFromFolder(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch

            val imageFiles = mutableListOf<Pair<Bitmap, String>>()
            Log.d("ImageLoader", "Dossier sélectionné : ${folder.name}")

            fun traverseFolder(folder: DocumentFile) {
                for (file in folder.listFiles()) {
                    if (file.isDirectory) {
                        traverseFolder(file)
                    } else if (file.name?.lowercase()?.endsWith(".jpg") == true ||
                        file.name?.lowercase()?.endsWith(".jpeg") == true ||
                        file.name?.lowercase()?.endsWith(".png") == true ||
                        file.name?.lowercase()?.endsWith(".webp") == true ||
                        file.name?.lowercase()?.endsWith(".bmp") == true ||
                        file.name?.lowercase()?.endsWith(".gif") == true) {

                        val inputStream: InputStream? = contentResolver.openInputStream(file.uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        Log.d("ImageLoader", "Fichier : ${file.name}, bitmap = ${bitmap != null}")
                        inputStream?.close()

                        if (bitmap != null && file.name != null) {
                            imageFiles.add(bitmap to file.name!!)
                            Log.d("ImageLoader", "Ajouté à la liste : ${file.name}")
                        } else {
                            Log.w("ImageLoader", "Impossible de décoder : ${file.name}")
                        }
                    }
                }
            }

            traverseFolder(folder)

            withContext(Dispatchers.Main) {
                imageFiles.forEach { (bitmap, name) ->
                    Log.d("ImageLoader", "Ajout dans la vue : $name")
                    addImageToSidebar(bitmap, name)
                }
            }
        }
    }

    private fun addImageToSidebar(bitmap: Bitmap, imageName: String) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100
            ).apply {
                setMargins(8, 8, 8, 8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
            contentDescription = imageName

            setOnClickListener {
                currentImageName = imageName
                binding.drawingView.imageBitmap = bitmap
                val zones = imageDataMap[imageName] ?: mutableListOf()
                binding.drawingView.zones.clear()
                binding.drawingView.zones.addAll(zones)
                binding.drawingView.invalidate()
            }
        }

        // Assurer que le LinearLayout est visible avant l'ajout
        binding.imageList.visibility = LinearLayout.VISIBLE

        // Ajouter l'image à la vue
        binding.imageList.addView(imageView)

        // Force la mise à jour de l'affichage
        binding.imageList.invalidate()

        if (!imageDataMap.containsKey(imageName)) {
            imageDataMap[imageName] = mutableListOf()
        }
    }
}

suuuuuuuuuuper