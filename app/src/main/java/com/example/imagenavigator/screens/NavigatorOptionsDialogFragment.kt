import android.app.Dialog
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

        val prefs = requireContext().getSharedPreferences("navigator_prefs", android.content.Context.MODE_PRIVATE)

        val colorLayout = dialog.findViewById<android.widget.LinearLayout>(R.id.colorPickerLayout)
        val buttonBg = dialog.findViewById<Button>(R.id.buttonBackground)

        val buttonWhite = dialog.findViewById<Button>(R.id.buttonWhite)
        val buttonGrey = dialog.findViewById<Button>(R.id.buttonGrey)
        val buttonBlack = dialog.findViewById<Button>(R.id.buttonBlack)

        val allColorButtons = listOf(buttonWhite, buttonGrey, buttonBlack)
        val colorMap = mapOf(
            buttonWhite to Color.WHITE,
            buttonGrey to Color.parseColor("#F0F0F0"),
            buttonBlack to Color.BLACK
        )

        fun updateSelection(selected: Button?) {
            allColorButtons.forEach { it?.isSelected = it == selected }
        }

        buttonBg?.setOnClickListener {
            colorLayout?.visibility = if (colorLayout?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        colorMap.forEach { (button, color) ->
            button?.setOnClickListener {
                updateSelection(button)
                prefs.edit().putInt("background_color", color).apply()
                onChangeBgClick()
            }
        }

        // Initialiser sélection selon préférences
        val savedColor = prefs.getInt("background_color", Color.WHITE)
        colorMap.entries.find { it.value == savedColor }?.key?.isSelected = true

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
        dialog.findViewById<Button>(R.id.buttonBackground).setOnClickListener {
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