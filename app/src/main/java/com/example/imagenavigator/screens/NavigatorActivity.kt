package com.example.imagenavigator.screens

import NavigatorOptionsDialogFragment
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.os.postDelayed
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.imagenavigator.databinding.ActivityNavigatorBinding
import com.example.imagenavigator.model.Adventure
import com.example.imagenavigator.model.ZoneData
import com.google.gson.Gson
import java.io.InputStreamReader
import android.os.Handler
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Button
import com.example.imagenavigator.R

class NavigatorActivity : BaseActivity() {

    private lateinit var binding: ActivityNavigatorBinding
    private lateinit var adventure: Adventure
    private lateinit var folderUri: Uri
    private var currentImageName: String? = null
    private var zonesVisible = true
    private val historyStack = mutableListOf<String>()
    var downTime = 0L
    val handler = Handler(Looper.getMainLooper())
    private lateinit var adventureName: String


    private val prefs by lazy {
        getSharedPreferences("navigator_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigatorBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val savedColor = prefs.getInt("background_color", Color.WHITE)
        binding.frameLayout.setBackgroundColor(savedColor)

        zonesVisible = prefs.getBoolean("zones_visible", true)
        binding.overlayView.setZonesVisible(zonesVisible)

        Log.d("NavigatorActivity", "Intent extras : ${intent.extras}")
        val keys = intent.extras?.keySet()
        Log.d("NavigatorActivity", "Intent keys: $keys")
        val folderUriExtraString = intent.getStringExtra("folderUri")
        folderUri = if (folderUriExtraString != null) {
            Uri.parse(folderUriExtraString)
        } else {
            Log.w("NavigatorActivity", "folderUri manquant, utilisation du mock temporaire")
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADATA")
        }
        Log.d("NavigatorActivity", "folderUri extra: $folderUri")
        val jsonUri = intent.getParcelableExtra<Uri>("adventureJsonUri")
        Log.d("NavigatorActivity", "jsonUri extra: $jsonUri")
        if (jsonUri == null) {
            Toast.makeText(this, "Erreur : aventure manquante.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.backButton.setOnClickListener {
            goBack()
        }

        binding.backButton.setOnLongClickListener {
            showContextMenu()
            true
        }

        /*val longClickRunnable = Runnable {
            doLongClickAction()
        }

        val veryLongClickRunnable = Runnable {
            handler.removeCallbacks(longClickRunnable) // ← empêche le long clic
            doVeryLongClickAction()
        }*/

        /*binding.backButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    handler.postDelayed(longClickRunnable, 1000)
                    handler.postDelayed(veryLongClickRunnable, 3000)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longClickRunnable)
                    handler.removeCallbacks(veryLongClickRunnable)

                    if (System.currentTimeMillis() - downTime < 1000) {
                        doShortClickAction()
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }*/




        // Configure le clic sur les zones
        binding.overlayView.onZoneClicked = { targetPath ->
            navigateToImage(targetPath)
        }



        loadAdventure(jsonUri)
    }


    /*private fun showContextMenu() {
        val view = layoutInflater.inflate(R.layout.dialog_navigator_menu, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(view)

        view.findViewById<Button>(R.id.buttonBackground)?.setOnClickListener {
            dialog.dismiss()
            showBackgroundColorDialog()
        }

        view.findViewById<Button>(R.id.buttonTransition)?.setOnClickListener {
            dialog.dismiss()
            showTransitionDialog()
        }

        view.findViewById<Button>(R.id.buttonEdit)?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, EditorActivity::class.java).apply {
                putExtra("adventureName", adventureName)
                putExtra("folderUri", folderUri.toString())
                // Ajoute toujours l'extra "imagePath" avec la valeur de currentImageName
                putExtra("imagePath", currentImageName)
                putExtra("editMode", true)
            }
            startActivity(intent)
        }

        view.findViewById<Button>(R.id.buttonStart)?.setOnClickListener {
            dialog.dismiss()
            currentImageName = adventure.startImagePath
            showCurrentImage()
        }

        view.findViewById<Button>(R.id.buttonMain)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        view.findViewById<Button>(R.id.buttonToggleZones)?.setOnClickListener {
            dialog.dismiss()
            zonesVisible = !zonesVisible
            binding.overlayView.setZonesVisible(zonesVisible)
            prefs.edit().putBoolean("zones_visible", zonesVisible).apply()
        }

        dialog.show()
    }*/

    // Affiche un menu contextuel en plein écran avec les options de navigation
    private fun showContextMenu() {
        val menuDialog = NavigatorOptionsDialogFragment(
            onEditClick = {
                val intent = Intent(this, EditorActivity::class.java).apply {
                    putExtra("adventureName", adventureName)
                    putExtra("folderUri", folderUri.toString())
                    putExtra("imagePath", currentImageName)
                    putExtra("editMode", true)
                }
                startActivity(intent)
            },
            onChangeBgClick = { showBackgroundColorDialog() },
            onTransitionClick = { showTransitionDialog() },
            onGoToStartClick = { showCurrentImage() },
            onReturnToMainClick = {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            },
            onToggleZones = {
                zonesVisible = !zonesVisible
                binding.overlayView.setZonesVisible(zonesVisible)
                prefs.edit().putBoolean("zones_visible", zonesVisible).apply()
            }
        )
        menuDialog.isCancelable = true
        // Retain setCanceledOnTouchOutside(true) if used in DialogFragment
        menuDialog.show(supportFragmentManager.beginTransaction().setReorderingAllowed(true), "NavigatorOptions")
    }


    private fun loadAdventure(jsonUri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(jsonUri) ?: run {
                Toast.makeText(this, "Erreur : impossible d'ouvrir le fichier JSON.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            adventure = Gson().fromJson(reader, Adventure::class.java)
            adventureName = adventure.adventureTitle
            Log.d("NavigatorActivity", "Adventure chargé : ${adventure.adventureTitle}, images: ${adventure.images.size}")
            reader.close()
            val startImageExists = adventure.images.any { it.imageName.trim() == adventure.startImagePath?.trim() }
            currentImageName = if (startImageExists) {
                adventure.startImagePath
            } else {
                val fallback = adventure.images.firstOrNull()?.imageName
                Log.w("NavigatorActivity", "Start image not found, using fallback: $fallback")
                fallback
            }
            if (currentImageName == null) {
                Toast.makeText(this, "Aucune image trouvée dans l’aventure.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            Log.d("NavigatorActivity", "Image de départ : $currentImageName")
            showCurrentImage()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur de chargement.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showCurrentImage() {
        currentImageName?.let { fullImagePath ->
            val folderDocument = DocumentFile.fromTreeUri(this, folderUri)
            if (folderDocument == null) {
                Toast.makeText(this, "Erreur : dossier inaccessible.", Toast.LENGTH_SHORT).show()
                return
            }
            Log.d("NavigatorActivity", "Chargement de l'image : $fullImagePath")
            val imageFile = findFileRecursively(folderDocument, fullImagePath)
            if (imageFile != null) {
                Glide.with(this)
                    .load(imageFile.uri)
                    .transition(withCrossFade(300))
                    .listener(object : RequestListener<Drawable> {
                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.d("DEBUG", "bitmap $fullImagePath loaded with width=${resource.intrinsicWidth}, height=${resource.intrinsicHeight}")
                            binding.imageView.post {
                                val width = binding.imageView.width
                                val height = binding.imageView.height
                                Log.d("DEBUG", "NavigatorActivity → imageView size = ${width}x${height}")
                                if (width > 0 && height > 0 && resource != null) {
                                    val bitmapWidth = resource.intrinsicWidth.toFloat()
                                    val bitmapHeight = resource.intrinsicHeight.toFloat()
                                    binding.overlayView.bitmapWidth = bitmapWidth
                                    binding.overlayView.bitmapHeight = bitmapHeight

                                    val viewAspect = width / height.toFloat()
                                    val imageAspect = bitmapWidth / bitmapHeight
                                    val scale: Float
                                    val scaledWidth: Float
                                    val scaledHeight: Float
                                    if (imageAspect > viewAspect) {
                                        scale = width / bitmapWidth
                                        scaledWidth = width.toFloat()
                                        scaledHeight = bitmapHeight * scale
                                    } else {
                                        scale = height / bitmapHeight
                                        scaledWidth = bitmapWidth * scale
                                        scaledHeight = height.toFloat()
                                    }
                                    binding.overlayView.imageDisplayWidth = scaledWidth
                                    binding.overlayView.imageDisplayHeight = scaledHeight
                                    val offsetX = (width - scaledWidth) / 2f
                                    val offsetY = (height - scaledHeight) / 2f
                                    binding.overlayView.imageOffsetX = offsetX
                                    binding.overlayView.imageOffsetY = offsetY
                                    binding.overlayView.invalidate()
                                    Log.d(
                                        "NavigatorActivity",
                                        "updateImageBounds → scaledWidth=$scaledWidth, scaledHeight=$scaledHeight, bitmapWidth=$bitmapWidth, bitmapHeight=$bitmapHeight, offsetX=$offsetX, offsetY=$offsetY"
                                    )
                                }
                            }
                            return false
                        }

                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            Log.e("NavigatorActivity", "Glide load failed", e)
                            return false
                        }
                    })
                    .into(binding.imageView)

                val currentImageData = adventure.images.find { it.imageName.trim() == fullImagePath.trim() }
                val zones = currentImageData?.zones ?: emptyList()

                // Log imageBitmapMap and zoneMap if accessible
                try {
                    val imageBitmapMapField = this::class.java.getDeclaredField("imageBitmapMap")
                    imageBitmapMapField.isAccessible = true
                    val imageBitmapMap = imageBitmapMapField.get(this) as? Map<*, *>
                    if (imageBitmapMap != null) {
                        Log.d("DEBUG", "imageBitmapMap size = ${imageBitmapMap.size}")
                        Log.d("DEBUG", "imageBitmapMap keys = ${imageBitmapMap.keys}")
                    }
                } catch (e: Exception) {
                    // Field not found or inaccessible, ignore
                }
                try {
                    val zoneMapField = this::class.java.getDeclaredField("zoneMap")
                    zoneMapField.isAccessible = true
                    val zoneMap = zoneMapField.get(this) as? Map<*, *>
                    if (zoneMap != null) {
                        Log.d("DEBUG", "zoneMap size = ${zoneMap.size}")
                        Log.d("DEBUG", "zoneMap keys = ${zoneMap.keys}")
                    }
                } catch (e: Exception) {
                    // Field not found or inaccessible, ignore
                }

                applyZones(zones)
                binding.overlayView.postInvalidateOnAnimation()
                binding.overlayView.postDelayed({ binding.overlayView.invalidate() }, 300)
            } else {
                Toast.makeText(this, "Image \"$fullImagePath\" non trouvée dans le dossier.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToImage(targetPath: String) {
        currentImageName?.let { historyStack.add(it) }
        Log.d("NavigatorActivity", "Navigation vers : $targetPath")
        if (currentImageName != targetPath) {
            currentImageName = targetPath
            showCurrentImage()
        }
    }

    private fun applyZones(zones: List<ZoneData>) {
        Log.d("NavigatorActivity", "Zones appliquées (${zones.size}): ${zones.joinToString { it.linkedImagePath ?: "no-link" }}")
        zones.forEachIndexed { index, zone ->
            Log.d("NavigatorActivity", "Zone $index → rect: ${zone.rect}, linkedImagePath: ${zone.linkedImagePath}")
        }
        binding.overlayView.zones = zones
        binding.overlayView.postInvalidateOnAnimation()
    }

    private fun findFileRecursively(folder: DocumentFile?, relativePath: String): DocumentFile? {
        Log.d("NavigatorActivity", "Recherche récursive du fichier : $relativePath")
        if (folder == null || !folder.isDirectory) return null
        val cleanRelativePath = relativePath.replace("\\", "/").replace(Regex("/+"), "/")
        val segments = cleanRelativePath.split('/')
        var currentFolder = folder
        for (i in 0 until segments.size - 1) {
            val segment = segments[i]
            val nextFolder = currentFolder?.listFiles()?.firstOrNull { it.isDirectory && it.name?.trim() == segment.trim() }
            if (nextFolder == null) return null
            currentFolder = nextFolder
        }
        if (currentFolder == null) return null
        return currentFolder.listFiles()?.firstOrNull { !it.isDirectory && it.name == segments.last() }
    }

    /*
    fun doShortClickAction() {
        if (historyStack.isNotEmpty()) {
            currentImageName = historyStack.removeAt(historyStack.size - 1)
            showCurrentImage()
        } else {
            finish()
        }
    }

    fun doLongClickAction() {
        if (historyStack.isNotEmpty()) {
            currentImageName = historyStack.first()
            showCurrentImage()
        } else {
            finish()
        }
    }


    fun doVeryLongClickAction() {
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("adventureName", adventureName)
            putExtra("folderUri", folderUri?.toString())
        }
        startActivity(intent)
    }*/

    private fun goBack() {
            if (historyStack.isNotEmpty()) {
                currentImageName = historyStack.first()
                showCurrentImage()
            } else {
                finish()
            }
        }



    private fun showBackgroundColorDialog() {
        val options = arrayOf("Blanc", "Gris", "Noir")
        val colors = arrayOf(Color.WHITE, Color.parseColor("#F0F0F0"), Color.BLACK)

        AlertDialog.Builder(this)
            .setTitle("Choisir une couleur de fond")
            .setItems(options) { _, which ->
                binding.frameLayout.setBackgroundColor(colors[which])
                prefs.edit().putInt("background_color", colors[which]).apply()
            }
            .show()

    }

    private fun showTransitionDialog() {
        val types = arrayOf("Fondu", "Glissement gauche", "Glissement droite", "Zoom avant", "Zoom arrière")
        AlertDialog.Builder(this)
            .setTitle("Type de transition")
            .setItems(types) { _, which ->
                Toast.makeText(this, "Transition : ${types[which]} (à implémenter)", Toast.LENGTH_SHORT).show()
            }
            .show()
    }





}