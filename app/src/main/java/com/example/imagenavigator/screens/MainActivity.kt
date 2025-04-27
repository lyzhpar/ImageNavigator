package com.example.imagenavigator.screens

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.imagenavigator.databinding.ActivityMenuBinding
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast

/**
 * Menu principal avec deux boutons : Éditeur et Navigateur.
 * Utilise ViewBinding pour gérer les clics.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clique sur "éditeur"
        binding.editorButton.setOnClickListener {
            val intent = Intent(this, EditorActivity_OLD::class.java)
            startActivity(intent)
        }

        // Clique sur "navigateur"
        binding.navigatorButton.setOnClickListener {
            val intent = Intent(this, NavigatorActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("CONFIG_CHANGE", "Orientation changed to: ${newConfig.orientation}")
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(this, "L'application fonctionne uniquement en mode paysage.", Toast.LENGTH_SHORT).show()
        }
    }
}