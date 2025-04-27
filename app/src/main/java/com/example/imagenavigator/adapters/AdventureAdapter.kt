package com.example.imagenavigator.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.imagenavigator.R

class AdventureAdapter(
    private val onAdventureClick: (String) -> Unit
) : RecyclerView.Adapter<AdventureAdapter.AdventureViewHolder>() {

    private val adventures = mutableListOf<String>()

    fun submitList(newList: List<String>) {
        adventures.clear()
        adventures.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdventureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_adventure, parent, false)
        return AdventureViewHolder(view)
    }

    override fun getItemCount(): Int = adventures.size

    override fun onBindViewHolder(holder: AdventureViewHolder, position: Int) {
        holder.bind(adventures[position])
    }

    inner class AdventureViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val adventureNameTextView: TextView = view.findViewById(R.id.adventureName)

        fun bind(name: String) {
            adventureNameTextView.text = name
            itemView.setOnClickListener { onAdventureClick(name) }
        }
    }
}