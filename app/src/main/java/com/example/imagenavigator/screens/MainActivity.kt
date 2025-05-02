package com.example.imagenavigator.screens

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imagenavigator.adapters.AdventureAdapter
import com.example.imagenavigator.databinding.ActivityMainBinding
import java.io.File

/**
 * Menu principal : créer une nouvelle aventure ou explorer les aventures existantes.
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adventureAdapter: AdventureAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialiser l'adapter (onAdventureClick)
        adventureAdapter = AdventureAdapter(
            onAdventureClick = { adventureName ->
                val file = File(filesDir, "${adventureName}_zones.json")
                if (file.exists()) {
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )

                    val intent = Intent(this, NavigatorActivity::class.java)
                    intent.putExtra("adventureJsonUri", fileUri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Très important pour donner accès au fichier
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Fichier d'aventure introuvable.", Toast.LENGTH_SHORT).show()
                }
            },
            onAdventureEdit = { adventureName ->
                val intent = Intent(this, EditorActivity::class.java)
                intent.putExtra("adventureName", adventureName)
                startActivity(intent)
            },
            onAdventureDelete = { adventureName ->
                deleteAdventureFile(adventureName)
            }
        )



        // Configurer le RecyclerView
        binding.recyclerViewAdventures.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adventureAdapter
        }

        loadAdventureList()

        // Clique sur "Créer une aventure"
        binding.editorButton.setOnClickListener {
            val intent = Intent(this, EditorActivity::class.java)
            startActivity(intent)
        }
    }

    private fun deleteAdventureFile(adventureName: String) {
        val file = File(filesDir, "${adventureName}_zones.json")
        if (file.exists()) {
            file.delete()
            Toast.makeText(this, "Aventure supprimée : $adventureName", Toast.LENGTH_SHORT).show()
            loadAdventureList() // Recharger la liste !
        }
    }

    override fun onResume() {
        super.onResume()
        // Recharger la liste quand on revient sur MainActivity
        loadAdventureList()
    }

    private fun loadAdventureList() {
        val adventureFiles = filesDir.listFiles { file ->
            file.extension == "json" && file.name.endsWith("_zones.json")
        } ?: emptyArray()

        val adventureNames = adventureFiles.map { file ->
            file.name.removeSuffix("_zones.json")
        }

        adventureAdapter.submitList(adventureNames)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("CONFIG_CHANGE", "Orientation changed to: ${newConfig.orientation}")
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(this, "L'application fonctionne uniquement en mode paysage.", Toast.LENGTH_SHORT).show()
        }
    }
}