package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.graphics.text.LineBreaker
import android.os.*
import android.view.*
import androidx.fragment.app.DialogFragment
import com.upang.hkfacilitator.databinding.DialogConfirmBinding
import com.upang.hkfacilitator.models.Global
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import java.util.Locale
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("ClickableViewAccessibility")
class ConfirmDialog(
    private val title: String,
    private val message: String,
    private val positive: String,
    private val negative: String? = null,
    private val isPINHidden: Boolean,
    private val onComplete: () -> Unit
): DialogFragment() {

    private lateinit var binding: DialogConfirmBinding
    private lateinit var dialog: AlertDialog
    private var pinDisabled = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogConfirmBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.title.text = title.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.message.text = message
        binding.btnPositive.text = positive
        if (negative != null) binding.btnNegative.apply {
            text = negative
            visibility = View.VISIBLE
        }

        if (title == "REGISTER" || title == "SECURITY NOTICE" ||
            title == "SUBMIT REQUEST" || title == "UPLOAD REQUIREMENT" ||
            title == "SUBMIT REMARKS" || title == "UPLOAD EXCEL" ||
            title == "CONFIRM ASSIGN" || title == "JOIN SCHEDULE" ||
            title == "NOTIFICATIONS AND SYNC" || title == "NOTIFICATIONS" ||
            title == "WITHDRAW SCHEDULE" || title == "SUSPEND SCHEDULES" ||
            title == "UPDATE REQUIRED" || title == "ALARMS AND REMINDERS") {
            binding.message.gravity = Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                binding.message.justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
        }

        if (!isPINHidden) {
            binding.pinRequire.visibility = View.VISIBLE
            pinListener()
            binding.checkBox.setOnCheckedChangeListener { _, checked ->
                pinDisabled = checked
            }
        }

        binding.btnNegative.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnPositive.setOnDebouncedClickListener {
            clear()
            confirmAction()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun confirmAction() {
        if (!isPINHidden) {
            if (isPINValid()) {
                val pin = "${binding.pin.text.trim()}"
                if (pin.hashSHA256() == Global.account!!.pin) {
                    Global.isPINDisabled = pinDisabled
                    dialog.dismiss()
                    onComplete()
                } else binding.pin.error = "Incorrect PIN"
            } else binding.pin.error = "Required"
        } else {
            onComplete()
            dialog.dismiss()
        }
    }

    private fun isPINValid(): Boolean = binding.pin.text.length == 6

    private fun pinListener() {
        binding.pin.setOnFocusChangeListener { _, focused ->
            val pin = "${binding.pin.text.trim()}"
            if (!focused) {
                if (pin.isNotEmpty()) {
                    binding.pin.error = null
                    if (pin.length < 6) binding.pin.error = "Must be 6 digits"
                } else binding.pin.error = "Required"
            }
        }
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}