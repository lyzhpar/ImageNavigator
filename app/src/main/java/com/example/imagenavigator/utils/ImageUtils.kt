
import java.io.File
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileOutputStream
import java.io.IOException






fun isValidImage(file: File): Boolean {
    // Vérifie que le fichier a une extension d'image connue
    val name = file.getName().lowercase(Locale.getDefault())
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(
        ".gif"
    ) || name.endsWith(".webp")
}


fun resizeImage(file: File, maxWidth: Int, maxHeight: Int): Bitmap? {
    // Décodage de l'image avec les options pour ne pas charger l'image en entier au départ
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }

    // Calcul de l'échelle pour redimensionner l'image tout en maintenant le ratio
    val ratioBitmap = Math.min(
        options.outWidth.toFloat() / maxWidth.toFloat(),
        options.outHeight.toFloat() / maxHeight.toFloat()
    )
    var inSampleSize = if (ratioBitmap > 1) {
        Math.round(ratioBitmap)
    } else {
        1
    }

    // Décodage de l'image avec l'échelle calculée
    val optionsInSample = BitmapFactory.Options().apply {
        inSampleSize = inSampleSize
    }

    return BitmapFactory.decodeFile(file.absolutePath, optionsInSample)
}


fun convertToWebP(bitmap: Bitmap, outputFile: File): Boolean {
    return try {
        FileOutputStream(outputFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, 80, outputStream) // Compression à 80% de qualité
        }
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}