package com.example.imagenavigator.model

// TODO: Ajouter des méthodes utilitaires pour lire et écrire cette config depuis un fichier JSON
// TODO: Valider la cohérence des chemins d'images (existence, racine, etc.)
data class AdventureConfig(
    var name: String,
    var mainImage: String?, // chemin relatif de l'image de départ
    var worlds: MutableMap<String, MutableList<String>>, // dossier/monde -> liste des images
    var links: MutableMap<String, MutableList<ZoneLink>> // image source -> liste de zones cliquables
)

// ✅ La structure des zones fonctionne avec des coordonnées relatives
// TODO: Ajouter une méthode pour tester si un point (clic) est dans la zone
// TODO: Ajouter une option pour rendre la zone visible (affichage coloré par exemple)
data class ZoneLink(
    val rect: RectF, // zone sous forme de rectangle relatif à l'image
    var target: String, // image de destination
    var sound: String? = null // son optionnel à jouer
)