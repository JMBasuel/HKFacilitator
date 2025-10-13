package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.text.*
import android.view.*
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.*
import com.google.firebase.database.DatabaseReference
import com.upang.hkfacilitator.databinding.DialogResetBinding
import com.upang.hkfacilitator.models.Global.emailToKey
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.snackBar
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class ResetDialog(
    private val email: String,
    private val auth: FirebaseAuth,
    private val dbRef: DatabaseReference,
    private val type: String,
    private val isNew: Boolean,
    private val onComplete: (pin: String?) -> Unit
): DialogFragment() {

    private lateinit var binding: DialogResetBinding
    private lateinit var dialog: AlertDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogResetBinding.inflate(layoutInflater)
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

        when (type) {
            "PIN" -> {
                binding.title.text = "${if (isNew) "REQUIRED: Create" else "Change"} PIN"
                binding.newInput.apply {
                    hint = "New PIN"
                    inputType = InputType.TYPE_CLASS_NUMBER
                    filters = arrayOf(InputFilter.LengthFilter(6))
                }
            }
            "PASSWORD" -> {
                binding.title.text = "${if (isNew) "REQUIRED:" else ""} Change password"
                binding.newInput.hint = "New password"
                binding.message.visibility = View.VISIBLE
                binding.require.visibility = View.VISIBLE
            }
        }

        if (isNew) binding.password.visibility = View.GONE
        else passwordListener()
        inputListener()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnChange.setOnDebouncedClickListener {
            clear()
            change()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun change() {
        if (isInputValid()) {
            val input = "${binding.newInput.text.trim()}"
            if (!isNew) {
                authenticate {
                    setupProgress("Updating ${if (type == "PIN") "PIN" else "password"}")
                    when (type) {
                        "PIN" -> updatePIN(input)
                        "PASSWORD" -> updatePassword(input)
                    }
                }
            } else when (type) {
                "PIN" -> updatePIN(input)
                "PASSWORD" -> updatePassword(input)
            }
        }
    }

    private fun authenticate(onFinish: () -> Unit) {
        setupProgress("Authenticating")
        val credential = EmailAuthProvider.getCredential(email, "${binding.password.text.trim()}")
        auth.currentUser!!.reauthenticate(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onFinish()
                else {
                    binding.loading.container.visibility = View.GONE
                    snackBar(binding.root, "Incorrect password")
                }
            }
    }

    private fun updatePassword(input: String) {
        auth.currentUser!!.updatePassword(input)
            .addOnSuccessListener {
                binding.loading.container.visibility = View.GONE
                auth.currentUser!!.reload()
                onComplete(null)
                dialog.dismiss()
            }
            .addOnFailureListener {
                binding.loading.container.visibility = View.GONE
                snackBar(binding.root, "Error: ${it.message}")
            }
    }

    private fun updatePIN(input: String) {
        dbRef.child("/Accounts/${emailToKey(email)}/pin").setValue(input.hashSHA256())
            .addOnSuccessListener {
                binding.loading.container.visibility = View.GONE
                onComplete(input)
                dialog.dismiss()
            }
            .addOnFailureListener {
                binding.loading.container.visibility = View.GONE
                snackBar(binding.root, "Error: ${it.message}")
            }
    }

    private fun isInputValid(): Boolean = (isNew || binding.password.error == null) && ((type == "PASSWORD" &&
            binding.newInput.error == null) || (type == "PIN" && binding.newInput.error == null))

    private fun passwordListener() {
        binding.password.setOnFocusChangeListener { _, focused ->
            val password = "${binding.password.text.trim()}"
            if (!focused) {
                if (password.isNotEmpty()) {
                    binding.password.error = null
                    if (password.length < 8) binding.password.error =
                        "Minimum of 8 characters"
                } else binding.password.error = "Required"
            }
        }
    }

    private fun inputListener() {
        binding.newInput.setOnFocusChangeListener { _, focused ->
            val newInput = "${binding.newInput.text.trim()}"
            if (!focused) {
                if (newInput.isNotEmpty()) {
                    binding.newInput.error = null
                    if (type == "PASSWORD" &&
                        !Regex("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[{}()\\[\\]#:;,.?!|&_~@$%=+\\-*\"']).{8,}$")
                            .matches(newInput)) {
                        binding.newInput.error = "Requirements not met"
                    }
                    if (type == "PIN" && newInput.length < 6) binding.newInput.error =
                        "Must be 6 digits"
                } else binding.newInput.error = "Required"
            }
        }
        binding.newInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (Regex("^(?=.*[A-Z]).+$").matches(s!!)) binding.uppercase.visibility = View.GONE
                else binding.uppercase.visibility = View.VISIBLE
                if (Regex("^(?=.*[a-z]).+$").matches(s)) binding.lowercase.visibility = View.GONE
                else binding.lowercase.visibility = View.VISIBLE
                if (Regex("^(?=.*[{}()\\[\\]#:;,.?!|&_~@$%=+\\-*\"']).+$").matches(s)) binding.symbol.visibility = View.GONE
                else binding.symbol.visibility = View.VISIBLE
                if (Regex("^(?=.*\\d).+$").matches(s)) binding.digit.visibility = View.GONE
                else binding.digit.visibility = View.VISIBLE
                if (Regex("^.{8,}$").matches(s)) binding.length.visibility = View.GONE
                else binding.length.visibility = View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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