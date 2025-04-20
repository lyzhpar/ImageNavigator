package com.example.imagenavigator.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.imagenavigator.databinding.ActivityEditorBinding
import com.example.imagenavigator.model.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private val imageDataMap: MutableMap<String, MutableList<Zone>> = mutableMapOf()
    private val worldsMap: MutableMap<String, MutableList<String>> = mutableMapOf()
    private var currentImageName: String? = null
    private val REQUEST_CODE_OPEN_FOLDER = 1001
    private var adventureName: String = ""
    private var selectedFolderName: String = ""
    private var adventureUri: Uri? = null
    private var folderPathView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            currentImageName = savedInstanceState.getString("currentImage")
            adventureName = savedInstanceState.getString("adventureName", "")
            selectedFolderName = savedInstanceState.getString("selectedFolder", "")
            savedInstanceState.getString("adventureUri")?.let {
                adventureUri = Uri.parse(it)
                loadImagesFromFolder(adventureUri!!)
            }
            savedInstanceState.getBundle("zoneMap")?.let { zoneBundle ->
                for (key in zoneBundle.keySet()) {
                    val zoneList = zoneBundle.getParcelableArrayList<Zone>(key)
                    if (zoneList != null) {
                        imageDataMap[key] = zoneList.toMutableList()
                    }
                }
            }
        } else {
            showAdventureSetupDialog()
        }

        val layoutParams = binding.drawingView.layoutParams as ConstraintLayout.LayoutParams
        binding.drawingView.layoutParams = layoutParams

        binding.drawingView.onZoneCreated = { zone ->
            currentImageName?.let { name ->
                val zones = imageDataMap.getOrPut(name) { mutableListOf() }
                zones.add(zone)
            }
        }

        binding.resetButton.setOnClickListener {
            currentImageName?.let { name ->
                imageDataMap[name]?.clear()
                binding.drawingView.zones.clear()
                binding.drawingView.invalidate()
            }
        }

        binding.saveButton.setOnClickListener {
            // À compléter : sauvegarde en JSON
        }
        Log.d("LAYOUT_DEBUG", "Chargé en orientation: ${resources.configuration.orientation}")
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_OPEN_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FOLDER && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            adventureUri = treeUri
            try {
                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val folder = DocumentFile.fromTreeUri(this@EditorActivity, treeUri) ?: return
                selectedFolderName = folder.name ?: ""
                folderPathView?.text = "Dossier sélectionné : $selectedFolderName"
                loadImagesFromFolder(treeUri)
            } catch (e: SecurityException) {
                Log.e("EditorActivity", "Erreur permission URI", e)
            }
        }
    }

    private fun loadImagesFromFolder(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val imageFiles = mutableListOf<Pair<Bitmap, String>>()

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
                        inputStream?.close()

                        if (bitmap != null && file.name != null) {
                            imageFiles.add(bitmap to file.name!!)
                            val relativePath = file.uri.path?.substringAfterLast("document/") ?: file.name!!
                            val world = relativePath.substringBeforeLast('/', missingDelimiterValue = "racine")
                            val imageName = file.name!!
                            val imagesInWorld = worldsMap.getOrPut(world) { mutableListOf() }
                            imagesInWorld.add(imageName)
                        }
                    }
                }
            }

            traverseFolder(folder)

            withContext(Dispatchers.Main) {
                val container = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT)
                    binding.imageListHorizontal!! else binding.imageListVertical!!

                container.removeAllViews()

                imageFiles.forEach { (bitmap, name) ->
                    addImageToSidebar(bitmap, name, container)
                }

                currentImageName?.let { current ->
                    imageFiles.find { it.second == current }?.let { (bitmap, _) ->
                        binding.drawingView.imageBitmap = bitmap
                        val zones = imageDataMap[current] ?: mutableListOf()
                        binding.drawingView.zones.clear()
                        binding.drawingView.zones.addAll(zones)
                        binding.drawingView.invalidate()
                    }
                }
            }
        }
    }

    private fun addImageToSidebar(bitmap: Bitmap, imageName: String, container: LinearLayout) {
        val density = resources.displayMetrics.density
        val widthPx = (120 * density).toInt()
        val heightPx = (90 * density).toInt()

        val frameLayout = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx).apply {
                setMargins(8, 8, 8, 8)
            }
            isClickable = true
        }

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#DDDDDD"))
            setPadding(2, 2, 2, 2)
            val vignetteBitmap = Bitmap.createScaledBitmap(bitmap, 400, 300, true)
            setImageBitmap(vignetteBitmap)

            setOnClickListener {
                currentImageName = imageName
                binding.drawingView.imageBitmap = bitmap
                val zones = imageDataMap[imageName] ?: mutableListOf()
                binding.drawingView.zones.clear()
                binding.drawingView.zones.addAll(zones)
                binding.drawingView.invalidate()
            }
        }

        frameLayout.addView(imageView)
        container.addView(frameLayout)
    }

    private fun showAdventureSetupDialog() {
        val editText = android.widget.EditText(this).apply { hint = "Nom de l'aventure" }
        folderPathView = android.widget.TextView(this).apply {
            text = "Aucun dossier sélectionné"
            setPadding(0, 16, 0, 16)
        }

        val chooseFolderButton = android.widget.Button(this).apply {
            text = "Choisir un dossier"
            setOnClickListener {
                folderPathView?.text = "Sélection en cours..."
                openFolderPicker()
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
            addView(editText)
            addView(folderPathView)
            addView(chooseFolderButton)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Nouvelle aventure")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    adventureName = name
                    dialog.dismiss()
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentImageName?.let { outState.putString("currentImage", it) }
        outState.putString("adventureName", adventureName)
        outState.putString("selectedFolder", selectedFolderName)
        adventureUri?.let { outState.putString("adventureUri", it.toString()) }

        val zoneBundle = Bundle()
        for ((imageName, zones) in imageDataMap) {
            zoneBundle.putParcelableArrayList(imageName, ArrayList(zones))
        }
        outState.putBundle("zoneMap", zoneBundle)
    }
}
