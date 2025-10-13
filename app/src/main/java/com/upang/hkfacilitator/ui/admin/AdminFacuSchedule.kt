package com.upang.hkfacilitator.ui.admin

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.upang.hkfacilitator.adapters.SchedFaciAdapter
import com.upang.hkfacilitator.databinding.AdminFacuScheduleBinding
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.calculateTimeRange
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.ui.popup.*
import com.upang.hkfacilitator.utils.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

@SuppressLint("SetTextI18n")
class AdminFacuSchedule : Fragment(), ConnectionStateListener, SchedFaciClickListener {

    private lateinit var binding: AdminFacuScheduleBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private var watchEventListener: ValueEventListener? = null
    private var facilitatorDialog: FacilitatorDialog? = null
    private lateinit var facilitators: ArrayList<User>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var dbRef: DatabaseReference
    private lateinit var schedule: Schedule
    private lateinit var account: Account
    private var watchQuery: Query? = null
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
        binding = AdminFacuScheduleBinding.inflate(inflater, container, false)
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
            "#D9BD2D".toColorInt())

        if (Global.offset == null) getServerTime()
        fetchData()

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnDecline.setOnDebouncedClickListener {
            decline()
        }

        binding.btnApprove.setOnDebouncedClickListener {
            approve()
        }

        binding.btnAssignFaci.setOnDebouncedClickListener {
            assign()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isConnected(requireContext())) {
            isPaused = true
            setupProgress("Waiting for connection")
            watchEventListener?.let { watchQuery?.removeEventListener(it) }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        Global.schedule = null
        if (facilitatorDialog != null) if (facilitatorDialog!!.isAdded) facilitatorDialog!!.dismiss()
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
                else watchEventListener?.let { watchQuery?.addValueEventListener(it) }
            }
        }
    }

    override fun onNetworkLost() {
        requireActivity().runOnUiThread {
            isPaused = true
            setupProgress("Waiting for connection")
            if (isConnected(requireContext())) endProgress()
            else watchEventListener?.let { watchQuery?.removeEventListener(it) }
        }
    }

    override fun onTimeClick(user: User, isTimeIn: Boolean) {}
    override fun onCheckClick() {}

    private fun initializations() {
        user = Global.userData!!
        account = Global.account!!
        schedule = Global.schedule!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        type = if (schedule.id!![1].isLetter() &&
            schedule.extension == null) "Permanents" else "Extras"
        facilitators = arrayListOf()
        listenerMap = mutableMapOf()
    }

    private fun fetchData() {
        setupProgress("Loading data")
        setupDetails()
        watchSched {
            isDoneSetup = true
            endProgress()
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
                    binding.rvScheduleFaci.adapter = SchedFaciAdapter(facilitators, this@AdminFacuSchedule,
                        false, isFaculty = false, isActiveOrDone = false)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun watchSched(onComplete: () -> Unit) {
        watchQuery = dbRef.child("/$type/${schedule.id}")
        watchEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                facilitators.clear()
                if (!snapshot.exists() && !snapshot.hasChildren()) {
                    snackBar(view, "Schedule has been finished or deleted")
                    findNavController().popBackStack()
                } else {
                    schedule = snapshot.getValue(Schedule::class.java)!!
                    Global.schedule = schedule
                    for (faci in snapshot.child("faci").children) {
                        val blacklisted = faci.child("blacklisted").getValue(Boolean::class.java)
                        if (blacklisted == null) facilitators.add(faci.getValue(User::class.java)!!)
                    }
                    binding.facilitator.text = "${facilitators.size}/${schedule.need}"
                    setupData {
                        onComplete()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        watchEventListener?.let { watchQuery?.addValueEventListener(it) }
    }

    private fun assign() {
        val faciList = Global.facilitators!!.filter { faci -> !facilitators.any {
            faci.email!!.contains(it.email!!, true) } } as ArrayList<User>
        facilitatorDialog = FacilitatorDialog(dbRef, faciList, schedule) { committed, size ->
            if (committed) snackBar(view, "Facilitator${if (size>1) "s have" else " has"} been assigned")
            else snackBar(view, "Assigning $size facilitator${if (size>1) "s" else ""} exceeds the limit")
        }
        facilitatorDialog!!.show(childFragmentManager, "FacilitatorDialog")
    }

    private fun approveExtension() {
        val today = todayDate(true)
        val id = "${today.substring(2).replace("-", "")}${todayHour()
            .replace(":", "")}${schedule.email!!
            .replace(".", "").substring(0, 6).uppercase()}EXT"
        val notifyID = today.substring(2).replace("-", "") +
                "${todayHour().replace(":", "")}${schedule.id}APPROVEAD"
        val title = "Schedule extension approved"
        var message = "Your schedule extension request for ${schedule.date} " +
                "${timeRangeTo12(schedule.time)} has been approved."
        val datetime = "${todayDate(false)} ${todayHour()}"
        var notify = Notifications(notifyID, title, message, datetime)
        val map = mutableMapOf<User, Schedule>()
        schedule.faci!!.forEach { faci ->
            val schedule = schedule.copy(
                id = id,
                detail = null,
                email = null,
                joined = null,
                need = null,
                restrict = null,
                room = null,
                subject = null,
                isDone = null,
                edited = null,
                faci = null,
                extension = null)
            schedule.hours = calculateTimeRange(
                schedule.time!!.substringBefore(" - "),
                schedule.time!!.substringAfter(" - "))
            schedule.remark = "PRESENT"
            map[faci.value] = schedule
        }
        val updates = hashMapOf(
            "/Extras/${schedule.id}" to null,
            "/Data/Faculty/${schedule.email!!.hashSHA256()}/extension" to null,
            "/Data/Faculty/${schedule.email!!.hashSHA256()}/notifications/$notifyID" to notify,
            "/Data/Faculty/${schedule.email!!.hashSHA256()}/notified" to true)
        setupProgress("Submitting records")
        dbRef.child("/Data/Facilitator").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                map.forEach { (faci, sched) ->
                    val check = currentData.child("/${faci.email!!.hashSHA256()}/dtr/${sched.id}").getValue(Schedule::class.java)
                    if (check != null) return Transaction.abort()
                    val hours = currentData.child("/${faci.email.hashSHA256()}/hrs").getValue(Float::class.java)
                        ?: return Transaction.success(currentData)
                    currentData.child("/${faci.email.hashSHA256()}/hrs").value = hours + sched.hours!!
                    currentData.child("/${faci.email.hashSHA256()}/dtr/${sched.id}").value = sched
                    message = "The schedule extension requested by ${schedule.owner} for ${schedule.date} " +
                            "${timeRangeTo12(schedule.time)} which you have ${if (!schedule.id!![1].isLetter())
                                "previously joined" else "assigned"} into has been approved." +
                            "You can check it out on your DTR."
                    notify = Notifications(notifyID, title, message, datetime, "HOME")
                    currentData.child("/${faci.email.hashSHA256()}/notifications/$notifyID").value = notify
                    currentData.child("/${faci.email.hashSHA256()}/notified").value = true
                }
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?,
                committed: Boolean, currentData: DataSnapshot?
            ) {
                endProgress()
                if (error != null) snackBar(view, "Error: ${error.message}")
                else if (committed) dbRef.updateChildren(updates)
                else snackBar(view, "Schedule extension has already been approved")
            }
        })
    }

    private fun approve() {
        confirmDialog = ConfirmDialog("APPROVE EXTENSION", "Are you sure to approve " +
                "this schedule extension?", "Approve", "Cancel", Global.isPINDisabled) {
            approveExtension()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun declineExtension() {
        val today = todayDate(true)
        val notifyID = today.substring(2).replace("-", "") +
                "${todayHour().replace(":", "")}${schedule.id}DECLINEAD"
        val title = "Schedule extension declined"
        val message = "Sorry. Your schedule extension request for ${schedule.date} " +
                "${timeRangeTo12(schedule.time)} has been declined."
        val datetime = "${todayDate(false)} ${todayHour()}"
        val notify = Notifications(notifyID, title, message, datetime)
        setupProgress("Processing")
        val updates = hashMapOf(
            "/Extras/${schedule.id}" to null,
            "/Data/Faculty/${schedule.email!!.hashSHA256()}/extension" to null,
            "/Data/Faculty/${schedule.email!!.hashSHA256()}/notifications/$notifyID" to notify,
            "/Data/Faculty/${schedule.email!!.hashSHA256()}/notified" to true)
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun decline() {
        confirmDialog = ConfirmDialog("DECLINE EXTENSION", "Are you sure to decline " +
                "this schedule extension?", "Decline", "Cancel", Global.isPINDisabled) {
            declineExtension()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
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
        if (schedule.joined != null && schedule.need != null)
            binding.facilitator.text = "${schedule.joined}/${schedule.need}"
        else binding.facilitator.visibility = View.GONE
        if (schedule.extension == true) {
            binding.btnAssignFaci.visibility = View.GONE
            binding.extension.visibility = View.VISIBLE
        } else if (type == "Permanents") binding.buttons.visibility = View.GONE
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