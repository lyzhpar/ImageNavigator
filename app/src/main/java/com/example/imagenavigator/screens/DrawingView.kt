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

/**
 * Vue personnalisée qui permet d'afficher une image et de dessiner des zones rectangulaires
 * avec le doigt (drag & drop).
 */
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

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
    private var spotlightActive = false
    private var overlayAlpha = 255
    private val fadeHandler = Handler(Looper.getMainLooper())
    // Map pour associer les chemins d'images aux bitmaps
    private val imageBitmapMap = mutableMapOf<String, Bitmap>()

    /**
     * Charge une nouvelle liste de zones à afficher pour l'image courante.
     * Cela remplace toutes les zones actuellement affichées.
     */
    fun setZonesForCurrentImage(newZones: List<Zone>) {
        zones.clear()
        zones.addAll(newZones)
        invalidate()
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            Log.d("DrawingView", "onSingleTapUp détecté")
            // Si spotlight actif, désactiver au tap simple
            if (spotlightActive) {
                clearSpotlight()
                return true
            }
            onTapListener?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            Log.d("DrawingView", "onLongPress détecté - appel performLongClick()")
            performLongClick()
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

        Log.d("DrawingView", "onDraw avec bitmap = ${imageBitmap != null}")

        val bitmap = imageBitmap ?: return
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

            canvas.drawRect(absRect, paintZone)
            canvas.drawRect(absRect, paintBorder)
        }

        // Dessiner temporairement le rectangle qu'on est en train de tracer
        drawingRect?.let {
            canvas.drawRect(it, paintZone)
            canvas.drawRect(it, paintBorder)
        }


        if (spotlightActive && selectedZone != null) {
            Log.d("DrawingView", "Image liée : ${selectedZone?.linkedImagePath}")

            // Overlay pour spotlight
            val paintOverlay = Paint().apply {
                color = Color.argb(128, 255, 255, 255) // Blanc semi-transparent
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintOverlay)

            val r = selectedZone!!.rect
            val absLeft = dstRect.left + r.left * dstRect.width()
            val absTop = dstRect.top + r.top * dstRect.height()
            val absRight = dstRect.left + r.right * dstRect.width()
            val absBottom = dstRect.top + r.bottom * dstRect.height()
            val absRect = RectF(absLeft, absTop, absRight, absBottom)

            // Découper un "trou" sur la zone sélectionnée
            val clearPaint = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            canvas.drawRect(absRect, clearPaint)

            // Affichage de l'image liée ou "Pas d'aperçu" au centre du spotlight
            selectedZone?.linkedImagePath?.let { linkedPath ->
                val previewBitmap = imageBitmapMap[linkedPath]

                if (previewBitmap == null) {
                    val paintText = Paint().apply {
                        color = Color.WHITE
                        textSize = 30f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("Pas d'image liée", absRect.centerX(), absRect.centerY(), paintText)
                } else {
                    val previewSize = 150f // Taille fixe de l'aperçu
                    val centerX = absRect.centerX()
                    val centerY = absRect.centerY()
                    val left = centerX - previewSize / 2
                    val top = centerY - previewSize / 2
                    val right = centerX + previewSize / 2
                    val bottom = centerY + previewSize / 2

                    val previewRect = RectF(left, top, right, bottom)

                    val paintPreview = Paint().apply {
                        alpha = 128 // 50% d'opacité
                    }

                    // Dessiner la vignette de l'image liée
                    canvas.drawBitmap(previewBitmap, null, previewRect, paintPreview)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("DrawingView", "onTouchEvent: ${event.action}, spotlightActive=$spotlightActive, selectedZone=$selectedZone")
        gestureDetector.onTouchEvent(event)

        val bitmap = imageBitmap
        if (bitmap == null) {
            if (event.action == MotionEvent.ACTION_UP) {
                if (spotlightActive) {
                    clearSpotlight()
                }
            }
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
                                spotlightActive = true
                                overlayAlpha = 192
                                Log.d("DrawingView", "Spotlight activé sur zone : ${zone.rect.left}, ${zone.rect.top}, ${zone.rect.right}, ${zone.rect.bottom}")
                                invalidate()
                                foundZone = true
                                break
                            }
                        }
                        if (!foundZone && spotlightActive) {
                            // Tap en dehors des zones désactive le spotlight
                            clearSpotlight()
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

     // Associe une image liée à la zone sélectionnée et force le rafraîchissement.
     fun assignLinkedImageToSelectedZone(imagePath: String) {
         selectedZone?.let {
             it.linkedImagePath = imagePath
             Log.d("DrawingView", "Image liée : ${it.linkedImagePath}")
             invalidate()  // Redessiner la vue pour afficher l'image liée
         } ?: run {
             Log.d("DrawingView", "Aucune zone sélectionnée pour l'image.")
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
        val bitmap = android.graphics.BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            Log.d("DrawingView", "Image liée introuvable : $path")
            return false
        }
        return true
    }

    /**
     * Indique si le spotlight est actuellement actif (zone sélectionnée en surbrillance).
     */
    fun isSpotlightActive(): Boolean {
        return spotlightActive
    }

    /**
     * Désactive le spotlight et désélectionne la zone, puis redessine la vue.
     */
    fun clearSpotlight() {
        spotlightActive = false
        selectedZone = null
        invalidate()
    }

}
