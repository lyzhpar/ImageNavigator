package com.example.imagenavigator.screens

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.imagenavigator.databinding.ActivityMenuBinding

/**
 * Menu principal avec deux boutons : Éditeur et Navigateur.
 * Utilise ViewBinding pour gérer les clics.
 */
class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clique sur "éditeur"
        binding.editorButton.setOnClickListener {
            val intent = Intent(this, EditorActivity::class.java)
            startActivity(intent)
        }

        // Clique sur "navigateur"
        binding.navigatorButton.setOnClickListener {
            val intent = Intent(this, NavigatorActivity::class.java)
            startActivity(intent)
        }
    }
}