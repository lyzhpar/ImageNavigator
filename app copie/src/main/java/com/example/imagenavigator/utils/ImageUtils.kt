
import java.io.File
import java.util.Locale

fun isValidImage(file: File): Boolean {
    // Vérifie que le fichier a une extension d'image connue
    val name = file.getName().lowercase(Locale.getDefault())
    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(
        ".gif"
    ) || name.endsWith(".webp")
}