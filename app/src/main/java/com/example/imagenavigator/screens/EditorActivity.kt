package com.example.imagenavigator.screens
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.content.res.Configuration
import com.example.imagenavigator.R
import android.view.MotionEvent

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
    private lateinit var imageListHorizontalContainer: LinearLayout
    private lateinit var imageListVerticalContainer: LinearLayout

    // Initialise l'activité, restaure les états précédents si disponibles, et prépare les écouteurs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val scrollHorizontal = findViewById<HorizontalScrollView>(R.id.imageListHorizontal)
        val scrollVertical = findViewById<ScrollView>(R.id.imageListVertical)
        imageListHorizontalContainer = findViewById(R.id.linearImageListHorizontal)
        imageListVerticalContainer = findViewById(R.id.linearImageListVertical)

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

    // Ouvre le sélecteur de dossier pour choisir les images
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_OPEN_FOLDER)
    }

    // Gère le résultat du sélecteur de dossier et lance le chargement des images
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

    // Parcourt récursivement le dossier sélectionné pour charger les images
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
                imageListHorizontalContainer.removeAllViews()
                imageListVerticalContainer.removeAllViews()
                val isPortrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                val container = if (isPortrait) imageListHorizontalContainer else imageListVerticalContainer

                container?.let {
                    // Définir la taille cible uniforme des vignettes
                    val density = resources.displayMetrics.density
                    val widthPx = if (isPortrait) ViewGroup.LayoutParams.MATCH_PARENT else (120 * density).toInt()
                    val heightPx = if (isPortrait) (100 * density).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT

                    imageFiles.forEach { (bitmap, name) ->
                        val frameLayout = FrameLayout(this@EditorActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx).apply {
                                setMargins(8, 8, 8, 8)
                            }
                        }

                        val imageView = ImageView(this@EditorActivity).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            adjustViewBounds = true
                            setImageBitmap(bitmap)
                            setBackgroundColor(Color.parseColor("#DDDDDD"))

                            setOnClickListener {
                                currentImageName = name
                                binding.drawingView.imageBitmap = bitmap
                                val zones = imageDataMap[name] ?: mutableListOf()
                                binding.drawingView.zones.clear()
                                binding.drawingView.zones.addAll(zones)
                                binding.drawingView.invalidate()
                            }
                        }

                        frameLayout.addView(imageView)
                        it.addView(frameLayout)

                        if (!imageDataMap.containsKey(name)) {
                            imageDataMap[name] = mutableListOf()
                        }
                    }

                    it.invalidate()
                    it.requestLayout()
                }

                // Recharge l'image courante
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

    // Ajoute une vignette de l'image dans la barre latérale ou inférieure selon l'orientation
    private fun addImageToSidebar(bitmap: Bitmap, imageName: String, container: LinearLayout) {
        val frameLayout = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f // chaque vignette prend une part égale de la sidebar
            ).apply {
                setMargins(8, 8, 8, 8)
            }
        }

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(2, 2, 2, 2)
            setImageBitmap(Bitmap.createScaledBitmap(bitmap, 400, 300, true))

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    Log.d("VIGNETTE", "TOUCH détecté sur $imageName")
                    currentImageName = imageName
                    binding.drawingView.imageBitmap = bitmap
                    val zones = imageDataMap[imageName] ?: mutableListOf()
                    binding.drawingView.zones.clear()
                    binding.drawingView.zones.addAll(zones)
                    binding.drawingView.invalidate()
                }
                true
            }
        }

        frameLayout.addView(imageView)
        frameLayout.setOnClickListener { imageView.performClick() }
        container.addView(frameLayout)

        // 🔄 Redemande un recalcul du layout
        container.invalidate()
        container.requestLayout()

        if (!imageDataMap.containsKey(imageName)) {
            imageDataMap[imageName] = mutableListOf()
        }
    }

    // Affiche une boîte de dialogue pour entrer le nom de l'aventure et choisir un dossier
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

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Nouvelle aventure")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val name = editText.text.toString()
                if (name.isNotBlank() && adventureUri != null) {
                    adventureName = name
                    dialog.dismiss()
                } else {
                    if (adventureUri == null) {
                        folderPathView?.error = "Vous devez choisir un dossier"
                    }
                    if (name.isBlank()) {
                        editText.error = "Le nom est requis"
                    }
                }
            }
        }

        dialog.show()
    }

    // Sauvegarde l’état actuel de l’activité (image courante, zones, etc.)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT

        binding.sidebarLeft.visibility = if (isPortrait) View.GONE else View.VISIBLE
        binding.bottomBar.visibility = if (isPortrait) View.VISIBLE else View.GONE
        binding.imageListHorizontal.visibility = if (isPortrait) View.VISIBLE else View.GONE
        binding.imageListVertical.visibility = if (isPortrait) View.GONE else View.VISIBLE

        imageListHorizontalContainer = binding.linearImageListHorizontal
        imageListVerticalContainer = binding.linearImageListVertical

        if (isPortrait) {
            // Déplacer les vues verticales vers horizontal
            while (imageListVerticalContainer.childCount > 0) {
                val child = imageListVerticalContainer.getChildAt(0)
                imageListVerticalContainer.removeViewAt(0)
                imageListHorizontalContainer.addView(child)
            }
        } else {
            // Déplacer les vues horizontales vers vertical
            while (imageListHorizontalContainer.childCount > 0) {
                val child = imageListHorizontalContainer.getChildAt(0)
                imageListHorizontalContainer.removeViewAt(0)
                imageListVerticalContainer.addView(child)
            }
        }

        binding.sidebarLeft.requestLayout()
        binding.bottomBar.requestLayout()
        binding.imageListHorizontal.requestLayout()
        binding.imageListVertical.requestLayout()
    }
}
