package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.view.MotionEvent
import androidx.fragment.app.DialogFragment
import com.upang.hkfacilitator.databinding.DialogBugReportBinding
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("ClickableViewAccessibility")
class BugReport(
    private val callback : (String, String?) -> Unit
)
    : DialogFragment()
{
    private lateinit var binding : DialogBugReportBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogBugReportBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSubmit.setOnDebouncedClickListener {
            val desc = binding.description.text.toString()
            val rep = binding.recreate.text.toString()
            if (desc.isNotEmpty()) {
                if (desc.length > 20) {
                    callback(desc, rep)
                    dialog.dismiss()
                } else binding.description.error = "Please provide more information"
            } else binding.description.error = "Please input bug/error description"
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}