package com.example.imagenavigator.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.imagenavigator.R
import android.content.Context
import android.graphics.Color

class AdventureAdapter(
    private val onAdventureClick: (String) -> Unit,
    private val onAdventureEdit: (String) -> Unit,
    private val onAdventureDelete: (String) -> Unit
) : RecyclerView.Adapter<AdventureAdapter.AdventureViewHolder>() {

    private val adventures = mutableListOf<String>()
    private var selectedPosition: Int? = null
    private val linkedImagePaths = mutableSetOf<String>()  // 🆕 Stocke les images liées

    private val DEBUG_MODE = true // 🆕 Active ou désactive les Toasts pour debug

    fun submitList(newList: List<String>) {
        adventures.clear()
        adventures.addAll(newList)
        notifyDataSetChanged()
    }

    // 🆕 Méthode pour définir les chemins des images liées
    fun setLinkedImagePaths(paths: List<String>) {
        linkedImagePaths.clear()
        linkedImagePaths.addAll(paths)
        notifyDataSetChanged()  // Mettre à jour l'affichage avec les images liées
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdventureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_adventure, parent, false)
        return AdventureViewHolder(view)
    }

    override fun getItemCount(): Int = adventures.size

    override fun onBindViewHolder(holder: AdventureViewHolder, position: Int) {
        val adventure = adventures[position]
        holder.adventureName.text = adventure // Assuming you just need the name here
    }

    inner class AdventureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val adventureName: TextView = itemView.findViewById(R.id.adventureName)
        private val overlayContainer: View = itemView.findViewById(R.id.overlayContainer)
        private val buttonEdit: Button = itemView.findViewById(R.id.buttonEdit)
        private val buttonPlay: Button = itemView.findViewById(R.id.buttonPlay)
        private val buttonDelete: Button = itemView.findViewById(R.id.buttonDelete)

        fun bind(name: String) {
            adventureName.text = name
            overlayContainer.visibility = View.GONE

            itemView.setOnClickListener {
                logAndToast(itemView.context, "Adventure clicked: $name")
                onAdventureClick(name)
            }

            itemView.setOnLongClickListener {
                logAndToast(itemView.context, "Adventure long-pressed: $name")
                overlayContainer.visibility = View.VISIBLE
                true
            }

            buttonEdit.setOnClickListener {
                logAndToast(itemView.context, "Edit adventure: $name")
                overlayContainer.visibility = View.GONE
                onAdventureEdit(name)
            }

            buttonPlay.setOnClickListener {
                logAndToast(itemView.context, "Launch adventure: $name")
                overlayContainer.visibility = View.GONE
                onAdventureClick(name)
            }

            buttonDelete.setOnClickListener {
                logAndToast(itemView.context, "Delete adventure: $name")
                overlayContainer.visibility = View.GONE
                onAdventureDelete(name)
            }

            overlayContainer.setOnClickListener {
                logAndToast(itemView.context, "Overlay closed for: $name")
                overlayContainer.visibility = View.GONE
            }
        }

        private fun logAndToast(context: Context, message: String) {
            Log.d("AdventureAdapter", message)
            if (DEBUG_MODE) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}