package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.DialogEditCourseBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.snackBar
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("SetTextI18n")
class EditCourseDialog(
    private val user: User,
    private val login: String,
    private val onComplete: () -> Unit
) :
    DialogFragment() {
    private lateinit var binding: DialogEditCourseBinding
    private lateinit var courses: ArrayList<String>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var dbRef: DatabaseReference
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogEditCourseBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        courses = arrayListOf()

        setupProfile()
        loadCourses()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSubmit.setOnDebouncedClickListener {
            submitCourse()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    private fun submitCourse() {
        val course = binding.courses.selectedItem.toString()
        val year = binding.year.selectedItem.toString()
        val courseLevel = Global.courses!!.filter { it.contains(course) }
            .map { it.substringAfter('.').substringBeforeLast('.') }[0] +
                if (login == "Facilitator") year else ""
        confirmDialog = ConfirmDialog("SUBMIT NEW COURSE", "Are you sure to submit new " +
                "course?", "Submit", "Cancel", true) {
            submit(courseLevel)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun submit(courseLevel: String) {
        setupProgress("Submitting new course")
        dbRef.child("/Data/$login/${user.email!!.hashSHA256()}/course")
            .setValue(courseLevel)
            .addOnSuccessListener {
                snackBar(binding.root, "Course has been edited")
                binding.loading.container.visibility = View.GONE
                user.course = courseLevel
                Global.profiledUser = user
                onComplete()
                dialog.dismiss()
            }
            .addOnFailureListener {
                snackBar(binding.root, "Error: ${it.message}")
                binding.loading.container.visibility = View.GONE
            }
    }

    private fun loadCourses() {
        setupProgress("Loading data")
        if (Global.courses == null) {
            dbRef.child("/Courses").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        courses.clear()
                        if (snapshot.exists()) for (course in snapshot.children)
                            courses.add(course.value as String)
                        Global.courses = courses
                        setupCourses()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        snackBar(binding.root, "Error: ${error.message}")
                        binding.loading.container.visibility = View.GONE
                    }
                }
            )
        } else setupCourses()
    }

    private fun setupCourses() {
        val courseFull = Global.courses!!.filter { it.startsWith('0') }.map { it.substringAfterLast('.') }
        val fullAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, courseFull)
        fullAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.courses.adapter = fullAdapter
        if (login == "Manager") binding.year.visibility = View.GONE
        binding.loading.container.visibility = View.GONE
    }

    private fun setupProfile() {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get().load(user.profileUrl).into(binding.userImage)
        binding.name.text = user.name
        val year = user.course!!.removeCourse()
        binding.course.text = "${
            Global.courses?.filter {
                it.contains(
                    user.course!!
                        .removeYear())
            }?.map { it.substringAfterLast('.') }?.get(0)
        } $year"
    }

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
    }
}