package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.view.MotionEvent
import androidx.fragment.app.DialogFragment
import com.upang.hkfacilitator.databinding.DialogDaytimePickerBinding
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.showTimePicker
import com.upang.hkfacilitator.models.Global.stringToTime
import com.upang.hkfacilitator.models.Timestamp
import com.upang.hkfacilitator.utils.snackBar
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("SetTextI18n, ClickableViewAccessibility")
class DayTimePickerDialog(
    private val onAddClick: (Timestamp) -> Unit
) : DialogFragment() {
    private lateinit var binding: DialogDaytimePickerBinding
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogDaytimePickerBinding.inflate(layoutInflater)
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

        startListener()
        endListener()

        binding.btnStartPicker.setOnDebouncedClickListener {
            clear()
            showTimePicker(true)
        }

        binding.btnEndPicker.setOnDebouncedClickListener {
            clear()
            showTimePicker(false)
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnAdd.setOnDebouncedClickListener {
            addVacant()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun addVacant() {
        val day = binding.dayPicker.selectedItem.toString()
        val start = binding.start.text.toString().trim()
        val end = binding.end.text.toString().trim()
        if (!checkTime(start, true)) return
        if (!checkTime(end, false)) return
        if (!checkTimeRange(start, end)) return
        if (isConnected(requireContext())) {
            val prefix = when (day) {
                "Monday" -> "1"
                "Tuesday" -> "2"
                "Wednesday" -> "3"
                "Thursday" -> "4"
                "Friday" -> "5"
                "Saturday" -> "6"
                else -> "7"
            }
            val id = prefix + day.substring(0, 3).uppercase() +
                    start.replace(":", "") +
                    end.replace(":", "")
            onAddClick(Timestamp(id, "$day.$start - $end"))
            dialog.dismiss()
        } else snackBar(binding.root, "Network error. Please try again")
    }

    private fun showTimePicker(isStart: Boolean) {
        showTimePicker(requireContext()) { time ->
            if (isStart) {
                binding.start.error = null
                binding.start.setText(time)
            } else {
                binding.end.error = null
                binding.end.setText(time)
            }
        }
    }

    private fun checkTimeRange(start: String, end: String): Boolean {
        if (start.substringAfter(':').toInt() < 60 &&
            end.substringAfter(':').toInt() < 60
        ) {
            val timeStart = start.replace(":", "").toInt()
            val timeEnd = end.replace(":", "").toInt()
            return if (timeStart < timeEnd) {
                if (stringToTime(end) - stringToTime(start) < 30) {
                    binding.start.error = "Time range must be a minimum of 30 minutes"
                    binding.end.error = "Time range must be a minimum of 30 minutes"
                    false
                } else true
            } else {
                binding.start.error = "Should be a valid time range"
                binding.end.error = "Should be a valid time range"
                false
            }
        }
        if (start.substringAfter(':').toInt() > 59)
            binding.start.error = "Should be a valid time"
        if (end.substringAfter(':').toInt() > 59)
            binding.end.error = "Should be a valid time"
        return false
    }

    private fun startListener() {
        binding.start.setOnFocusChangeListener { _, focused ->
            val start = binding.start.text.toString().trim()
            if (!focused) checkTime(start, true)
        }
    }

    private fun endListener() {
        binding.end.setOnFocusChangeListener { _, focused ->
            val end = binding.end.text.toString().trim()
            if (!focused) checkTime(end, false)
        }
    }

    private fun checkTime(time: String, isStart: Boolean): Boolean {
        if (time.isNotEmpty()) {
            val regex = Regex("^\\d{2}:\\d{2}$")
            if (!regex.matches(time)) {
                return if (isStart) {
                    binding.start.error = "Should follow the format HH:MM"
                    false
                } else {
                    binding.end.error = "Should follow the format HH:MM"
                    false
                }
            }
            return true
        } else {
            return if (isStart) {
                binding.start.error = "Required"
                false
            } else {
                binding.end.error = "Required"
                false
            }
        }
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}