package com.upang.hkfacilitator.ui.facilitator

import android.annotation.SuppressLint
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.upang.hkfacilitator.adapters.SchedFaciAdapter
import com.upang.hkfacilitator.databinding.FacilitatorScheduleBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isActive
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.isDayBefore
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.ui.popup.ConfirmDialog
import com.upang.hkfacilitator.utils.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

@SuppressLint("SetTextI18n")
class FacilitatorSchedule : Fragment(), ConnectionStateListener, SchedFaciClickListener {

    private lateinit var binding: FacilitatorScheduleBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private var watchEventListener: ValueEventListener? = null
    private var schedEventListener: ValueEventListener? = null
    private lateinit var facilitators: ArrayList<User>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var dbRef: DatabaseReference
    private lateinit var schedule: Schedule
    private lateinit var account: Account
    private var watchQuery: Query? = null
    private var schedQuery: Query? = null
    private lateinit var type: String
    private lateinit var user: User
    private var isDoneSetup = false
    private var isPaused = false
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
        binding = FacilitatorScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        isPaused = false

        binding.rvScheduleFaci.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt()
        )

        if (Global.offset == null) getServerTime()
        fetchData()

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnAction.setOnDebouncedClickListener {
            action()
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
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
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
                    addListener(schedQuery, schedEventListener)
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

    override fun onTimeClick(user: User, isTimeIn: Boolean) {}
    override fun onCheckClick() {}

    private fun initializations() {
        user = Global.userData!!
        account = Global.account!!
        schedule = Global.schedule!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        type = if (schedule.id!![1].isLetter()) "Permanents" else "Extras"
        facilitators = arrayListOf()
        listenerMap = mutableMapOf()
    }

    private fun fetchData() {
        setupProgress("Loading data")
        setupDetails()
        watchUser {
            watchSched {
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
                    binding.rvScheduleFaci.adapter = SchedFaciAdapter(facilitators, this@FacilitatorSchedule,
                        false, isFaculty = false, isActiveOrDone = schedule.isActive ?: false)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun watchSched(onComplete: () -> Unit) {
        schedQuery = dbRef.child("/$type/${schedule.id}")
        schedEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                facilitators.clear()
                if (!snapshot.exists() && !snapshot.hasChildren()) {
                    snackBar(view, "The schedule has been finished or deleted")
                    findNavController().popBackStack()
                } else {
                    val blacklisted = snapshot.child("/faci/${user.email!!.hashSHA256()}/blacklisted").getValue(Boolean::class.java)
                    if (blacklisted == null) {
                        schedule = snapshot.getValue(Schedule::class.java)!!
                        schedule.isDone = if (isPast(schedule, type == "Extras")) true else null
                        schedule.isActive = if (schedule.suspended == null && isActive(schedule, false)) true else null
                        for (item in snapshot.child("faci").children) {
                            val faci = item.getValue(User::class.java)!!
                            facilitators.add(faci)
                            if (faci.email == user.email) {
                                schedule.isJoinedIn = true
                                user.timeIn = faci.timeIn
                                user.timeOut = faci.timeOut
                            }
                        }
                        binding.facilitator.text = "${facilitators.size}/${schedule.need}"
                        Global.schedule = schedule
                        setupButton()
                        setupData {
                            onComplete()
                        }
                    } else {
                        snackBar(view, "You have been blacklisted from rejoining the schedule")
                        findNavController().popBackStack()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(schedQuery, schedEventListener)
    }

    private fun watchUser(onComplete: () -> Unit) {
        watchQuery = dbRef.child("/Data/Facilitator/${user.email!!.hashSHA256()}/email")
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

    private fun setupButton() {
        if (schedule.isJoinedIn == true && schedule.isActive == true) {
            if (user.timeIn == null) binding.btnAction.text = "TIME-IN"
            else if (user.timeOut == null) binding.btnAction.text = "TIME-OUT"
            else binding.btnAction.isEnabled = false
        } else if (schedule.isJoinedIn == true) {
            if (type == "Permanents") {
                binding.btnAction.apply {
                    text = "TIME-IN"
                    isEnabled = false
                }
            } else if (isDayBefore(schedule.date!!, true, 1) &&
                schedule.isDone == null)
                binding.btnAction.apply {
                    text = "TIME-IN"
                    isEnabled = false
                }
            else if (schedule.isDone == true)
                binding.btnAction.apply {
                    text = "FINISHED"
                    isEnabled = false
                }
            else binding.btnAction.apply {
                text = "WITHDRAW"
                isEnabled = true
            }
        } else binding.btnAction.text = "JOIN"
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

    private fun action() {
        if (schedule.isActive == true && schedule.isJoinedIn == true) {
            if (user.timeIn == null) timeIn()
            else timeOut()
        } else if (schedule.isJoinedIn == true) withdraw()
        else if (schedule.isDone == true) {
            snackBar(view, "Unsuccessful. This schedule is now finished.")
            setupButton()
        } else join()
    }

    private fun timeOutSchedule() {
        setupProgress("Timing out")
        dbRef.child("/$type/${schedule.id}/faci/${user.email!!.hashSHA256()}/timeOut")
            .setValue(todayHour())
            .addOnSuccessListener {
                snackBar(view, "Timed out successfully")
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun timeOut() {
        confirmDialog = ConfirmDialog("TIME OUT", "Are you sure you want to time out " +
                "for this schedule?", "Time out", "Cancel", Global.isPINDisabled) {
            timeOutSchedule()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun timeInSchedule() {
        binding.btnAction.isEnabled = false
        setupProgress("Timing in")
        dbRef.child("/$type/${schedule.id}/faci/${user.email!!.hashSHA256()}/timeIn")
            .setValue(todayHour())
            .addOnSuccessListener {
                snackBar(view, "Timed in successfully")
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun timeIn() {
        confirmDialog = ConfirmDialog("TIME IN", "Are you sure you want to time in " +
                "for this schedule?", "Time in", "Cancel", Global.isPINDisabled) {
            timeInSchedule()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun withdrawSchedule() {
        setupProgress("Processing")
        dbRef.child("/Extras/${schedule.id}").runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val joined = currentData.child("/joined").getValue(Int::class.java)
                        ?: return Transaction.success(currentData)
                    currentData.child("/faci/${user.email!!.hashSHA256()}/blacklisted").value = true
                    currentData.child("/joined").value = joined - 1
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    endProgress()
                    if (error != null) snackBar(view, "Error: ${error.message}")
                    else if (committed) snackBar(view, "Successfully withdrawn from this schedule")
                }
            })
    }

    private fun joinSchedule() {
        setupProgress("Joining schedule")
        dbRef.child("/Extras/${schedule.id}").runTransaction(
            object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val joined = currentData.child("/joined").getValue(Int::class.java)
                        ?: return Transaction.success(currentData)
                    val need = currentData.child("/need").getValue(Int::class.java)!!
                    if (joined + 1 > need) return Transaction.abort()
                    val faci = User(email = user.email, name = user.name, gender = user.gender, course = user.course)
                    currentData.child("/faci/${user.email!!.hashSHA256()}").value = faci
                    currentData.child("/joined").value = joined + 1
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?,
                    committed: Boolean, currentData: DataSnapshot?
                ) {
                    endProgress()
                    if (error != null) snackBar(view, "Error: ${error.message}")
                    else if (committed) snackBar(view, "Successfully joined the schedule")
                    else snackBar(view, "Schedule is full")
                }
            })
    }

    private fun withdraw() {
        confirmDialog = ConfirmDialog("WITHDRAW SCHEDULE", "Are you sure you want to " +
                "withdraw from this schedule?\nNOTE: Be advised that you will NOT BE ALLOWED to rejoin " +
                "this schedule unless an Admin or Manager assigns you back.", "Withdraw",
            "Cancel", Global.isPINDisabled) {
            withdrawSchedule()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun join() {
        confirmDialog = ConfirmDialog("JOIN SCHEDULE", "Are you sure you want to " +
                "join this schedule?\nNOTE: You will not be able to withdraw from this schedule one day " +
                "before the activity", "Join", "Cancel", Global.isPINDisabled) {
            joinSchedule()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun removeListeners() {
        removeListener(watchQuery)
        removeListener(schedQuery)
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