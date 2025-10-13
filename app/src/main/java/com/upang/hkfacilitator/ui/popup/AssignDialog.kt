package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.*
import android.view.*
import android.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.upang.hkfacilitator.adapters.AssignAdapter
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.*
import com.upang.hkfacilitator.models.Global.isWithinTimeRange
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isActive
import com.upang.hkfacilitator.models.Global.isNotRestricted
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.utils.*
import com.upang.hkfacilitator.viewholders.AssignViewHolder
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible

@SuppressLint("SetTextI18n, ClickableViewAccessibility")
class AssignDialog(
    private val user: User,
    private val filter: ArrayList<Timestamp>?,
    private val dbRef: DatabaseReference
) :
    DialogFragment(), AssignClickListener
{
    private lateinit var binding: DialogAssignBinding
    private lateinit var watchEventListener: ValueEventListener
    private lateinit var schedules: ArrayList<Schedule>
    private lateinit var filtered: ArrayList<Schedule>
    private lateinit var assignAdapter: AssignAdapter
    private var confirmDialog: ConfirmDialog? = null
    private var selectSched: Schedule? = null
    private lateinit var dialog: AlertDialog
    private lateinit var watchQuery: Query

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogAssignBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        filtered = arrayListOf()
        schedules = arrayListOf()

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        binding.rvAssign.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        setupProfile()
        watchAssignment()

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.btnNo.setOnClickListener {
            dialog.dismiss()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }

    private fun setupNotAssigned() {
        binding.btnYes.isEnabled = false
        binding.btnYes.text = "Assign"
        if (filter != null) binding.checkBox.visibility = View.VISIBLE
        else binding.checkBox.visibility = View.GONE
        binding.btnYes.setOnDebouncedClickListener {
            assignment(true)
        }
        binding.searchBar.setOnQueryTextListener(searchListener())
        binding.checkBox.setOnCheckedChangeListener { _, checked ->
            checkFilter(checked)
        }
        fetchPermanents {
            binding.assign.visibility = View.VISIBLE
            binding.assigned.visibility = View.GONE
        }
        binding.loading.container.visibility = View.GONE
    }

    private fun setupAssigned() {
        binding.schedTitle.text = selectSched!!.title
        binding.owner.text = selectSched!!.owner
        binding.date.text = selectSched!!.date
        binding.time.text = timeRangeTo12(selectSched!!.time)
        binding.room.text = selectSched!!.room
        binding.assign.visibility = View.GONE
        binding.assigned.visibility = View.VISIBLE
        binding.btnYes.isEnabled = true
        binding.btnYes.text = "Remove"
        binding.btnYes.setOnDebouncedClickListener {
            assignment(false)
        }
        binding.loading.container.visibility = View.GONE
    }

    private fun watchAssignment() {
        setupProgress("Loading data")
        watchQuery = dbRef.child("/Data/Facilitator/${user.email!!.hashSHA256()}/assignment")
        watchEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) getPermanent(snapshot.getValue(String::class.java)!!) {
                    if (selectSched != null) setupAssigned()
                }
                if (selectSched == null) setupNotAssigned()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(binding.root, "Error: ${error.message}")
                binding.loading.container.visibility = View.GONE
            }
        }
        watchQuery.addValueEventListener(watchEventListener)
    }

    private fun getPermanent(schedId: String, onComplete: () -> Unit) {
        dbRef.child("/Permanents/$schedId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) selectSched = snapshot.getValue(Schedule::class.java)
                    binding.loading.container.visibility = View.GONE
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(binding.root, "Error: ${error.message}")
                    binding.loading.container.visibility = View.GONE
                }
            })
    }

    private fun fetchPermanents(onComplete: () -> Unit) {
        dbRef.child("/Permanents").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    schedules.clear()
                    if (snapshot.exists()) for (sched in snapshot.children) {
                        val schedule = sched.getValue(Schedule::class.java)!!
                        schedules.apply {
                            if (schedule.joined!! < schedule.need!! &&
                                isNotRestricted(schedule, user.course!!) &&
                                !isActive(schedule, false)) add(schedule)
                        }
                    }
                    assignAdapter = AssignAdapter(schedules, this@AssignDialog)
                    binding.rvAssign.adapter = assignAdapter
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(binding.root, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun submitUpdates(isAssign: Boolean, onComplete: () -> Unit) {
        val today = todayDate(true)
        val id = today.substring(2).replace("-", "") +
                todayHour().replace(":", "") +
                "${if (isAssign) "ASSIGN" else "REMOVE"}AD"
        val title = if (isAssign) "Permanent schedule assigned"
        else "Permanent schedule removed"
        val message = if (isAssign) "You have been assigned to a permanent " +
                "schedule owned by ${selectSched!!.owner}. Please ensure that " +
                "you attend your permanent schedule regularly to avoid penalties."
        else "You have been removed from the permanent schedule owned by " +
                "${selectSched!!.owner} that you have previously assigned to."
        val datetime = "${todayDate(false)} ${todayHour()}"
        val notification = Notifications(id, title, message, datetime)
        val updates = hashMapOf(
            "/notifications/$id" to notification,
            "/notified" to true,
            "/assignment" to if (isAssign) "${selectSched!!.id}" else null)
        setupProgress(if (isAssign) "Assigning schedule" else "Removing assignment")
        dbRef.child("/Data/Facilitator/${user.email!!.hashSHA256()}").updateChildren(updates)
            .addOnCompleteListener { onComplete() }
    }

    private fun assignSchedule(isAssign: Boolean) {
        setupProgress(if (isAssign) "Assigning schedule" else "Removing assignment")
        var aborted = ""
        dbRef.child("/Permanents/${selectSched!!.id}").runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val joined = currentData.child("/joined").getValue(Int::class.java)
                        ?: return Transaction.success(currentData)
                    val need = currentData.child("/need").getValue(Int::class.java)!!
                    var faci = currentData.child("/faci/${user.email!!.hashSHA256()}").getValue(User::class.java)
                    if (isAssign) {
                        if (joined + 1 > need) {
                            aborted = "Schedule is full"
                            return Transaction.abort()
                        } else if (faci != null) {
                            aborted = "Already assigned"
                            return Transaction.abort()
                        }
                        faci = User(email = user.email, name = user.name, gender = user.gender, course = user.course)
                        currentData.child("/faci/${user.email.hashSHA256()}").value = faci
                        currentData.child("/joined").value = joined + 1
                    } else {
                        currentData.child("/faci/${user.email.hashSHA256()}").value = null
                        currentData.child("/joined").value = joined - 1
                    }
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    binding.loading.container.visibility = View.GONE
                    if (error != null) snackBar(binding.root, "Error: ${error.message}")
                    else if (committed) {
                        submitUpdates(isAssign) {
                            selectSched = null
                        }
                    } else snackBar(binding.root, aborted)
                }
            })
    }

    private fun assignment(isAssign: Boolean) {
        confirmDialog = ConfirmDialog("ASSIGNMENT", "Please confirm ${if (isAssign) 
            "schedule assignment${if (filter != null && !filter.any { 
                it.time!!.substringBefore('.') == selectSched!!.date && 
                        isWithinTimeRange(it.time.substringAfter('.'), selectSched!!.time!!) 
            }) "\n\nNOTE: The selected schedule's date-time DOES NOT MATCH any date-time in the " +
                    "facilitator's VACANT" else ""}" else "assignment removal"}", if (isAssign)
                        "Assign" else "Remove", "Cancel", Global.isPINDisabled) {
            assignSchedule(isAssign)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun setupProfile() {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.userImage)
        binding.name.text = user.name
        val year = user.course!!.removeCourse()
        binding.course.text = "${Global.courses?.filter { it.contains( user.course!!.removeYear()) }
                ?.map{ it.substringAfterLast('.') }?.get(0)} $year"
    }

    private fun checkFilter(checked: Boolean) {
        if (checked) {
            filtered.clear()
            filtered.addAll(schedules.filter { item ->
                filter!!.any { filter ->
                    item.date!!.contains(filter.time!!.substringBefore('.')) &&
                            isWithinTimeRange(filter.time.substringAfter('.'), item.time!!)
                }
            })
            assignAdapter = AssignAdapter(filtered, this)
            binding.rvAssign.adapter = assignAdapter
            refresh(filtered)
        } else {
            assignAdapter = AssignAdapter(schedules, this)
            binding.rvAssign.adapter = assignAdapter
            refresh(schedules)
        }
    }

    private fun searchListener(): SearchView.OnQueryTextListener {
        return object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                clear()
                filterDataOnSubmit(query!!.trim())
                return true
            }
            override fun onQueryTextChange(query: String?): Boolean {
                if (binding.searchError.isVisible)
                    binding.searchError.visibility = View.GONE
                resetDataOnClear(query)
                return true
            }
        }
    }

    private fun resetDataOnClear(query: String?) {
        if (query.isNullOrBlank()) {
            assignAdapter = AssignAdapter(schedules, this)
            binding.rvAssign.adapter = assignAdapter
            refresh(schedules)
        }
    }

    private fun filterDataOnSubmit(query: String?) {
        filtered.clear()
        if (!query.isNullOrBlank()) {
            val regex = Regex("^\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}$")
            if (regex.matches(query)) {
                val time = query.split("-")
                if (time[0].trim().substringAfter(':').toInt() < 60 &&
                    time[1].trim().substringAfter(':').toInt() < 60 &&
                    time[0].trim().replace(":", "").toInt() <
                    time[1].trim().replace(":", "").toInt()) {
                    filtered.addAll(schedules.filter { item ->
                        isWithinTimeRange(query, item.time!!)
                    })
                } else binding.searchError.visibility = View.VISIBLE
            } else {
                filtered.addAll(schedules.filter { item ->
                    item.date!!.contains(query, true) ||
                            item.time!!.contains(query, true)
                })
            }
            assignAdapter = AssignAdapter(filtered, this)
            binding.rvAssign.adapter = assignAdapter
            refresh(filtered)
        }
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }

    private fun refresh(arrayList: ArrayList<Schedule>) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (selectSched != null) {
                if (selectSched in arrayList) {
                    val previous = assignAdapter.getAssignViewHolders(selectSched!!.id.toString())
                    for (viewHolder in previous)
                        viewHolder.binding.cvSched.setCardBackgroundColor(
                            "#4015B34E".toColorInt())
                } else {
                    selectSched = null
                    binding.btnYes.isEnabled = false
                }
            }
        }, 1)
    }

    override fun onPause() {
        super.onPause()
        watchQuery.removeEventListener(watchEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    override fun onSchedClick(sched: Schedule, card: ItemAssignBinding) {
        selectSched = sched
        binding.btnYes.isEnabled = true
        card.cvSched.setCardBackgroundColor("#4015B34E".toColorInt())
    }

    override fun getViewHolder(id: String): List<AssignViewHolder> {
        return assignAdapter.getAssignViewHolders(id)
    }

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
    }
}