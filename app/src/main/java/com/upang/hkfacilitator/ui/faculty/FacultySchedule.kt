package com.upang.hkfacilitator.ui.faculty

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.RadioButton
import androidx.activity.OnBackPressedCallback
import androidx.core.view.children
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.*
import com.upang.hkfacilitator.databinding.FacultyScheduleBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.calculateTimeRange
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.getWeekRange
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isActive
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.isDayAfter
import com.upang.hkfacilitator.models.Global.isEarlyLate
import com.upang.hkfacilitator.models.Global.isFinished
import com.upang.hkfacilitator.models.Global.isLateTimeIn
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.showTimePicker
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.ui.popup.ConfirmDialog
import com.upang.hkfacilitator.utils.*
import com.upang.hkfacilitator.viewholders.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

@SuppressLint("SetTextI18n")
class FacultySchedule : Fragment(), ConnectionStateListener, SchedFaciClickListener, SchedFaciBatchedClickListener {

    private lateinit var binding: FacultyScheduleBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private lateinit var batchedAdapter: SchedFaciBatchedAdapter
    private var watchEventListener: ValueEventListener? = null
    private var faciEventListener: ValueEventListener? = null
    private var schedFaci: HashMap<String, User>? = null
    private lateinit var facilitators: ArrayList<User>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var adapter: SchedFaciAdapter
    private lateinit var dbRef: DatabaseReference
    private lateinit var schedule: Schedule
    private lateinit var account: Account
    private var watchQuery: Query? = null
    private var faciQuery: Query? = null
    private lateinit var type: String
    private lateinit var user: User
    private var isDoneSetup = false
    private var isPaused = false
    private var isFinish = false
    private val callbackFalse = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {} }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializations()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FacultyScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        isPaused = false

        binding.btnYes.isEnabled = schedule.isDone == true

        binding.rvScheduleFaci.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        if (Global.offset == null) getServerTime()
        fetchData()

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnExtend.setOnDebouncedClickListener {
            extendSchedule()
        }

        binding.btnEdit.setOnDebouncedClickListener {
            editSchedule()
        }

        binding.checkAll.setOnCheckedChangeListener { _, checked ->
            checkAll(checked)
        }

        binding.btnNo.setOnDebouncedClickListener {
            btnNo()
        }

        binding.btnYes.setOnDebouncedClickListener {
            btnYes()
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.green, R.color.yellow)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)
        binding.swipeRefresh.viewTreeObserver.addOnGlobalLayoutListener {
            val distance = (binding.swipeRefresh.height * 0.6).toInt()
            binding.swipeRefresh.setDistanceToTriggerSync(distance)
        }

        binding.swipeRefresh.setOnRefreshListener {
            if (schedule.inCompleted == null) loadData(true)
            else binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isConnected(requireContext())) {
            isPaused = true
            setupProgress("Waiting for connection")
            removeListeners()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.swipeRefresh.isRefreshing = false
        connectionStateMonitor.enable(requireContext())
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
        connectionStateMonitor.disable(requireContext())
        removeListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Global.schedule = null
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPaused = true
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            if (isPaused) {
                isPaused = false
                endProgress()
                if (Global.offset == null) getServerTime()
                checkTimeChange()
                if (!isDoneSetup) fetchData()
                else {
                    addListener(watchQuery, watchEventListener)
                    addListener(faciQuery, faciEventListener)
                }
            }
        }
    }

    override fun onNetworkLost() {
        requireActivity().runOnUiThread {
            isPaused = true
            setupProgress("Waiting for connection")
            if (isConnected(requireContext())) endProgress()
            else removeListeners()
        }
    }

    override fun onTimeClick(user: User, isTimeIn: Boolean) {
        confirmDialog = ConfirmDialog(if (isTimeIn) "TIME IN" else "TIME OUT", "Are you " +
                "sure you want to edit ${user.name}'s ${if (isTimeIn) "time-in" else "time-out"}?",
            if (isTimeIn) "Time in" else "Time out", "Cancel", true) {
            showTimePicker(requireContext()) { time ->
                if (isTimeIn) editTimeInOut(user, time, "timeIn")
                else editTimeInOut(user, time, "timeOut")
            }
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    override fun onCheckClick() {}

    override fun onRadioSelect() {
        val viewHolders = batchedAdapter.getViewHolders()
        toggleViewHolders(viewHolders, viewHolders.count { it.binding.mark.children.any { rb -> (rb as RadioButton).isChecked } } >= 5)
    }

    private fun initializations() {
        user = Global.userData!!
        account = Global.account!!
        schedule = Global.schedule!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        type = if (schedule.id!![1].isLetter()) "Permanents" else "Extras"
        facilitators = arrayListOf()
        schedFaci = schedule.faci
        listenerMap = mutableMapOf()
    }

    private fun fetchData() {
        setupProgress("Loading data")
        setupDetails()
        watchUser {
            watchFaci {
                loadData(false)
            }
        }
    }

    private fun loadData(isRefresh: Boolean) {
        fetchSchedule {
            setupData {
                if (isRefresh) binding.swipeRefresh.isRefreshing = false
                isDoneSetup = true
                endProgress()
            }
        }
    }

    private fun setupData(onComplete: () -> Unit) {
        dbRef.child("/Data/Facilitator").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) for (faci in snapshot.children) {
                        val data = faci.getValue(User::class.java)!!
                        val index = facilitators.indexOfFirst { it.email == data.email && data.profileUrl != null }
                        if (index != -1) facilitators[index].profileUrl = data.profileUrl
                    }
                    binding.rvScheduleFaci.adapter = SchedFaciAdapter(facilitators, this@FacultySchedule,
                        false, isFaculty = true, isActiveOrDone = schedule.isActive == true || schedule.isDone == true
                    )
                    setupButton()
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun watchFaci(onComplete: () -> Unit) {
        faciQuery = dbRef.child("/${if (schedule.inCompleted == null) type else "Completed"}/${schedule.id}/faci")
        faciEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                facilitators.clear()
                if (snapshot.exists()) for (faci in snapshot.children) {
                    val blacklisted = faci.child("blacklisted").getValue(Boolean::class.java)
                    if (blacklisted == null) facilitators.add(faci.getValue(User::class.java)!!)
                }
                binding.facilitator.text = "${facilitators.size}/${schedule.need}"
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(faciQuery, faciEventListener)
    }

    private fun fetchSchedule(onComplete: () -> Unit) {
        dbRef.child("/${if (schedule.inCompleted == null) type else "Completed"}/${schedule.id}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val sched = snapshot.getValue(Schedule::class.java)!!
                        if (schedule.inCompleted == null) {
                            if (type == "Permanents") {
                                if (sched.suspended == null && isPast(sched, false) &&
                                    !isFinished(sched) && sched.joined!! > 0)
                                    sched.isDone = true
                                else if (sched.suspended == null && isActive(sched, false) &&
                                    !isFinished(sched) && sched.joined!! > 0)
                                    sched.isActive = true
                            } else if (sched.suspended == null && isPast(sched, true) &&
                                sched.joined!! > 0) sched.isDone = true
                            else if (sched.suspended == null && isActive(sched, false) &&
                                sched.joined!! > 0) sched.isActive = true
                        } else {
                            sched.inCompleted = true
                            sched.isDone = true
                        }
                        schedule = sched
                        Global.schedule = schedule
                    }
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun watchUser(onComplete: () -> Unit) {
        watchQuery = dbRef.child("/Data/Faculty/${user.email!!.hashSHA256()}/email")
        watchEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() && snapshot.value == null)
                    findNavController().popBackStack()
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(watchQuery, watchEventListener)
    }

    private fun submit() {
        var status: String? = null
        val map = mutableMapOf<User, Schedule>()
        val today = todayDate(true)
        val id = today.substring(2).replace("-", "") +
                "${todayHour().replace(":", "")}${schedule.email!!
            .replace(".", "").substring(0, 6).uppercase()}"
        val sched = schedule.copy(
            id = id,
            detail = null,
            email = null,
            joined = null,
            need = null,
            restrict = null,
            room = null,
            date = todayDate(false),
            subject = null,
            isDone = null,
            edited = null,
            faci = null,
            inCompleted = null,
            extended = null,
            extension = null,
            suspended = null)
        if (schedule.joined!! > 5) {
            val viewHolders = batchedAdapter.getViewHolders()
            viewHolders.filter { it.binding.mark.children.any { rb -> (rb as RadioButton).isChecked } }.forEach { viewHolder ->
                val (schedule, state) = processScheduleBatched(viewHolder, sched)
                map[viewHolder.user] = schedule
                status = state
            }
        } else {
            val viewHolders = adapter.getViewHolders()
            viewHolders.forEach { viewHolder ->
                val (schedule, state) = processSchedule(viewHolder, sched)
                map[viewHolder.user] = schedule
                status = state
            }
        }
        var names = ""
        map.forEach { (user, sched) ->
            names += "\n${user.name} : ${sched.remark}"
        }
        confirmDialog = ConfirmDialog("SUBMIT REMARKS", "Please confirm the following " +
                "remarks:\n$names${when (status) {
                    "INCOMPLETE" -> "\n\nNOTE: Students without both time-in and " +
                            "time-out will be marked as ABSENT regardless of the mark you've given. For " +
                            "facilitators that were PRESENT but did not time in and/or time out, you " +
                            "can edit their time-in and/or time-out."
                    "LATE" -> "\n\nLate time-ins of more than 30 minutes also counts as ABSENT."
                    else -> "" }}", "Submit",
            "Cancel", Global.isPINDisabled) {
            submitDtr(map)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun submitDtr(map: MutableMap<User, Schedule>) {
        setupProgress("Submitting remarks")
        val today = todayDate(true)
        val faculty = User(email = user.email, gender = user.gender, name = user.name)
        val notifyId = today.substring(2).replace("-", "") +
                "${todayHour().replace(":", "")}${schedule.id}COMPLETEDFA"
        val title = "Schedule has been completed"
        val datetime = "${todayDate(false)} ${todayHour()}"
        val updates = hashMapOf<String, Any?>()
        updates.apply {
            schedFaci = HashMap(schedFaci!!.filter { (_, faci) ->
                !map.keys.any { it.email.equals(faci.email) }
            })
            if (type == "Extras") {
                if (schedFaci!!.isNotEmpty()) {
                    map.forEach { (faci, _) ->
                        put("/Extras/${schedule.id}/faci/${faci.email!!.hashSHA256()}", null)
                    }
                } else put("/Extras/${schedule.id}", null)
            } else if (schedule.inCompleted != null) {
                if (schedFaci!!.isNotEmpty()) {
                    map.forEach { (faci, _) ->
                        put("/Completed/${schedule.id}/faci/${faci.email!!.hashSHA256()}", null)
                    }
                } else put("/Completed/${schedule.id}", null)
            } else {
                map.forEach { (faci, _) ->
                    put("/Permanents/${schedule.id}/faci/${faci.email!!.hashSHA256()}/timeIn", null)
                    put("/Permanents/${schedule.id}/faci/${faci.email.hashSHA256()}/timeOut", null)
                    put("/Permanents/${schedule.id}/faci/${faci.email.hashSHA256()}/marked", true)
                }
                if (schedFaci.isNullOrEmpty()) {
                    val (start, end) = getWeekRange(schedule.date!!)
                    put("/Permanents/${schedule.id}/extended", null)
                    put("/Permanents/${schedule.id}/completed", "$start - $end")
                    put("/Completed/${schedule.id}", null)
                }
            }
            map.forEach { (faci, _) ->
                val facilitator = User(email = faci.email, gender = faci.gender, name = faci.name)
                put("/Data/Faculty/${user.email!!.hashSHA256()}/facilitator/${faci.email!!.hashSHA256()}", facilitator)
            }
        }
        dbRef.child("/Data/Facilitator").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                map.forEach { (faci, sched) ->
                    val counter = currentData.child("/${faci.email!!.hashSHA256()}/counter").getValue(Int::class.java)
                        ?: return Transaction.success(currentData)
                    val hours = currentData.child("/${faci.email.hashSHA256()}/hrs").getValue(Float::class.java)
                        ?: return Transaction.success(currentData)
                    val render = currentData.child("/${faci.email.hashSHA256()}/render").getValue(Float::class.java)
                        ?: return Transaction.success(currentData)
                    if (sched.remark == "ABSENT") {
                        if (counter - 1 < 0) {
                            currentData.child("/${faci.email.hashSHA256()}/counter").value = 3
                            currentData.child("/${faci.email.hashSHA256()}/render").value = render + 10F
                        } else currentData.child("/${faci.email.hashSHA256()}/counter").value = counter - 1
                    } else currentData.child("/${faci.email.hashSHA256()}/hrs").value = hours + sched.hours!!
                    currentData.child("/${faci.email.hashSHA256()}/dtr/${sched.id}").value = sched
                    currentData.child("/${faci.email.hashSHA256()}/faculty/${user.email!!.hashSHA256()}").value = faculty
                    val message = "The schedule owned by ${schedule.owner} for ${schedule.date} " +
                            "${timeRangeTo12(schedule.time)} which you have ${if (type == "Extras")
                                "previously joined" else "assigned"} into has been completed and marked " +
                            "you as ${sched.remark}. You can check it out on your DTR."
                    val notify = Notifications(notifyId, title, message, datetime, "HOME")
                    currentData.child("/${faci.email.hashSHA256()}/notifications/$notifyId").value = notify
                    currentData.child("/${faci.email.hashSHA256()}/notified").value = true
                }
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?,
                committed: Boolean, currentData: DataSnapshot?
            ) {
                endProgress()
                if (error != null) {
                    snackBar(view, "Error: ${error.message}")
                    binding.checkAll.isChecked = false
                    schedFaci = schedule.faci
                    if (schedule.joined!! > 5) {
                        batchedAdapter = SchedFaciBatchedAdapter(facilitators.filter { item ->
                            schedFaci?.any { (_, filter) ->
                                item == filter && item.marked == null
                            } == true
                        } as ArrayList, this@FacultySchedule)
                        binding.rvScheduleFaci.adapter = batchedAdapter
                    } else {
                        adapter = SchedFaciAdapter(facilitators,
                            this@FacultySchedule, true, isFaculty = true,
                            isActiveOrDone = true)
                        binding.rvScheduleFaci.adapter = adapter
                    }
                } else if (committed) {
                    dbRef.updateChildren(updates)
                        .addOnSuccessListener {
                            snackBar(view, "Marks have been submitted")
                            if (schedule.joined!! > 5) {
                                batchedAdapter = SchedFaciBatchedAdapter(facilitators.filter { item ->
                                    schedFaci?.any { (_, filter) ->
                                        item == filter && item.marked == null
                                    } == true
                                } as ArrayList, this@FacultySchedule)
                                binding.rvScheduleFaci.adapter = batchedAdapter
                            }
                            schedule.faci = schedFaci
                            if (schedule.faci.isNullOrEmpty()) {
                                snackBar(view, "Schedule has been finished")
                                findNavController().popBackStack()
                            }
                        }
                }
            }
        })
    }

    private fun btnYes() {
        if (!isFinish) {
            confirmDialog = ConfirmDialog("FINISH SCHEDULE", "Please confirm finishing " +
                    "this schedule", "Finish", "Cancel", Global.isPINDisabled) {
                swap()
            }
            confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
        } else submit()
    }

    private fun navigateToExtend() {
        Global.isExtension = true
        Global.facilitators = facilitators
        findNavController().navigate(FacultyScheduleDirections.actionFacultyScheduleToFacultyRequest())
    }

    private fun extendSchedule() {
        confirmDialog = ConfirmDialog("EXTEND SCHEDULE", "Please confirm extending " +
                "this schedule", "Extend", "Cancel", Global.isPINDisabled) {
            navigateToExtend()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun navigateToEdit() {
        Global.isEditSchedule = true
        findNavController().navigate(FacultyScheduleDirections.actionFacultyScheduleToFacultyRequest())
    }

    private fun editSchedule() {
        confirmDialog = ConfirmDialog("EDIT SCHEDULE", "Please confirm editing this " +
                "schedule", "Edit", "Cancel", Global.isPINDisabled) {
            navigateToEdit()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun btnNo() {
        if (!isFinish) {
            confirmDialog = ConfirmDialog("DELETE SCHEDULE", "Please confirm deleting " +
                    "this schedule", "Delete", "Cancel", Global.isPINDisabled) {
                deleteSchedule()
            }
            confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
        } else swap()
    }

    private fun deleteSchedule() {
        setupProgress("Deleting schedule")
        val updates = hashMapOf<String, Any?>(
            "/${if (schedule.inCompleted == null) type else "Completed"}/${schedule.id}" to null)
        if (schedule.inCompleted == null) {
            val today = todayDate(true)
            val id = today.substring(2).replace("-", "") +
                    "${todayHour().replace(":", "")}${schedule.id}DELETEFA"
            val title = "Schedule has been deleted"
            val message = "The schedule owned by ${schedule.owner} for ${schedule.date} " +
                    "${timeRangeTo12(schedule.time)} which you have ${if (type == "Extras")
                        "joined" else "assigned"} into has been deleted."
            val datetime = "${todayDate(false)} ${todayHour()}"
            val notify = Notifications(id, title, message, datetime)
            updates.apply {
                facilitators.forEach { faci ->
                    put("/Data/Facilitator/${faci.email!!.hashSHA256()}/notifications/$id", notify)
                    put("/Data/Facilitator/${faci.email.hashSHA256()}/notified", true)
                }
            }
        }
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                snackBar(view, "Schedule has been deleted")
                endProgress()
                findNavController().popBackStack()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun editTimeInOut(user: User, time: String, path: String) {
        setupProgress("Processing")
        dbRef.child("/${if (schedule.inCompleted == null) type else "Completed"}/" +
                "${schedule.id}/faci/${user.email!!.hashSHA256()}/$path").setValue(time)
            .addOnSuccessListener {
                snackBar(view, "${if (path == "timeIn") "Time-in" else "Time-out"} has been added")
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun checkAll(checked: Boolean) {
        val viewHolders = adapter.getViewHolders()
        for (viewHolder in viewHolders) viewHolder.binding.checkBox.isChecked = checked
    }

    private fun setupButton() {
        if (schedule.isActive == true) {
            binding.btnNo.isEnabled = false
            binding.btnYes.isEnabled = false
            binding.btnEdit.visibility = View.GONE
            binding.activeFinishInfo.visibility = View.VISIBLE
        } else if (schedule.isDone == true) {
            binding.btnNo.isEnabled = schedule.inCompleted == true
            binding.btnYes.isEnabled = true
            if (schedule.extended == null &&
                ((type == "Extras" && isDayAfter(schedule.date!!, true, 1)) ||
                        (type == "Permanents" && isDayAfter(schedule.date!!, false, 1)))) {
                binding.btnExtend.visibility = View.VISIBLE
            }
            binding.btnEdit.visibility = View.GONE
            binding.activeFinishInfo.visibility = View.VISIBLE
        } else {
            binding.btnNo.isEnabled = true
            binding.btnYes.isEnabled = false
            binding.btnEdit.visibility = View.VISIBLE
        }
    }

    private fun setupDetails() {
        binding.title.text = schedule.title
        binding.date.text = schedule.date
        binding.time.text = timeRangeTo12(schedule.time)
        binding.room.text = schedule.room
        if (schedule.subject != null) {
            binding.subject.text = schedule.subject
            binding.subjects.visibility = View.VISIBLE
        }
        if (schedule.detail != null) {
            binding.detail.text = schedule.detail
            binding.detail.visibility = View.VISIBLE
        }
        binding.facilitator.text = "${schedule.joined}/${schedule.need}"
        setupButton()
    }

    private fun swap() {
        if (isFinish) {
            binding.checkAll.visibility = View.GONE
            binding.finishInfo.visibility = View.GONE
            if (schedule.isActive != null || schedule.isDone != null)
                binding.activeFinishInfo.visibility = View.VISIBLE
            binding.facilitator.visibility = View.VISIBLE
            binding.btnNo.isEnabled = schedule.isDone == true && schedule.joined == 0
            binding.btnNo.text = "Delete"
            binding.btnYes.text = "Finish"
            adapter = SchedFaciAdapter(facilitators, this, false, isFaculty = true,
                isActiveOrDone = schedule.isActive == true || schedule.isDone == true
            )
            binding.rvScheduleFaci.adapter = adapter
            binding.swipeRefresh.isEnabled = true
            addListener(faciQuery, faciEventListener)
        } else {
            if (schedule.joined!! > 5) binding.finishInfo.text = "Since this schedule has more than 5 facilitators, " +
                    "you can mark them in batches of up to 5${if (facilitators.any { it.marked == true }) 
                        "\nNOTE: The facilitator/s you don't see here have already been marked" else ""}"
            else binding.checkAll.visibility = View.VISIBLE
            binding.finishInfo.visibility = View.VISIBLE
            binding.activeFinishInfo.visibility = View.GONE
            binding.facilitator.visibility = View.GONE
            binding.btnNo.isEnabled = true
            binding.btnNo.text = "Cancel"
            binding.btnYes.text = "Submit"
            if (schedule.joined!! > 5) {
                batchedAdapter = SchedFaciBatchedAdapter(facilitators.filter { it.marked == null } as ArrayList, this)
                binding.rvScheduleFaci.adapter = batchedAdapter
            } else {
                adapter = SchedFaciAdapter(facilitators, this, true, isFaculty = true,
                    isActiveOrDone = true)
                binding.rvScheduleFaci.adapter = adapter
            }
            removeListener(faciQuery)
            binding.swipeRefresh.isEnabled = false
        }
        isFinish = !isFinish
    }

    private fun processScheduleBatched(viewHolder: SchedFaciBatchedViewHolder, sched: Schedule): Pair<Schedule, String?> {
        var state: String? = null
        val schedule = sched.copy(
            timeIn = viewHolder.user.timeIn,
            timeOut = viewHolder.user.timeOut)
        if (viewHolder.binding.present.isChecked) {
            if (viewHolder.user.timeIn != null && viewHolder.user.timeOut != null) {
                if (!isLateTimeIn(schedule.time!!.substringBefore(" - "), viewHolder.user.timeIn!!)) {
                    schedule.hours = calculateTimeRange(
                        if (isEarlyLate(schedule.time!!.substringBefore(" - "),
                                viewHolder.user.timeIn!!, true))
                            schedule.time!!.substringBefore(" - ")
                        else viewHolder.user.timeIn!!,
                        if (isEarlyLate(schedule.time!!.substringAfter(" - "),
                                viewHolder.user.timeOut!!, false))
                            schedule.time!!.substringAfter(" - ")
                        else viewHolder.user.timeOut!!)
                    schedule.remark = "PRESENT"
                } else {
                    schedule.hours = 0F
                    schedule.remark = "ABSENT"
                    state = "LATE"
                }
            } else {
                schedule.hours = 0F
                schedule.remark = "ABSENT"
                state = "INCOMPLETE"
            }
        } else {
            schedule.hours = 0F
            schedule.remark = "ABSENT"
        }
        return Pair(schedule, state)
    }

    private fun processSchedule(viewHolder: SchedFaciViewHolder, sched: Schedule): Pair<Schedule, String?> {
        var state: String? = null
        val schedule = sched.copy(
            timeIn = viewHolder.user.timeIn,
            timeOut = viewHolder.user.timeOut)
        if (viewHolder.binding.checkBox.isChecked) {
            if (viewHolder.user.timeIn != null && viewHolder.user.timeOut != null) {
                if (!isLateTimeIn(schedule.time!!.substringBefore(" - "), viewHolder.user.timeIn!!)) {
                    schedule.hours = calculateTimeRange(
                        if (isEarlyLate(schedule.time!!.substringBefore(" - "),
                                viewHolder.user.timeIn!!, true))
                            schedule.time!!.substringBefore(" - ")
                        else viewHolder.user.timeIn!!,
                        if (isEarlyLate(schedule.time!!.substringAfter(" - "),
                                viewHolder.user.timeOut!!, false))
                            schedule.time!!.substringAfter(" - ")
                        else viewHolder.user.timeOut!!)
                    schedule.remark = "PRESENT"
                } else {
                    schedule.hours = 0F
                    schedule.remark = "ABSENT"
                    state = "LATE"
                }
            } else {
                schedule.hours = 0F
                schedule.remark = "ABSENT"
                state = "INCOMPLETE"
            }
        } else {
            schedule.hours = 0F
            schedule.remark = "ABSENT"
        }
        return Pair(schedule, state)
    }

    private fun toggleViewHolders(viewHolders: List<SchedFaciBatchedViewHolder>, isDisable: Boolean) {
        if (viewHolders.count { it.binding.mark.children.any { rb -> (rb as RadioButton).isChecked } } > 5) {
            viewHolders.filter { it.binding.mark.children.any { rb -> (rb as RadioButton).isChecked } }
                .drop(5).forEach { viewHolder ->
                    viewHolder.binding.apply {
                        absent.isChecked = false
                        present.isChecked = false
                    }
                }
        }
        viewHolders.forEach { viewHolder ->
            if (isDisable) {
                if (!viewHolder.binding.mark.children.any { rb -> (rb as RadioButton).isChecked })
                    viewHolder.binding.apply {
                        cvFacilitator.isClickable = false
                        container.setBackgroundColor(viewHolder.itemView.context.getColor(R.color.gray))
                        absent.isEnabled = false
                        present.isEnabled = false
                    }
                else viewHolder.binding.apply {
                    cvFacilitator.isClickable = true
                    container.setBackgroundResource(R.drawable.rv_items)
                    absent.isEnabled = true
                    present.isEnabled = true
                }
            } else viewHolder.binding.apply {
                cvFacilitator.isClickable = true
                container.setBackgroundResource(R.drawable.rv_items)
                absent.isEnabled = true
                present.isEnabled = true
            }
        }
    }

    private fun removeListeners() {
        removeListener(watchQuery)
        removeListener(faciQuery)
    }

    private fun addListener(query: Query?, listener: ValueEventListener?) {
        if (!listenerMap.containsKey(query) && query != null && listener != null) {
            query.addValueEventListener(listener)
            listenerMap[query] = listener
        }
    }

    private fun removeListener(query: Query?) {
        val listener = listenerMap[query]
        if (listener != null && query != null) {
            query.removeEventListener(listener)
            listenerMap.remove(query)
        }
    }

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        callbackFalse.remove()
    }
}