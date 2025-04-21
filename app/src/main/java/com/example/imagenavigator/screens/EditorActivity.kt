package com.example.imagenavigator.screens

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.imagenavigator.databinding.ActivityEditorBinding
import com.example.imagenavigator.model.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import com.example.imagenavigator.R
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import android.widget.ProgressBar
import kotlinx.coroutines.delay


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
    private lateinit var imageListVerticalContainer: LinearLayout




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        // 🔒 Forcer le mode paysage pour cette activité
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // 🧱 Lier la vue avec ViewBinding
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🖼️ Plein écran sans barre de statut ni navigation
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        actionBar?.hide()
        supportActionBar?.hide()

        // 📌 Récupérer la référence de la liste de vignettes
        imageListVerticalContainer = findViewById(R.id.imageListVertical)

        // 🖋️ Afficher le nom de l’aventure (et permettre de le modifier)
        val titleView = findViewById<TextView>(R.id.adventureTitle)
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
                    Log.d("ADVENTURE", "Nom modifié : $adventureName")
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        // 🔁 Restauration après rotation ou première ouverture
        if (savedInstanceState != null) {
            currentImageName = savedInstanceState.getString("currentImage")
            adventureName = savedInstanceState.getString("adventureName", "")
            titleView.text = adventureName
            selectedFolderName = savedInstanceState.getString("selectedFolder", "")
            Log.d("RESTORE", "Nom de l’aventure restauré : $adventureName")

            savedInstanceState.getString("adventureUri")?.let {
                adventureUri = Uri.parse(it)
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(adventureUri!!, flags)
                    Log.d("RESTORE", "Permission persistante restaurée")
                } catch (e: SecurityException) {
                    Log.e("RESTORE", "Erreur de permission persistante", e)
                }
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

        // 🎯 Zone de dessin — ajustement du layout
        val layoutParams = binding.drawingView.layoutParams as ConstraintLayout.LayoutParams
        binding.drawingView.layoutParams = layoutParams

        // ➕ Ajout d’une zone
        binding.drawingView.onZoneCreated = { zone ->
            currentImageName?.let { name ->
                val zones = imageDataMap.getOrPut(name) { mutableListOf() }
                zones.add(zone)
                Log.d("ZONE", "Zone ajoutée à l’image : $name")
            }
        }

        // 🧽 Réinitialiser les zones
        binding.resetButton.setOnClickListener {
            currentImageName?.let { name ->
                imageDataMap[name]?.clear()
                binding.drawingView.zones.clear()
                binding.drawingView.invalidate()
                Log.d("ZONE", "Zones effacées pour l’image : $name")
            }
        }

        // 💾 Sauvegarder (à implémenter plus tard)
        binding.saveButton.setOnClickListener {
            Toast.makeText(this, "Fonction de sauvegarde à implémenter", Toast.LENGTH_SHORT).show()
        }

        Log.d("LAYOUT_DEBUG", "Chargé en orientation: ${resources.configuration.orientation}")
    }

    // 📂 Ouvre un sélecteur de dossier
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_OPEN_FOLDER)
    }

    // ✅ Traite le résultat du sélecteur
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FOLDER && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            adventureUri = treeUri

            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                val folder = DocumentFile.fromTreeUri(this@EditorActivity, treeUri) ?: return
                selectedFolderName = folder.name ?: ""
                folderPathView?.text = "Dossier sélectionné : $selectedFolderName"
                loadImagesFromFolder(treeUri)

            } catch (e: SecurityException) {
                Log.e("EditorActivity", "Erreur permission URI", e)
            }
        }
    }

    private fun showProgress(show: Boolean) {
        val overlay = binding.loadingOverlay
        val progressBar = binding.loadingProgressBar

        if (show) {
            overlay?.apply {
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(200).start()
            }
            progressBar?.visibility = View.VISIBLE
            binding.editorRoot.isEnabled = false
        } else {
            overlay?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
                overlay.visibility = View.GONE
            }?.start()
            progressBar?.visibility = View.GONE
            binding.editorRoot.isEnabled = true
        }
    }


    // 🔄 Chargement récursif des images depuis le dossier
    private fun loadImagesFromFolder(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val folder = DocumentFile.fromTreeUri(this@EditorActivity, uri) ?: return@launch
            val imageFiles = mutableListOf<Pair<Bitmap, String>>()

            fun traverseFolder(folder: DocumentFile) {
                for (file in folder.listFiles()) {
                    if (file.isDirectory) {
                        traverseFolder(file)
                    } else if (file.name?.lowercase()?.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|gif)$")) == true) {
                        val inputStream: InputStream? = contentResolver.openInputStream(file.uri)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4 // ✅ réduit la taille (1=plein, 2=moitié, 4=quart)
                        }
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream?.close()

                        if (bitmap != null && file.name != null) {
                            imageFiles.add(bitmap to file.name!!)
                            val relativePath = file.uri.path?.substringAfterLast("document/") ?: file.name!!
                            val world = relativePath.substringBeforeLast('/', missingDelimiterValue = "racine")
                            val imageName = file.name!!
                            val imagesInWorld = worldsMap.getOrPut(world) { mutableListOf() }
                            imagesInWorld.add(imageName)

                            // ✅ Limite temporaire à 100 images
                            if (imageFiles.size >= 100) break
                        }
                    }
                }
            }

            traverseFolder(folder)

            withContext(Dispatchers.Main) {
                imageListVerticalContainer.removeAllViews()
                val density = resources.displayMetrics.density
                val widthPx = ViewGroup.LayoutParams.MATCH_PARENT
                val heightPx = (80 * density).toInt() // ✅ vignette plus petite

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
                    imageListVerticalContainer.addView(frameLayout)
                    if (!imageDataMap.containsKey(name)) {
                        imageDataMap[name] = mutableListOf()
                    }
                }

                imageListVerticalContainer.invalidate()
                imageListVerticalContainer.requestLayout()

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

    // 🧩 Boîte de dialogue de configuration de l'aventure
    private fun showAdventureSetupDialog() {
        val editText = EditText(this).apply { hint = "Nom de l'aventure" }
        folderPathView = TextView(this).apply {
            text = "Aucun dossier sélectionné"
            setPadding(0, 16, 0, 16)
        }

        val chooseFolderButton = Button(this).apply {
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
                    findViewById<TextView>(R.id.adventureTitle).text = adventureName
                    dialog.dismiss()
                } else {
                    if (adventureUri == null) folderPathView?.error = "Vous devez choisir un dossier"
                    if (name.isBlank()) editText.error = "Le nom est requis"
                }
            }
        }

        dialog.show()
    }

    // 💾 Sauvegarde de l’état en cas de rotation ou fermeture
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

    // 🔕 Rotation désactivée pour cette activité
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("CONFIG_CHANGE", "Orientation changed to: ${newConfig.orientation}")
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(this, "L'application fonctionne uniquement en mode paysage.", Toast.LENGTH_SHORT).show()
        }
    }
}