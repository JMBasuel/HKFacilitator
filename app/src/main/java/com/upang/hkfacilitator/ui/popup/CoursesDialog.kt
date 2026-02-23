package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.upang.hkfacilitator.adapters.CourseAdapter
import com.upang.hkfacilitator.databinding.DialogCoursesBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.*
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible

@SuppressLint("SetTextI18n")
class CoursesDialog : DialogFragment(), CourseClickListener {

    private lateinit var binding: DialogCoursesBinding
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var dbRef: DatabaseReference
    private var title: String? = null
    private var code: String? = null
    private var type: Int? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogCoursesBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        binding.rvCourses.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        setupCourses()

        binding.btnExit.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnAdd.setOnDebouncedClickListener {
            swapCourse()
        }

        binding.addCourse.setOnDebouncedClickListener {
            binding.root.clearFocus()
            addCourse()
        }

        binding.cancelAdd.setOnClickListener {
            binding.root.clearFocus()
            swapCourse()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    override fun onDeleteClick(course: String) {
        val code = Global.courses!!.filter { it.contains(course) }.map{ it.substringAfter('.').substringBeforeLast('.') }[0]
        confirmDialog = ConfirmDialog("CONFIRM", "Are you sure you want to delete $code?",
            "Delete", "Cancel", true) {
            deleteCourse(course)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun deleteCourse(course: String) {
        setupProgress("Deleting course")
        val code = course.substringAfter('.').substringBeforeLast('.')
        Global.courses!!.remove(course)
        dbRef.child("/Courses").setValue(Global.courses)
            .addOnSuccessListener {
                binding.loading.container.visibility = View.GONE
                snackBar(binding.root, "$code has been deleted")
                binding.rvCourses.adapter = CourseAdapter(Global.courses!!, this@CoursesDialog)
            }
            .addOnFailureListener {
                binding.loading.container.visibility = View.GONE
                snackBar(binding.root, "Error: ${it.message}")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    private fun setupCourses() {
        binding.rvCourses.adapter = Global.courses?.let { CourseAdapter(it, this) }
    }

    private fun swapCourse() {
        if (binding.buttons.isVisible) {
            binding.buttons.visibility = View.GONE
            binding.newCourse.visibility = View.VISIBLE
            codeListener()
            titleListener()
        } else {
            binding.buttons.visibility = View.VISIBLE
            binding.newCourse.visibility = View.GONE
            clearCourse()
            binding.code.error = null
            binding.title.error = null
        }
    }

    private fun addCourse() {
        code = binding.code.text.toString().replace(".", " ").trim()
        title = binding.title.text.toString().replace(".", " ").trim()
        type = binding.type.selectedItemPosition
        if (checkCode(code!!) && checkTitle(title!!)) {
            val course = "$type.$code.$title"
            if (!Global.courses!!.contains(course)) {
                setupProgress("Adding new course")
                Global.courses!!.add(course)
                dbRef.child("/Courses").setValue(Global.courses)
                    .addOnSuccessListener {
                        binding.loading.container.visibility = View.GONE
                        snackBar(binding.root, "$code has been added")
                        binding.rvCourses.adapter = CourseAdapter(Global.courses!!, this)
                        clearCourse()
                        swapCourse()
                    }
                    .addOnFailureListener {
                        binding.loading.container.visibility = View.GONE
                        snackBar(binding.root, "Error: ${it.message}")
                    }
            } else {
                snackBar(binding.root, "Course already exist")
                clearCourse()
            }
        } else checkTitle(title!!)
    }

    private fun codeListener() {
        binding.code.setOnFocusChangeListener { _, focused ->
            val code = binding.code.text.toString()
            if (!focused) checkCode(code)
        }
    }

    private fun checkCode(code: String): Boolean {
        if (code.isNotEmpty()) {
            val regex = Regex("^[^0-9.]*$")
            if (!regex.matches(code)) {
                binding.code.error = "Must not contain " +
                        "numbers and/or a dot"
                return false
            }
            return true
        } else {
            binding.code.error = "Required"
            return false
        }
    }

    private fun titleListener() {
        binding.title.setOnFocusChangeListener { _, focused ->
            val title = binding.title.text.toString()
            if (!focused) checkTitle(title)
        }
    }

    private fun checkTitle(title: String): Boolean {
        if (title.isNotEmpty()) {
            val regex = Regex("^[^0-9.]*$")
            if (!regex.matches(title)) {
                binding.title.error = "Must not contain " +
                        "numbers and/or a dot"
                return false
            }
            return true
        } else {
            binding.title.error = "Required"
            return false
        }
    }

    private fun clearCourse() {
        binding.code.text = null
        binding.title.text = null
        code = null
        title = null
    }

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
    }
}