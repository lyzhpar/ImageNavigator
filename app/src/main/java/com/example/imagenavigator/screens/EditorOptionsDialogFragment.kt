import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import androidx.fragment.app.DialogFragment
import com.example.imagenavigator.R

class EditorOptionsDialogFragment(
    private val showImageThumbnails: Boolean,
    private val showSidebarZones: Boolean,
    private val showZoneThumbnails: Boolean,
    private val useCondensedSidebar: Boolean,
    private val onOptionsConfirmed: (Boolean, Boolean, Boolean, Boolean) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_editor_options, null)

        val checkboxThumbnails = view.findViewById<CheckBox>(R.id.checkbox_thumbnails)
        val checkboxZones = view.findViewById<CheckBox>(R.id.checkbox_zones)
        val checkboxZoneThumbnails = view.findViewById<CheckBox>(R.id.checkbox_zone_thumbnails)
        val checkboxCondensed = view.findViewById<CheckBox>(R.id.checkbox_condensed)

        checkboxThumbnails.isChecked = showImageThumbnails
        checkboxZones.isChecked = showSidebarZones
        checkboxZoneThumbnails.isChecked = showZoneThumbnails
        checkboxCondensed.isChecked = useCondensedSidebar

        builder.setView(view)
            .setTitle("Options de l'éditeur")
            .setPositiveButton("OK") { _, _ ->
                onOptionsConfirmed(
                    checkboxThumbnails.isChecked,
                    checkboxZones.isChecked,
                    checkboxZoneThumbnails.isChecked,
                    checkboxCondensed.isChecked
                )
            }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }
}