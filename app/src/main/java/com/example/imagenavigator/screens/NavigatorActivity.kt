package com.example.imagenavigator.screens

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
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
import com.example.imagenavigator.R


class NavigatorActivity : BaseActivity() {

    private lateinit var binding: ActivityNavigatorBinding
    private lateinit var adventure: Adventure
    private lateinit var folderUri: Uri
    private var currentImageName: String? = null
    private val historyStack = mutableListOf<String>()
    private var loadingView: View? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.overlayView.onLongClickAt = { x, y ->
            lastTouchX = x
            lastTouchY = y
        }

        loadingView = layoutInflater.inflate(R.layout.loading_view, null)
        val loadingText = loadingView?.findViewById<TextView>(R.id.loadingText)
        val animator = ObjectAnimator.ofFloat(loadingText, View.ALPHA, 0.5f, 1f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        binding.root.addView(loadingView)

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)

        binding.backButton.setOnClickListener { goBack() }

        binding.overlayView.isClickable = true
        binding.overlayView.isLongClickable = true
        binding.overlayView.setOnLongClickListener {
            Log.d("NAVIGATOR", "Long-clic détecté sur overlayView")
            showContextMenu()
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == RESULT_OK) {
            folderUri = data?.data ?: return
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
            currentImageName = adventure.startImagePath ?: adventure.images.firstOrNull()?.imageName
            showCurrentImage()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur de chargement de l'aventure.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showCurrentImage() {
        currentImageName?.let { fullImagePath ->
            val folderDocument = DocumentFile.fromTreeUri(this, folderUri)
            var imageFile = findFileRecursively(folderDocument, fullImagePath)
            var correctedPath = fullImagePath

            if (imageFile == null) {
                correctedPath = correctImagePathIfNeeded(folderDocument, fullImagePath)
                imageFile = findFileRecursively(folderDocument, correctedPath)
                if (imageFile != null) currentImageName = correctedPath
            }

            if (imageFile != null) {
                Glide.with(this)
                    .load(imageFile.uri)
                    .transition(withCrossFade(300))
                    .override(2048, 2048)
                    .into(binding.imageView)
                removeLoadingView()
            } else {
                Toast.makeText(this, "Image non trouvée : $correctedPath", Toast.LENGTH_SHORT).show()
            }

            val currentImageData = adventure.images.find { it.imageName.trim() == correctedPath.trim() }
            val zones = currentImageData?.zones ?: emptyList()
            binding.overlayView.zones = zones
            binding.overlayView.onZoneClicked = { targetPath -> navigateToImage(targetPath) }
        }
    }

    private fun removeLoadingView() {
        loadingView?.let {
            binding.root.removeView(it)
            loadingView = null
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

    private fun findFileRecursively(folder: DocumentFile?, relativePath: String): DocumentFile? {
        if (folder == null || !folder.isDirectory) return null
        val cleanRelativePath = relativePath.replace("\\", "/").replace(Regex("/+"), "/")
        val segments = cleanRelativePath.split('/')
        var currentFolder = folder
        for (i in 0 until segments.size - 1) {
            currentFolder = currentFolder?.listFiles()?.firstOrNull { it.isDirectory && it.name == segments[i] }
        }
        return currentFolder?.listFiles()?.firstOrNull { !it.isDirectory && it.name == segments.last() }
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
        return matchingFile?.let {
            (segments.dropLast(1) + (it.name ?: targetName)).joinToString("/")
        } ?: path
    }

    private fun showContextMenu() {
        Log.d("NAVIGATOR", "Affichage du PopupWindow")
        val popupView = layoutInflater.inflate(R.layout.custom_popup_menu, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.showAtLocation(binding.root, Gravity.NO_GRAVITY, lastTouchX.toInt(), lastTouchY.toInt())

        popupView.findViewById<TextView>(R.id.option1).setOnClickListener {
            currentImageName = adventure.startImagePath ?: adventure.images.firstOrNull()?.imageName
            historyStack.clear()
            showCurrentImage()
            popupWindow.dismiss()
        }
        popupView.findViewById<TextView>(R.id.option2).setOnClickListener {
            Toast.makeText(this, "Fonction pas encore implémentée", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 123
    }
}
