package com.example.imagenavigator.screens

import android.net.Uri

import android.content.Intent
import android.os.Bundle
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imagenavigator.adapters.AdventureAdapter
import com.example.imagenavigator.adapters.Adventure
import com.example.imagenavigator.databinding.ActivityMainBinding
import com.example.imagenavigator.model.Adventure as ModelAdventure
import com.google.gson.GsonBuilder
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
            onAdventureClick = { adventureName, folderUriString ->
                val folderUri = folderUriString?.let { Uri.parse(it) }
                val file = File(filesDir, "${adventureName}_zones.json")
                if (file.exists()) {
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(this, NavigatorActivity::class.java)
                    intent.putExtra("adventureJsonUri", fileUri)
                    intent.putExtra("folderUri", folderUri?.toString())
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Très important pour donner accès au fichier
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Fichier d'aventure introuvable.", Toast.LENGTH_SHORT).show()
                }
            },
            onAdventureEdit = { adventureName, folderUri ->
                Log.d("AdventureAdapter", "Lancement de l’édition pour $adventureName")
                val intent = Intent(this, EditorActivity::class.java).apply {
                    putExtra("adventureName", adventureName)
                    putExtra("folderUri", folderUri?.toString())
                }
                startActivity(intent)
            },

            onAdventureRename = { adventureName ->
                showRenameDialog(adventureName)
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

        val gson = GsonBuilder().setPrettyPrinting().create()
        val adventures = adventureFiles.mapNotNull { file ->
            try {
                val adventure = gson.fromJson(file.readText(), ModelAdventure::class.java)
                adventure
            } catch (e: Exception) {
                Log.e("MainActivity", "Erreur lors du parsing de ${file.name}: ${e.message}")
                null
            }
        }
        adventureAdapter.submitList(adventures.map {
            Adventure(
                name = it.adventureTitle,
                folderUri = it.folderUri
            )
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("CONFIG_CHANGE", "Orientation changed to: ${newConfig.orientation}")
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(this, "L'application fonctionne uniquement en mode paysage.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showRenameDialog(oldName: String) {
        val editText = android.widget.EditText(this)
        editText.setText(oldName)
        editText.setSingleLine(true)
        editText.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Renommer l’aventure")
            .setView(editText)
            .setPositiveButton("Renommer") { _, _ ->
                val newName = editText.text.toString()
                renameAdventureFile(oldName, newName)
            }
            .setNegativeButton("Annuler", null)
            .create()

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val newName = editText.text.toString()
                renameAdventureFile(oldName, newName)
                dialog.dismiss()
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    private fun renameAdventureFile(oldName: String, newName: String) {
        val oldFile = File(filesDir, "${oldName}_zones.json")
        val newFile = File(filesDir, "${newName}_zones.json")
        if (oldFile.exists()) {
            if (newFile.exists()) {
                Toast.makeText(this, "Un fichier portant ce nom existe déjà.", Toast.LENGTH_SHORT).show()
            } else {
                val gson = GsonBuilder().setPrettyPrinting().create()
                val adventure = gson.fromJson(oldFile.readText(), ModelAdventure::class.java)
                adventure.adventureTitle = newName
                val updatedJson = gson.toJson(adventure)
                val renamed = oldFile.renameTo(newFile)
                if (renamed) {
                    newFile.writeText(updatedJson)
                    Toast.makeText(this, "Aventure renommée en $newName", Toast.LENGTH_SHORT).show()
                    loadAdventureList()
                } else {
                    Toast.makeText(this, "Erreur lors du renommage.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}