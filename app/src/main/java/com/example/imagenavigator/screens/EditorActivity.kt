package com.example.imagenavigator.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color // NOTE: Ajout de l'import pour Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout // Ajout de l'import pour FrameLayout
import android.widget.ImageView // NOTE: Ajout de l'import pour ImageView
import android.widget.LinearLayout
import android.widget.TextView // NOTE: Ajout de l'import pour TextView
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

    // NOTE: ✅ Stocke les zones par image
    private val imageDataMap: MutableMap<String, MutableList<Zone>> = mutableMapOf()

    // NOTE: Stocke les mondes (dossiers) et les images associées
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
        }
        setContentView(binding.root)

        val isPortrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        binding.startGuideline.setGuidelineBegin(if (isPortrait) 0 else 120)
        binding.bottomGuideline.setGuidelineEnd(if (isPortrait) 120 else 0)
        binding.sidebarLeft.visibility = if (isPortrait) View.GONE else View.VISIBLE
        binding.bottomBar.visibility = if (isPortrait) View.VISIBLE else View.GONE

        if (savedInstanceState == null) {
            showAdventureSetupDialog()
        }

        // Set aspect ratio for drawingView after layoutParams and padding setup
        val layoutParams = binding.drawingView.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.dimensionRatio = "4:3"
        binding.drawingView.layoutParams = layoutParams

        // NOTE: ✅ Ajoute la zone dessinée dans imageDataMap pour l'image courante
        binding.drawingView.onZoneCreated = { zone ->
            currentImageName?.let { name ->
                val zones = imageDataMap.getOrPut(name) { mutableListOf() }
                zones.add(zone)
            }
        }

        // openFolderPicker()
        // TODO: Afficher une interface de création d'aventure :
        // 1. Entrer un nom d'aventure
        // 2. Choisir un dossier complet ou un monde (sous-dossier)
        // 3. Initialiser les données seulement après cette sélection
        // ✅ Reset : efface les zones de l’image courante
        binding.resetButton.setOnClickListener {
            currentImageName?.let { name ->
                imageDataMap[name]?.clear()
                binding.drawingView.zones.clear()
                binding.drawingView.invalidate()
                // ✅ Bien joué, feedback instantané
                // TODO : Ajouter un Toast ou effet visuel pour confirmer l'effacement
            }
        }

        binding.saveButton.setOnClickListener {
            // TODO : sauvegarder imageDataMap dans un fichier JSON
            // 💡 Étapes suggérées :
            // 1. Créer un AdventureConfig avec mainImage, worlds, links
            // 2. Utiliser Gson().toJson(adventureConfig)
            // 3. Écrire ce JSON dans un fichier local
        }
    }

    private fun openFolderPicker() {
        // NOTE: Lancer la fenêtre de sélection du dossier
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_OPEN_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FOLDER && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            adventureUri = treeUri

            // NOTE: Demander une permission persistante pour l'URI
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                // NOTE: Charger les images du dossier sélectionné
                val folder = DocumentFile.fromTreeUri(this@EditorActivity, treeUri) ?: return
                selectedFolderName = folder.name ?: ""
                folderPathView?.text = "Dossier sélectionné : $selectedFolderName"
                loadImagesFromFolder(treeUri)
            } catch (e: SecurityException) {
                Log.e(
                    "EditorActivity",
                    "Erreur lors de la prise de la permission persistante pour l'URI",
                    e
                )
                // NOTE: Gérer l'erreur ici, comme afficher un message à l'utilisateur
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
                        file.name?.lowercase()?.endsWith(".gif") == true
                    ) {

                        val inputStream: InputStream? = contentResolver.openInputStream(file.uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        Log.d("ImageLoader", "Fichier : ${file.name}, bitmap = ${bitmap != null}")
                        inputStream?.close()

                        if (bitmap != null && file.name != null) {
                            imageFiles.add(bitmap to file.name!!)
                            // NOTE: Calculer le monde et enregistrer l'image dans worldsMap
                            val relativePath =
                                file.uri.path?.substringAfterLast("document/") ?: file.name!!
                            val world = relativePath.substringBeforeLast(
                                '/',
                                missingDelimiterValue = "racine"
                            )
                            val imageName = file.name!!

                            val imagesInWorld = worldsMap.getOrPut(world) { mutableListOf() }
                            imagesInWorld.add(imageName)

                            Log.d("ImageLoader", "Ajouté à la liste : ${file.name}")
                        } else {
                            Log.w("ImageLoader", "Impossible de décoder : ${file.name}")
                        }
                    }
                }
            }

            traverseFolder(folder)

            withContext(Dispatchers.Main) {
            val container = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT)
                binding.imageListHorizontal else binding.imageListVertical

            container.removeAllViews()

            imageFiles.forEach { (bitmap, name) ->
                    Log.d("ImageLoader", "Ajout dans la vue : $name")
                    addImageToSidebar(bitmap, name, container)
            }
                Log.d("WorldsMap", "Structure des mondes : $worldsMap")

                if (currentImageName != null) {
                    val matching = imageFiles.find { it.second == currentImageName }
                    if (matching != null) {
                        binding.drawingView.imageBitmap = matching.first
                        val zones = imageDataMap[currentImageName] ?: mutableListOf()
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
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = false
            setBackgroundColor(Color.parseColor("#DDDDDD"))
            setPadding(2, 2, 2, 2)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            isEnabled = true
            visibility = View.VISIBLE

            val vignetteBitmap = Bitmap.createScaledBitmap(bitmap, 400, 300, true)
            setImageBitmap(vignetteBitmap)

            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
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

        frameLayout.setOnClickListener {
            Log.d("VIGNETTE", "Frame cliqué : $imageName → on déclenche le click de l'image")
            imageView.performClick()
        }

        frameLayout.addView(imageView)
        container.addView(frameLayout)
        container.invalidate()
        container.requestLayout()

        if (!imageDataMap.containsKey(imageName)) {
            imageDataMap[imageName] = mutableListOf()
        }
    }

    private fun showAdventureSetupDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "Nom de l'aventure"
        }

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

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
            addView(editText)
            addView(this@EditorActivity.folderPathView)
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
                    // NOTE: Ici tu peux appeler openFolderPicker() ou une autre étape d'initialisation
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
