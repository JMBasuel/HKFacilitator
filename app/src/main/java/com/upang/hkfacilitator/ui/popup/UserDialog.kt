package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.*
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.database.*
import com.google.firebase.storage.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.DialogUserBinding
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.emailToKey
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("SetTextI18n")
class UserDialog(
    private var user: User
) : DialogFragment(), ConnectionStateListener {

    private lateinit var binding: DialogUserBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var managerEventListener: ValueEventListener
    private var editCourseDialog: EditCourseDialog? = null
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private lateinit var managerQuery: Query
    private var isPaused = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogUserBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        stRef = FirebaseStorage.getInstance(Global.firebase!!).reference

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt()
        )

        setupProfile()
        watchManager()

        binding.btnRemove.setOnDebouncedClickListener {
            terminateAccount()
        }

        binding.btnEdit.setOnDebouncedClickListener {
            editCourse()
        }

        binding.btnExit.setOnClickListener {
            dialog.dismiss()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    override fun onResume() {
        super.onResume()
        connectionStateMonitor.enable(requireContext())
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
        connectionStateMonitor.disable(requireContext())
        managerQuery.removeEventListener(managerEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (editCourseDialog != null) if (editCourseDialog!!.isAdded) editCourseDialog!!.dismiss()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            if (isPaused) {
                isPaused = false
                binding.loading.container.visibility = View.GONE
                managerQuery.addValueEventListener(managerEventListener)
            }
        }
    }

    override fun onNetworkLost() {
        requireActivity().runOnUiThread {
            isPaused = true
            setupProgress("Waiting for connection")
            if (isConnected(requireContext()))
                binding.loading.container.visibility = View.GONE
            else managerQuery.removeEventListener(managerEventListener)
        }
    }

    private fun watchManager() {
        managerQuery = dbRef.child("/Data/Manager/${user.email!!.hashSHA256()}/email")
        managerEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() && snapshot.value == null) {
                    snackBar(binding.root, "This account has been deleted")
                    findNavController().popBackStack()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(binding.root, "Error: ${error.message}")
            }
        }
        managerQuery.addValueEventListener(managerEventListener)
    }

    private fun setupProfile() {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get().load(user.profileUrl).into(binding.userImage)
        binding.name.text = user.name
        binding.course.text = "${Global.courses?.filter { it.contains( user.course!!
            .removeYear()) }?.map{ it.substringAfterLast('.') }?.get(0)}"
    }

    private fun showEditCourseDialog() {
        editCourseDialog = EditCourseDialog(user, "Manager") {
            user = Global.profiledUser!!
            binding.course.text = "${Global.courses?.filter { it.contains( user.course!!
                .removeYear()) }?.map{ it.substringAfterLast('.') }?.get(0)}"
        }
        editCourseDialog!!.show(childFragmentManager, "EditCourseDialog")
    }

    private fun editCourse() {
        confirmDialog = ConfirmDialog("EDIT COURSE", "Do you want to edit this user's " +
                "course?", "Proceed", "Cancel", Global.isPINDisabled) {
            showEditCourseDialog()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun terminate() {
        setupProgress("Deleting account")
        val updates = hashMapOf<String, Any?>(
            "/Accounts/${emailToKey(user.email!!)}" to null,
            "/Data/Manager/${user.email!!.hashSHA256()}" to null)
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                stRef.child("/Profiles/${user.email!!.hashSHA256()}").delete()
                binding.loading.container.visibility = View.GONE
            }
            .addOnFailureListener {
                snackBar(binding.root, "Error: ${it.message}")
                binding.loading.container.visibility = View.GONE
            }
    }

    private fun terminateAccount() {
        confirmDialog = ConfirmDialog("CONFIRM", "Please confirm account deletion",
            "Terminate", "Cancel", Global.isPINDisabled) {
            terminate()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
    }
}