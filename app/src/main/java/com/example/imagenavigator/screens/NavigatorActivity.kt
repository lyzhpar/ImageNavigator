package com.example.imagenavigator.screens

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.imagenavigator.R
import com.example.imagenavigator.databinding.ActivityNavigatorBinding
import com.example.imagenavigator.model.ImageData
import com.example.imagenavigator.model.Zone

/**
 * Activité qui affiche une image en plein écran avec des zones cliquables.
 */
class NavigatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigatorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Afficher un bouton de retour discret
        binding.backButton.setOnClickListener {
            finish() // Ferme simplement l'activité
        }

        // Charger une image de test depuis drawable
        val image = BitmapFactory.decodeResource(resources, R.drawable.test_image)
        binding.drawingView.imageBitmap = image

        // Créer une zone cliquable fictive
        val demoZone = Zone(
            rect = android.graphics.RectF(0.3f, 0.3f, 0.6f, 0.6f), // coordonnées relatives
            targetImageName = null,
            audioFileName = null
        )

        // Affecter les zones à dessiner
        binding.drawingView.zones.clear()
        binding.drawingView.zones.add(demoZone)
        binding.drawingView.invalidate()
    }
}