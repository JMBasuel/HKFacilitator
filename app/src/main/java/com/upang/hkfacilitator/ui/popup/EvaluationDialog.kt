package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.DialogEvaluationBinding
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.snackBar
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("SetTextI18n")
class EvaluationDialog(
    private val evalUser: User?,
    private val user: User,
    private val login: String,
    private val eval: Evaluation?,
    private val isEdit: Boolean,
    private val onSuccess: () -> Unit
)
    : DialogFragment()
{
    private lateinit var binding: DialogEvaluationBinding
    private lateinit var dbRef: DatabaseReference
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogEvaluationBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference

        setupProfile()
        commentListener()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSubmit.setOnDebouncedClickListener {
            submit()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun submitEditEvaluation(eval: Evaluation) {
        dbRef.child("/Data/$login/${user.email!!.hashSHA256()}/evaluation/${eval.id}").setValue(eval)
            .addOnSuccessListener {
                onSuccess()
                dialog.dismiss()
            }
            .addOnFailureListener {
                snackBar(binding.root, "Error: ${it.message}")
                binding.loading.container.visibility = View.GONE
            }
    }

    private fun submitNewEvaluation(eval: Evaluation) {
        val updates = hashMapOf<String, Any?>(
            "/evaluation/${eval.id}" to eval,
            "/${if (login == "Faculty") "facilitator" else "faculty"}/${evalUser!!.email!!.hashSHA256()}" to null)
        dbRef.child("/Data/$login/${user.email!!.hashSHA256()}").updateChildren(updates)
            .addOnSuccessListener {
                onSuccess()
                dialog.dismiss()
            }
            .addOnFailureListener {
                snackBar(binding.root, "Error: ${it.message}")
                binding.loading.container.visibility = View.GONE
            }
    }

    private fun submit() {
        val comment = binding.comment.text.toString().trim()
        if (!checkComment(comment)) return
        if (isConnected(requireContext())) {
            val today = todayDate(true)
            val id = if (isEdit) eval!!.id
            else evalUser!!.email!!.hashSHA256()
            setupProgress()
            if (!isEdit) submitNewEvaluation(Evaluation(id, evalUser!!.name, evalUser.gender, evalUser.course, today, comment))
            else submitEditEvaluation(Evaluation(id, eval!!.name, eval.gender, eval.course, today, comment))
        } else snackBar(binding.root, "Network error. Please check your connection")
    }

    private fun setupProfile() {
        if (!isEdit) {
            if (evalUser!!.profileUrl == null) {
                if (evalUser.gender.equals("Female"))
                    binding.userImage.setImageResource(R.drawable.head_female)
            } else Picasso.get()
                .load(evalUser.profileUrl)
                .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
                else R.drawable.head_female)
                .into(binding.userImage)
            binding.name.text = evalUser.name
            if (evalUser.course != null) {
                val year = evalUser.course!!.removeCourse()
                binding.course.apply {
                    text = "${Global.courses?.filter { it.contains( evalUser.course!!.removeYear()) }
                        ?.map{ it.substringAfterLast('.') }?.get(0)} $year"
                    visibility = View.VISIBLE
                }
            }
        } else {
            if (eval!!.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
            binding.name.text = eval.name
            if (eval.course != null) {
                val year = eval.course.removeCourse()
                binding.course.apply {
                    text = "${Global.courses?.filter { it.contains( eval.course.removeYear()) }
                        ?.map{ it.substringAfterLast('.') }?.get(0)} $year"
                    visibility = View.VISIBLE
                }
            }
            binding.comment.setText(eval.comment)
        }
    }

    private fun commentListener() {
        binding.comment.setOnFocusChangeListener { _, focused ->
            val comment = binding.comment.text.toString().trim()
            if (!focused) checkComment(comment)
        }
    }

    private fun checkComment(comment: String): Boolean {
        if (comment.isEmpty()) {
            binding.title.error = "Required"
            return false
        }
        return true
    }

    private fun setupProgress() {
        binding.loading.message.text = "Submitting evaluation"
        binding.loading.container.visibility = View.VISIBLE
    }
}