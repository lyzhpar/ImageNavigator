
# 📚 Comportement de la Bottom Bar - ImageNavigator

---

## 🧩 1. Fonctionnalité

- **Mode normal** :
  - `Images : X`
  - `Mondes : Y`

- **Mode sélection** :
  - `Images sélectionnées : X`
  - `Dossiers sélectionnés : Y`
  - + bouton **"Mode sélection"** pour sortir manuellement du mode sélection.

---

## 🧩 2. XML - `bottom_bar_layout.xml`

Ajout d'un conteneur dynamique invisible :

```xml
<LinearLayout
    android:id="@+id/selectionInfoContainer"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:visibility="gone"
    android:gravity="center_vertical"
    android:layout_marginStart="16dp">

    <TextView
        android:id="@+id/selectedImagesCount"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Images sélectionnées : 0"
        android:textStyle="bold"
        android:layout_marginEnd="16dp"/>

    <TextView
        android:id="@+id/selectedWorldsCount"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Dossiers sélectionnés : 0"
        android:textStyle="bold"/>
</LinearLayout>
```

---

## 🧩 3. Kotlin - `EditorActivity.kt`

Déclaration des propriétés :

```kotlin
private lateinit var imagesInfoText: TextView
private lateinit var worldsInfoText: TextView
private lateinit var selectedImagesCount: TextView
private lateinit var selectedWorldsCount: TextView
private lateinit var selectionInfoContainer: View
private lateinit var selectionModeIndicator: TextView
```

Dans `onCreate()` :

```kotlin
val bottomBarView = binding.bottomBar.root
imagesInfoText = bottomBarView.findViewById(R.id.textImageCount)
worldsInfoText = bottomBarView.findViewById(R.id.textWorldCount)
selectedImagesCount = bottomBarView.findViewById(R.id.selectedImagesCount)
selectedWorldsCount = bottomBarView.findViewById(R.id.selectedWorldsCount)
selectionInfoContainer = bottomBarView.findViewById(R.id.selectionInfoContainer)
```

Ajout d'un bouton "Mode sélection" dynamique :

```kotlin
selectionModeIndicator = TextView(this).apply {
    text = "Mode sélection"
    visibility = View.GONE
    textSize = 16f
    setPadding(16, 0, 16, 0)
    setOnClickListener {
        exitSelectionMode()
        updateDeleteButtonVisibility(deleteButton)
        visibility = View.GONE
    }
}
binding.bottomBar.root.addView(selectionModeIndicator)
```

---

## 🧩 4. Nouvelle fonction centrale

```kotlin
private fun updateBottomBarInfo() {
    if (!::imagesInfoText.isInitialized || !::worldsInfoText.isInitialized ||
        !::selectedImagesCount.isInitialized || !::selectedWorldsCount.isInitialized ||
        !::selectionInfoContainer.isInitialized) return

    if (isSelectionMode) {
        val imageCount = selectedItems.count { !isGroupPath(it) }
        val folderCount = selectedItems.count { isGroupPath(it) }
        selectionInfoContainer.visibility = View.VISIBLE
        selectedImagesCount.text = "Images sélectionnées : $imageCount"
        selectedWorldsCount.text = "Dossiers sélectionnés : $folderCount"
        imagesInfoText.visibility = View.GONE
        worldsInfoText.visibility = View.GONE
    } else {
        selectionInfoContainer.visibility = View.GONE
        val totalImages = imageBitmapMap.size
        val totalWorlds = countTotalGroups(imageRootNode)
        imagesInfoText.visibility = View.VISIBLE
        worldsInfoText.visibility = View.VISIBLE
        imagesInfoText.text = "Images : $totalImages"
        worldsInfoText.text = "Mondes : $totalWorlds"
    }
}
```

---

## 🧩 5. Où `updateBottomBarInfo()` est appelée :

- Après toute sélection/désélection (`toggleSelection`)
- Après une suppression (`handleDeleteSelectedItems`)
- À la sortie du mode sélection (`exitSelectionMode`)
- Après chargement d'images (`loadImagesFromFolder`)

---

## 📦 Résultat

| Mode actuel  | Affichage |
|:---|:---|
| Normal | Images : X<br>Mondes : Y |
| Sélection | Images sélectionnées : X<br>Dossiers sélectionnés : Y<br>+ bouton "Mode sélection" |
