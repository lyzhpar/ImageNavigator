package com.example.imagenavigator.screens

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.imagenavigator.model.Zone
import android.util.Log
import android.view.GestureDetector
import android.os.Handler
import android.os.Looper
import kotlin.math.absoluteValue

/**
 * Vue personnalisée qui permet d'afficher une image et de dessiner des zones rectangulaires
 * avec le doigt (drag & drop).
 */
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var justDeletedZones = false

    init {
        isClickable = true
        isLongClickable = true
    }
    var imageBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    // Liste des zones visibles pour cette image
    val zones: MutableList<Zone> = mutableListOf()

    // Callback appelée quand une nouvelle zone est créée
    var onZoneCreated: ((Zone) -> Unit)? = null

    var onTapListener: (() -> Unit)? = null

    var selectedZone: Zone? = null
    private var overlayAlpha = 255
    private val fadeHandler = Handler(Looper.getMainLooper())
    // Map pour associer les chemins d'images aux bitmaps
    var imageBitmapMap = mutableMapOf<String, Bitmap>()

    private val selectedZonesMulti = mutableSetOf<Zone>()
    fun clearSelectedZones() {
        selectedZonesMulti.clear()
        invalidate()
    }

    /**
     * Charge une nouvelle liste de zones à afficher pour l'image courante.
     * Cela remplace toutes les zones actuellement affichées.
     */
    fun setZonesForCurrentImage(newZones: List<Zone>) {
        zones.clear()
        zones.addAll(newZones)
        invalidate()
    }

    fun getAllZones(): MutableList<Zone> = zones

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Log.d("DrawingView", "onSingleTapUp détecté")
            onTapListener?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val bitmap = imageBitmap ?: return
            val dstRect = getImageDisplayRect(bitmap)
            val x = e.x.coerceIn(dstRect.left, dstRect.right)
            val y = e.y.coerceIn(dstRect.top, dstRect.bottom)

            for (zone in zones) {
                val absLeft = dstRect.left + zone.rect.left * dstRect.width()
                val absTop = dstRect.top + zone.rect.top * dstRect.height()
                val absRight = dstRect.left + zone.rect.right * dstRect.width()
                val absBottom = dstRect.top + zone.rect.bottom * dstRect.height()
                val absRect = RectF(absLeft, absTop, absRight, absBottom)

                if (absRect.contains(x, y)) {
                    if (selectedZonesMulti.contains(zone)) {
                        selectedZonesMulti.remove(zone)
                    } else {
                        selectedZonesMulti.add(zone)
                    }
                    (context as? EditorActivity)?.updateDeleteButtonVisibilityForZones()
                    invalidate()
                    break
                }
            }
        }
    })

    private val paintZone = Paint().apply {
        color = Color.argb(128, 0, 255, 0)
        style = Paint.Style.FILL
    }

    private val paintBorder = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private var drawingRect: RectF? = null
    private var startX = 0f
    private var startY = 0f

   override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = imageBitmap
        if (bitmap == null || bitmap.isRecycled) {
            Log.e("DrawingView", "Bitmap nul ou recyclé, onDraw annulé")
            return
        }

        Log.d("DrawingView", "onDraw avec bitmap = ${bitmap != null}")

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

            // Afficher la vignette 50% transparente de l’image liée au-dessus de chaque zone, taille fixe
            zone.linkedImagePath?.let { linkedPath ->
                val linkedBitmap = imageBitmapMap[linkedPath]
                linkedBitmap?.let { bmp ->
                    drawLinkedThumbnail(canvas, bmp, absRect)
                }
            }

            val zonePaint = Paint().apply {
                color = when {
                    zone == selectedZone -> Color.argb(150, 255, 165, 0) // orange semi-transparent
                    zone.linkedImagePath != null -> Color.argb(150, 0, 255, 0) // vert semi-transparent
                    else -> Color.argb(150, 128, 128, 128) // gris semi-transparent
                }
                style = Paint.Style.FILL
            }

            canvas.drawRect(absRect, zonePaint)
        }

        // Dessiner temporairement le rectangle qu'on est en train de tracer
        drawingRect?.let {
            canvas.drawRect(it, paintZone)
            canvas.drawRect(it, paintBorder)
        }

        // Plus d'overlay blanc/spotlight ici
    }

    private fun drawLinkedThumbnail(canvas: Canvas, bmp: Bitmap, absRect: RectF) {
        val thumbnailWidth = 200
        val thumbnailHeight = 100
        val srcAspect = bmp.width.toFloat() / bmp.height.toFloat()
        val targetAspect = thumbnailWidth.toFloat() / thumbnailHeight.toFloat()
        val srcRect: Rect = if (srcAspect > targetAspect) {
            val newWidth = (bmp.height * targetAspect).toInt()
            val left = (bmp.width - newWidth) / 2
            Rect(left, 0, left + newWidth, bmp.height)
        } else {
            val newHeight = (bmp.width / targetAspect).toInt()
            val top = (bmp.height - newHeight) / 2
            Rect(0, top, bmp.width, top + newHeight)
        }
        val paint = Paint().apply { alpha = 200 }
        val thumbLeft = absRect.left
        val thumbTop = absRect.top - thumbnailHeight
        val thumbRect = RectF(thumbLeft, thumbTop, thumbLeft + thumbnailWidth, thumbTop + thumbnailHeight)
        canvas.drawBitmap(bmp, srcRect, thumbRect, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("DrawingView", "onTouchEvent: ${event.action}, selectedZone=$selectedZone")
        if (event.action == MotionEvent.ACTION_DOWN && selectedZonesMulti.isNotEmpty()) {
            clearSelectedZones()
            (context as? EditorActivity)?.hideDeleteZonesButton()
        }
        gestureDetector.onTouchEvent(event)

        val bitmap = imageBitmap
        if (bitmap != null && bitmap.isRecycled) {
            Log.e("DrawingView", "Bitmap recyclé détecté dans onTouchEvent, retour anticipé")
            return true
        }

        if (justDeletedZones) {
            justDeletedZones = false
            return true
        }

        if (bitmap == null) {
            return true
        }

        if (selectedZonesMulti.isNotEmpty()) {
            // Pas de sélection simple en mode sélection multiple
            return true
        }

        val dstRect = getImageDisplayRect(bitmap)
        val x = event.x.coerceIn(dstRect.left, dstRect.right)
        val y = event.y.coerceIn(dstRect.top, dstRect.bottom)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                drawingRect = RectF(startX, startY, startX, startY)
            }
            MotionEvent.ACTION_MOVE -> {
                drawingRect?.set(
                    minOf(startX, x),
                    minOf(startY, y),
                    maxOf(startX, x),
                    maxOf(startY, y)
                )
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                drawingRect?.let { rect ->
                    val movementX = Math.abs(rect.right - rect.left)
                    val movementY = Math.abs(rect.bottom - rect.top)

                    if (movementX < 10f && movementY < 10f) {
                        // Petit déplacement : considérer comme un clic
                        val touchX = event.x
                        val touchY = event.y
                        var foundZone = false
                        for (zone in zones) {
                            val absLeft = dstRect.left + zone.rect.left * dstRect.width()
                            val absTop = dstRect.top + zone.rect.top * dstRect.height()
                            val absRight = dstRect.left + zone.rect.right * dstRect.width()
                            val absBottom = dstRect.top + zone.rect.bottom * dstRect.height()
                            val absRect = RectF(absLeft, absTop, absRight, absBottom)

                            if (absRect.contains(touchX, touchY)) {
                                selectedZone = zone
                                overlayAlpha = 192
                                Log.d("DrawingView", "Zone sélectionnée : ${zone.rect.left}, ${zone.rect.top}, ${zone.rect.right}, ${zone.rect.bottom}")
                                invalidate()
                                foundZone = true
                                break
                            }
                        }
                        if (!foundZone) {
                            // Tap en dehors des zones désélectionne la zone
                            selectedZone = null
                            invalidate()
                        }
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
     */
    fun assignLinkedImageToSelectedZone(imagePath: String) {
        selectedZone?.let {
            it.linkedImagePath = imagePath
            Log.d("DrawingView", "Image liée à la zone : ${it.linkedImagePath}")
            selectedZone = null
            invalidate()
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
        val bitmap = try {
            android.graphics.BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            Log.e("DrawingView", "Erreur lors du décodage de l'image liée : $path", e)
            null
        }
        if (bitmap == null) {
            Log.d("DrawingView", "Image liée introuvable ou invalide : $path")
            return false
        }
        return true
    }

    // spotlightActive et spotlight methods supprimés

    fun deleteSelectedZones() {
        Log.d("DeleteZones", "Sélection : ${selectedZonesMulti.size}, Zones : ${zones.size}")
        if (selectedZonesMulti.isEmpty()) {
            Log.d("DeleteZones", "Aucune zone sélectionnée.")
            return
        }

        Log.d("DeleteZones", "Zones avant suppression: ${zones.size}")
        Log.d("DeleteZones", "Zones sélectionnées: ${selectedZonesMulti.size}")

        val selectedRects = selectedZonesMulti.map { it.rect }

        val toRemove = zones.filter { zone ->
            selectedRects.any { selectedRect ->
                areRectsEqual(zone.rect, selectedRect)
            }
        }

        Log.d("DeleteZones", "Zones à supprimer : $toRemove")
        Log.d("DeleteZones", "Contenu exact des zones : $zones")
        Log.d("DeleteZones", "Contenu exact des selectedZonesMulti : $selectedZonesMulti")
        zones.removeAll(toRemove)
        Log.d("DeleteZones", "zones.removeAll(toRemove) exécuté")

        Log.d("DeleteZones", "Zones après suppression: ${zones.size}")

        selectedZonesMulti.clear()
        justDeletedZones = true
        invalidate()
    }

    private fun areRectsEqual(rect1: RectF, rect2: RectF): Boolean {
        return (rect1.left - rect2.left).absoluteValue < 0.01f &&
                (rect1.top - rect2.top).absoluteValue < 0.01f &&
                (rect1.right - rect2.right).absoluteValue < 0.01f &&
                (rect1.bottom - rect2.bottom).absoluteValue < 0.01f
    }

    fun hasSelectedZones(): Boolean {
        return selectedZonesMulti.isNotEmpty()
    }

}
