package com.example.imagenavigator.screens

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.imagenavigator.model.Zone
import android.util.Log
import android.view.GestureDetector
import com.example.imagenavigator.model.ZoneData
import com.example.imagenavigator.model.toZoneData
import com.google.android.material.snackbar.Snackbar
import com.example.imagenavigator.utils.ThumbnailLoader

/**
 * Vue personnalisée qui permet d'afficher une image et de dessiner des zones rectangulaires
 * avec le doigt (drag & drop).
 */
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Indique si le bitmap est prêt à être affiché
    var isBitmapReady = false
    // Contrôle pour éviter les reloads multiples tant que le bitmap n'est pas revenu
    private var isReloading = false

    //Vignettes de ZONES
    data class EditorConfig(
        var thumbnailWidth: Int = 300,
        var thumbnailHeight: Int = 200,
        var thumbnailAlpha: Int = 200,
        var keepPanoramic: Boolean = true,
        var showLinkedThumbnails: Boolean = true,
        var margin: Float = 0f
    )
    var editorConfig = EditorConfig()

    init {
        isClickable = true
        isLongClickable = true
    }

    var bitmapProvider: (() -> Bitmap?)? = null
    var requestReload: ((String) -> Unit)? = null


    //Liste des zones visibles pour cette image
    val zones: MutableList<Zone> = mutableListOf()

    // Callback appelée quand une nouvelle zone est créée
    var onZoneCreated: ((Zone) -> Unit)? = null

    var onTapListener: (() -> Unit)? = null

    var onZoneSelected: (() -> Unit)? = null

    var selectedZone: Zone? = null
    var currentImageName: String? = null
    var imageExistChecker: ((String) -> Boolean)? = null

    private val linkedThumbnails = mutableMapOf<ZoneData, Bitmap>()
    // Stocke les rectangles des vignettes et leur imagePath associé
    private val thumbnailRects = mutableMapOf<RectF, String>() // imagePath associé à chaque vignette



    /**
     * Charge un bitmap dans la vue et déclenche un redraw.
     */
    fun loadImage(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            Log.e("DrawingView", "loadImage → Bitmap invalide, bitmap=$bitmap, isRecycled=${bitmap?.isRecycled}")
            return
        }
        bitmapProvider = { bitmap }
        isReloading = false
        isBitmapReady = true
        invalidate()
    }

    fun clearLinkedThumbnails() {
        linkedThumbnails.clear()
        invalidate()
    }

    fun reloadLinkedThumbnailsForCurrentImage() {
        linkedThumbnails.clear()

        val bitmap = bitmapProvider?.invoke()
        if (bitmap == null || bitmap.isRecycled) return

        zones.filter { it.linkedImagePath != null }.forEach { zone ->
            val uri = (context as? EditorActivity)?.imageFileMap?.get(zone.linkedImagePath!!)?.uri
            if (uri != null) {
                ThumbnailLoader.load(context, uri) { bitmap, _ ->
                    if (zone.linkedImagePath != null) {
                        linkedThumbnails[zone.toZoneData()] = bitmap
                        invalidate()
                    }
                }
            }
        }
    }

    /**
     * Charge une nouvelle liste de zones à afficher pour l'image courante.
     * Cela remplace toutes les zones actuellement affichées.
     */
    fun setZonesForCurrentImage(newZones: List<Zone>) {
        val bitmap = bitmapProvider?.invoke()
        if (bitmap == null || bitmap.isRecycled) {
            Log.e("DrawingView", "Bitmap invalide → bitmap=$bitmap, isRecycled=${bitmap?.isRecycled}")
            return
        }
        Log.d("DrawingView", "setZonesForCurrentImage → zones=${newZones.map { it.rect }}")
        zones.clear()
        zones.addAll(newZones)
        (context as? EditorActivity)?.updateDeleteButtonVisibilityForZones()
        Log.d("ZonesForCurrentImage", "Zones actuelles → count=${zones.size}, liste=${zones.map { it.rect }}")
        invalidate()
    }

    fun getAllZones(): MutableList<Zone> = zones

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Log.d("DrawingView", "onSingleTapUp détecté")
            onTapListener?.invoke()
            return true
        }
        // onLongPress supprimé (plus de sélection multiple)
    })

    private val paintBorder = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private var drawingRect: RectF? = null
    private var startX = 0f
    private var startY = 0f

    override fun onDraw(canvas: Canvas) {
        Log.d("onDraw", "Zones actuelles → count=${zones.size}, liste=${zones.map { it.rect }}")

        super.onDraw(canvas)

        // On efface la map des rectangles de vignettes à chaque draw
        thumbnailRects.clear()

        if (!isBitmapReady) {
            Log.d("DrawingView", "onDraw → Bitmap pas encore prêt, on saute le draw")
            return
        }
        val bitmap = bitmapProvider?.invoke()
        if (bitmap == null || bitmap.isRecycled) {
            Log.e("DrawingView", "onDraw → Bitmap invalide, demande de reload")
            isBitmapReady = false
            if (!isReloading) {
                isReloading = true
                Log.e("DrawingView", "Bitmap manquant ou recyclé → demande de reload")
                currentImageName?.let { requestReload?.invoke(it) }
            }
            Log.e("DrawingView", "onDraw → Bitmap invalide, bitmap=$bitmap, isRecycled=${bitmap?.isRecycled}")
            return
        }
        isReloading = false
        Log.d("DrawingView", "onDraw → Bitmap affiché: $bitmap")
        val dstRect = getImageDisplayRect(bitmap)

        // Dessiner l’image
        canvas.drawBitmap(bitmap, null, dstRect, null)

        // Dessiner les zones enregistrées
        for (zone in zones) {
            val r = zone.rect
            val absLeft = dstRect.left + r.left * dstRect.width()
            val absTop = dstRect.top + r.top * dstRect.height()
            val absRight = dstRect.left + r.right * dstRect.width()
            val absBottom = dstRect.top + r.bottom * dstRect.height()
            val absRect = RectF(absLeft, absTop, absRight, absBottom)

            //TODO:Config couleur zones
            val zonePaint = Paint().apply {
                color = when {
                    zone == selectedZone -> Color.argb(150, 255, 165, 0) // orange semi-transparent
                    zone.linkedImagePath != null -> Color.argb(60, 0, 255, 0) // vert semi-transparent
                    else -> Color.argb(150, 128, 128, 128) // gris semi-transparent
                }
                style = Paint.Style.FILL
            }

            canvas.drawRect(absRect, zonePaint)
        }

        // Dessiner temporairement le rectangle qu'on est en train de tracer
        drawingRect?.let {
            val tempPaint = Paint().apply {
                color = Color.argb(150, 128, 128, 128) // gris semi-transparent
                style = Paint.Style.FILL
            }
            canvas.drawRect(it, tempPaint)
            canvas.drawRect(it, paintBorder)
        }

        if (editorConfig.showLinkedThumbnails) {
            val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = editorConfig.thumbnailAlpha
            }

            linkedThumbnails.forEach { (zone, bmp) ->
                val dstRect = getImageDisplayRect(bitmapProvider?.invoke() ?: return@forEach)
                val absLeft = dstRect.left + zone.rect.left * dstRect.width()
                val absTop = dstRect.top + zone.rect.top * dstRect.height()
                val absRight = dstRect.left + zone.rect.right * dstRect.width()
                val absBottom = dstRect.top + zone.rect.bottom * dstRect.height()
                val absRect = RectF(absLeft, absTop, absRight, absBottom)

                // On passe la zone pour récupérer thumbRect afin d'enregistrer le rectangle interactif
                val linkedZone = zones.find { it.toZoneData() == zone }
                drawLinkedThumbnail(canvas, bmp, absRect, linkedZone)
            }
        }
    }

    // Ajout d'un paramètre facultatif zone pour enregistrer le thumbRect interactif
    private fun drawLinkedThumbnail(canvas: Canvas, bmp: Bitmap, absRect: RectF, zone: Zone? = null) {
        if (bmp.isRecycled) {
            Log.e("DrawingView", "Bitmap recyclé détecté pour la vignette, on saute le draw")
            return
        }

        val thumbnailWidth = editorConfig.thumbnailWidth.toFloat()
        val thumbnailHeight = editorConfig.thumbnailHeight.toFloat()
        val margin = editorConfig.margin

        val srcAspect = bmp.width.toFloat() / bmp.height.toFloat()
        val targetAspect = thumbnailWidth / thumbnailHeight

        val srcRect: Rect = if (editorConfig.keepPanoramic && srcAspect > targetAspect) {
            // Image panoramique plus large que la cible
            val newWidth = (bmp.height * targetAspect).toInt()
            val left = (bmp.width - newWidth) / 2
            Rect(left, 0, left + newWidth, bmp.height)
        } else if (editorConfig.keepPanoramic) {
            // Image plus haute que large
            val newHeight = (bmp.width / targetAspect).toInt()
            val top = (bmp.height - newHeight) / 2
            Rect(0, top, bmp.width, top + newHeight)
        } else {
            Rect(0, 0, bmp.width, bmp.height)
        }

        val paint = Paint().apply { alpha = editorConfig.thumbnailAlpha }

        // Position de la vignette par défaut : au-dessus à gauche de la zone
        val thumbLeft = absRect.left
        val thumbTop = absRect.top - thumbnailHeight - margin
        val thumbRect = RectF(thumbLeft, thumbTop, thumbLeft + thumbnailWidth, thumbTop + thumbnailHeight)

        // ✅ Vérification des débordements
        val displayWidth = width.toFloat()
        val displayHeight = height.toFloat()

        // Si trop à gauche
        if (thumbRect.left < margin) {
            thumbRect.offsetTo(margin, thumbRect.top)
        }

        // Si trop en haut
        if (thumbRect.top < margin) {
            thumbRect.offsetTo(thumbRect.left, absRect.bottom + margin)
        }

        // Si trop à droite
        if (thumbRect.right > displayWidth - margin) {
            val newLeft = displayWidth - thumbnailWidth - margin
            thumbRect.offsetTo(newLeft, thumbRect.top)
        }

        // Si trop en bas (rare)
        if (thumbRect.bottom > displayHeight - margin) {
            val newTop = displayHeight - thumbnailHeight - margin
            thumbRect.offsetTo(thumbRect.left, newTop)
        }

        // Dessin final
        canvas.drawBitmap(bmp, srcRect, thumbRect, paint)

        // Enregistre le rectangle et le chemin de l'image liée pour interaction
        if (zone != null && zone.linkedImagePath != null) {
            thumbnailRects[RectF(thumbRect)] = zone.linkedImagePath!!
        }
    }

    fun setLinkedThumbnailBitmap(zone: ZoneData, bitmap: Bitmap) {
        if (zone.linkedImagePath == null) return
        linkedThumbnails[zone] = bitmap
        invalidate()
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap = bitmapProvider?.invoke()
        Log.d("DrawingView", "onTouchEvent() → action=${event.action}, bitmap=$bitmap, isRecycled=${bitmap?.isRecycled}")
        if (bitmap == null || bitmap.isRecycled) {
            Log.e("DrawingView", "Bitmap nul ou recyclé détecté, onTouchEvent interrompu")
            return false
        }
        Log.d("DrawingView", "onTouchEvent: ${event.action}, selectedZone=$selectedZone")

        // Si le geste est traité, on ne fait rien d’autre

        // Clique sur une vignette → charger l'image liée
        thumbnailRects.forEach { (rect, path) ->
            if (rect.contains(event.x, event.y)) {
                (context as? EditorActivity)?.let { activity ->
                    activity.onImageSelected(path, ImageClickSource.ZONE_THUMBNAIL, allowScroll = true)
                }
                return true // Ne pas laisser l’événement sélectionner une zone derrière
            }
        }

        val dstRect = getImageDisplayRect(bitmap)
        val x = event.x
        val y = event.y
        val clampedX = x.coerceIn(dstRect.left, dstRect.right)
        val clampedY = y.coerceIn(dstRect.top, dstRect.bottom)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = clampedX
                startY = clampedY
                drawingRect = RectF(startX, startY, startX, startY)
            }
            MotionEvent.ACTION_MOVE -> {
                drawingRect?.set(
                    minOf(startX, clampedX),
                    minOf(startY, clampedY),
                    maxOf(startX, clampedX),
                    maxOf(startY, clampedY)
                )
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                drawingRect?.let { rect ->
                    val movementX = Math.abs(rect.right - rect.left)
                    val movementY = Math.abs(rect.bottom - rect.top)

                    if (movementX < 10f && movementY < 10f) {
                        val touchX = event.x
                        val touchY = event.y
                        for (zone in zones) {
                            val absLeft = dstRect.left + zone.rect.left * dstRect.width()
                            val absTop = dstRect.top + zone.rect.top * dstRect.height()
                            val absRight = dstRect.left + zone.rect.right * dstRect.width()
                            val absBottom = dstRect.top + zone.rect.bottom * dstRect.height()
                            val absRect = RectF(absLeft, absTop, absRight, absBottom)

                            if (absRect.contains(touchX, touchY)) {
                                selectedZone = zone
                                onZoneSelected?.invoke()
                                invalidate()
                                (context as? EditorActivity)?.updateDeleteButtonVisibilityForZones()
                                return true // Empêche d'appeler onTapListener
                            }
                        }
                        // Tap en dehors des zones
                        selectedZone = null
                        onZoneSelected?.invoke()
                        invalidate()
                        (context as? EditorActivity)?.updateDeleteButtonVisibilityForZones()
                        return true // Empêche aussi onTapListener ici
                    } else {
                        // Grand mouvement : créer une nouvelle zone
                        val relative = RectF(
                            (rect.left - dstRect.left) / dstRect.width(),
                            (rect.top - dstRect.top) / dstRect.height(),
                            (rect.right - dstRect.left) / dstRect.width(),
                            (rect.bottom - dstRect.top) / dstRect.height()
                        )
                        val zone = Zone(rect = relative)
                        zones.add(zone)
                        onZoneCreated?.invoke(zone)
                        Log.d("DrawingView", "Nouvelle zone créée : ${relative.left}, ${relative.top}, ${relative.right}, ${relative.bottom}")
                        invalidate()
                    }
                }
                drawingRect = null
            }
        }

        return true
    }


    /**
     * Calcule la zone de l'écran où l'image est affichée.
     */
    private fun getImageDisplayRect(bitmap: Bitmap): RectF {
        Log.d("DrawingView", "getImageDisplayRect() → width=$width, height=$height, bitmap.width=${bitmap.width}, bitmap.height=${bitmap.height}")
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2

        return RectF(left, top, left + scaledWidth, top + scaledHeight)
    }

    /**
     * Associe simplement le chemin de l'image liée à la zone sélectionnée.
     * Déclenche le redessin de la vue pour afficher la modification.
     * Désactive toutes les sélections avant de mettre à jour l'affichage du bouton.
     */
    fun assignLinkedImageToSelectedZone(imagePath: String) {
        selectedZone?.let {
            Log.d("DrawingView", "assignLinkedImageToSelectedZone → imagePath=$imagePath, currentImageName=$currentImageName")
            if (imagePath == currentImageName) {
                Log.d("DrawingView", "Impossible de lier une zone à la même image.")
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "C'est la même image !",
                    Snackbar.LENGTH_LONG
                ).show()
                return
            }
            it.linkedImagePath = imagePath
            Log.d("assignLinked", "Zones actuelles → count=${zones.size}, liste=${zones.map { it.rect }}")
            selectedZone = null
            invalidate()
            (context as? EditorActivity)?.hideDeleteZonesButton()
            Log.d("DebugLink", "assignLinkedImageToSelectedZone → zone=$selectedZone, imagePath=$imagePath")
        }
    }

    /**
     * Vérifie si l'image liée à la zone sélectionnée existe.
     * Retourne true si l'image est trouvée, false sinon.
     */
    fun hasLinkedImage(): Boolean {
        val path = selectedZone?.linkedImagePath
        if (path.isNullOrEmpty()) {
            Log.d("DrawingView", "Aucune image liée à cette zone.")
            return false
        }
        val exists = imageExistChecker?.invoke(path) ?: false
        if (!exists) {
            Log.d("DrawingView", "Image liée introuvable ou invalide : $path")
            return false
        }
        return true
    }

    // spotlightActive et spotlight methods supprimés

    fun deleteSelectedZones() {
        Log.d("DeleteZones", "Zones : ${zones.size}")

        val zonesToDelete = selectedZone?.let { listOf(it) } ?: emptyList()

        if (zonesToDelete.isEmpty()) {
            Log.d("DeleteZones", "Aucune zone sélectionnée.")
            return
        }

        zones.removeAll(zonesToDelete)
        zonesToDelete.map { it.toZoneData() }.forEach { linkedThumbnails.remove(it) }

        (context as? EditorActivity)?.updateImageDataMap(zones)
        (context as? EditorActivity)?.refreshThumbnailZones()

        selectedZone = null
        drawingRect = null
        startX = 0f
        startY = 0f
        invalidate()
        (context as? EditorActivity)?.updateDeleteButtonVisibilityForZones()
    }


    // fun hasSelectedZones() supprimée (remplacée par selectedZone != null si besoin)

}
