package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.util.Patterns
import android.view.*
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.upang.hkfacilitator.databinding.DialogForgotBinding
import com.upang.hkfacilitator.models.Account
import com.upang.hkfacilitator.models.Global.emailToKey
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.snackBar
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("ClickableViewAccessibility")
class ForgotDialog(
    private val auth: FirebaseAuth,
    private val dbRef: DatabaseReference,
    private val onComplete: () -> Unit
): DialogFragment() {

    private lateinit var binding: DialogForgotBinding
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogForgotBinding.inflate(layoutInflater)
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

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        emailListener()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnReset.setOnDebouncedClickListener {
            clear()
            sendResetLink()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun sendResetLink() {
        val email = "${binding.email.text.trim()}"
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            checkEmail(email) { isRegisteredAndLogged ->
                if (isRegisteredAndLogged) resetPass(email)
                else snackBar(binding.root, "Failed. Please log-in with your registered account credentials.")
            }
        }
    }

    private fun checkEmail(email: String, onComplete: (Boolean) -> Unit) {
        setupProgress("Verifying email")
        dbRef.child("/Accounts/${emailToKey(email)}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val account = snapshot.getValue(Account::class.java)!!
                        if (account.password != null) onComplete(false)
                        else onComplete(true)
                    } else onComplete(false)
                    binding.loading.container.visibility = View.GONE
                }
                override fun onCancelled(error: DatabaseError) {
                    binding.loading.container.visibility = View.GONE
                    snackBar(binding.root, "Error: ${error.message}")
                }
            })
    }

    private fun resetPass(email: String) {
        setupProgress("Sending reset link")
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                binding.loading.container.visibility = View.GONE
                onComplete()
                dialog.dismiss()
            }
            .addOnFailureListener {
                binding.loading.container.visibility = View.GONE
                snackBar(binding.root, "Error: ${it.message}")
            }
    }

    private fun emailListener() {
        binding.email.setOnFocusChangeListener { _, focused ->
            val email = "${binding.email.text.trim()}"
            if (!focused) {
                if (email.isNotEmpty()) {
                    binding.email.error = null
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches())
                        binding.email.error = "Invalid email address"
                } else binding.email.error = "Required"
            }
        }
    }

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}