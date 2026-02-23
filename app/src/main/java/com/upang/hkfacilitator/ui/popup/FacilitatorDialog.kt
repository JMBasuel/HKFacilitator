package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.upang.hkfacilitator.adapters.SchedFaciAdapter
import com.upang.hkfacilitator.databinding.DialogFacilitatorBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.utils.*
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("ClickableViewAccessibility")
class FacilitatorDialog(
    private val dbRef: DatabaseReference,
    private val facilitators: ArrayList<User>,
    private val schedule: Schedule,
    private val onComplete: (Boolean, Int) -> Unit
) :
    DialogFragment(), SchedFaciClickListener
{
    private lateinit var binding: DialogFacilitatorBinding
    private lateinit var adapter: SchedFaciAdapter
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var dialog: AlertDialog
    private val filtered: ArrayList<User> = arrayListOf()
    private var need: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogFacilitatorBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        binding.rvAssign.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        need = schedule.need!! - schedule.joined!!
        setupFacilitators()

        binding.searchBar.setOnQueryTextListener(searchListener())

        binding.btnExit.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnAssign.setOnDebouncedClickListener {
            assign()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    override fun onTimeClick(user: User, isTimeIn: Boolean) {}

    override fun onCheckClick() {
        binding.btnAssign.isEnabled = adapter.getViewHolders().count { it.binding.checkBox.isChecked } in 1 downTo need
    }

    private fun assign() {
        var names = ""
        val facilitator = arrayListOf<User>()
        val viewHolders = adapter.getViewHolders()
        for (viewHolder in viewHolders) {
            if (viewHolder.binding.checkBox.isChecked) {
                facilitator.add(viewHolder.user)
                names += "\n${viewHolder.user.name}"
            }
        }
        confirmDialog = ConfirmDialog("CONFIRM ASSIGN", "Please confirm assigning the " +
                "following facilitator${if (facilitator.size>1) "s" else ""} to this schedule:" +
                "\n$names\n\nNOTE: The facilitator${if (facilitator.size>1) "s" else ""} you are " +
                "about to assign to this schedule MUST HAVE CONSENTED to this action to avoid giving " +
                "them unfair penalties.", "Assign", "Cancel", Global.isPINDisabled) {
            assignment(facilitator)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun setupFacilitators() {
        adapter = SchedFaciAdapter(facilitators, this, true, isFaculty = true, isActiveOrDone = false)
        binding.rvAssign.adapter = adapter
    }

    private fun assignment(facilitator: ArrayList<User>) {
        val updates = hashMapOf<String, Any>()
        val today = todayDate(true)
        val notifyID = today.substring(2).replace("-", "") +
                "${todayHour().replace(":", "")}${schedule.id}ASSIGNAD"
        val title = "Assigned to extra schedule"
        val message = "You have been assigned to an extra schedule owned by ${schedule.owner} " +
                "for ${schedule.date} ${timeRangeTo12(schedule.time)}."
        val datetime = "${todayDate(false)} ${todayHour()}"
        val notify = Notifications(notifyID, title, message, datetime, "SCHEDULE.${schedule.id}")
        setupProgress("Assigning facilitator${if (facilitator.size>1) "s" else ""}")
        dbRef.child("/Extras/${schedule.id}").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val joined = currentData.child("/joined").getValue(Int::class.java)
                    ?: return Transaction.success(currentData)
                if (joined + facilitator.size > schedule.need!!) return Transaction.abort()
                facilitator.forEach { faci ->
                    currentData.child("/faci/${faci.email!!.hashSHA256()}").value = User(
                        email = faci.email, name = faci.name, gender = faci.gender, course = faci.course)
                    updates["/${faci.email.hashSHA256()}/notifications/$notifyID"] = notify
                    updates["/${faci.email.hashSHA256()}/notified"] = true
                }
                currentData.child("/joined").value = joined + facilitator.size
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?,
                committed: Boolean, currentData: DataSnapshot?
            ) {
                binding.loading.container.visibility = View.GONE
                if (error != null) snackBar(view, "Error: ${error.message}")
                else {
                    if (committed) dbRef.child("/Data/Facilitator").updateChildren(updates)
                    onComplete(committed, facilitator.size)
                    dialog.dismiss()
                }
            }
        })
    }

    private fun searchListener(): SearchView.OnQueryTextListener {
        return object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                clear()
                filterDataOnSubmit(query!!.trim())
                return true
            }

            override fun onQueryTextChange(query: String?): Boolean {
                resetDataOnClear(query)
                return true
            }
        }
    }

    private fun resetDataOnClear(query: String?) {
        if (query.isNullOrBlank()) {
            adapter = SchedFaciAdapter(facilitators, this, true, isFaculty = true, isActiveOrDone = false)
            binding.rvAssign.adapter = adapter
        }
    }

    private fun filterDataOnSubmit(query: String?) {
        filtered.clear()
        if (!query.isNullOrBlank()) {
            val regex = Regex("^\\d{2}-\\d{4}-\\d{5,}$")
            if (regex.matches(query)) filtered.addAll(facilitators.filter { item ->
                item.id!!.contains(query, true)
            })
            else filtered.addAll(facilitators.filter { item ->
                item.name!!.contains(query, true) ||
                        item.course!!.contains(query, true) ||
                        item.gender!!.contains(query, true) ||
                        item.email!!.contains(query, true) ||
                        item.hk!!.contains(query, true) ||
                        item.hrs.toString().contains(query, true) ||
                        item.toString().contains(query, true)
            })
            adapter = SchedFaciAdapter(filtered, this, true, isFaculty = true, isActiveOrDone = false)
            binding.rvAssign.adapter = adapter
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