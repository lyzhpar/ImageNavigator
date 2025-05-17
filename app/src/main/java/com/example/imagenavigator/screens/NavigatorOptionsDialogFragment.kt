import android.app.Dialog
import android.content.Context
import android.view.View
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.example.imagenavigator.R
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout


class NavigatorOptionsDialogFragment(
    private val onEditClick: () -> Unit,
    private val onChangeBgClick: () -> Unit,
    private val onTransitionClick: () -> Unit,
    private val onGoToStartClick: () -> Unit,
    private val onReturnToMainClick: () -> Unit,
    private val onToggleZones: () -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_navigator_menu)

        val prefs = requireContext().getSharedPreferences("navigator_prefs", Context.MODE_PRIVATE)

        val colorViews = listOf(
            dialog.findViewById<ImageView>(R.id.colorWhite),
            dialog.findViewById<ImageView>(R.id.colorGrey),
            dialog.findViewById<ImageView>(R.id.colorBlack)
        )

        val colorMap = mapOf(
            R.id.colorWhite to Color.WHITE,
            R.id.colorGrey to Color.parseColor("#F0F0F0"),
            R.id.colorBlack to Color.BLACK
        )

        fun updateSelection(selectedId: Int) {
            colorViews.forEach { it?.isSelected = it?.id == selectedId }
        }

        colorViews.forEach { imageView ->
            imageView?.setOnClickListener {
                val selectedColor = colorMap[imageView.id] ?: Color.WHITE
                prefs.edit().putInt("background_color", selectedColor).apply()
                updateSelection(imageView.id)
                onChangeBgClick()
            }
        }

// Initialisation selon couleur enregistrée
        val savedColor = prefs.getInt("background_color", Color.WHITE)
        val selectedId = colorMap.entries.find { it.value == savedColor }?.key
        updateSelection(selectedId ?: R.id.colorWhite)

        val root = dialog.findViewById<FrameLayout>(R.id.outsideContainer)
        val menu = dialog.findViewById<View>(R.id.menuContainer)

        root?.setOnClickListener {
            Log.d("NavigatorOptions", "Clic à l'extérieur → fermeture du menu")
            dismiss()
        }
        menu?.setOnClickListener {
            Log.d("NavigatorOptions", "Clic à l'intérieur → ne pas fermer")
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        isCancelable = true

        dialog.findViewById<Button>(R.id.buttonEdit).setOnClickListener {
            onEditClick()
            dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.buttonBackground).setOnClickListener {
            onChangeBgClick()
            dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonTransition).setOnClickListener {
            onTransitionClick()
            dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonStartImage).setOnClickListener {
            onGoToStartClick()
            dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonReturnMain).setOnClickListener {
            onReturnToMainClick()
            dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonToggleZones).setOnClickListener {
            onToggleZones()
            dismiss()
        }

        return dialog
    }
}