package com.upang.hkfacilitator.ui.faculty

import android.annotation.SuppressLint
import android.app.*
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.*
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.SchedFaciAdapter
import com.upang.hkfacilitator.databinding.FacultyRequestBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.getWeekRange
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.showTimePicker
import com.upang.hkfacilitator.models.Global.stringToTime
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.ui.popup.ConfirmDialog
import com.upang.hkfacilitator.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

@SuppressLint("SetTextI18n, ClickableViewAccessibility")
class FacultyRequest : Fragment(), ConnectionStateListener, SchedFaciClickListener {

    private lateinit var binding: FacultyRequestBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private var joinedEventListener: ValueEventListener? = null
    private var watchEventListener: ValueEventListener? = null
    private lateinit var facilitator: ArrayList<User>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var courses: ArrayList<String>
    private lateinit var adapter: SchedFaciAdapter
    private val calendar = Calendar.getInstance()
    private lateinit var manager: ArrayList<User>
    private lateinit var dbRef: DatabaseReference
    private lateinit var joined: ArrayList<User>
    private lateinit var admin: ArrayList<User>
    private lateinit var schedule: Schedule
    private var joinedQuery: Query? = null
    private var watchQuery: Query? = null
    private lateinit var account: Account
    private var yearPosition: Int? = null
    private var restrict: Boolean? = null
    private var subject: String? = null
    private var detail: String? = null
    private var course: String? = null
    private var start: String? = null
    private var title: String? = null
    private var isSubmitting = false
    private var year: String? = null
    private var time: String? = null
    private var room: String? = null
    private var need: String? = null
    private var date: String? = null
    private var type: String? = null
    private lateinit var user: User
    private var day: String? = null
    private var end: String? = null
    private var isDoneSetup = false
    private var id: String? = null
    private var isChecking = false
    private var isPaused = false
    private var init = false
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
        binding = FacultyRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        isPaused = false

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.rvFacilitators.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.checkBox.setOnCheckedChangeListener { _, checked ->
            clear()
            restrictListener(checked)
        }

        binding.type.setOnCheckedChangeListener { rg, id ->
            clear()
            typeListener(rg, id)
        }

        binding.checkAll.setOnCheckedChangeListener { _, checked ->
            checkAll(checked)
        }

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        if (Global.offset == null) getServerTime()
        subjectListener()
        setupData()
        titleListener()
        roomListener()
        detailListener()
        dateListener()
        startListener()
        endListener()
        needListener()

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnDatePicker.setOnDebouncedClickListener {
            clear()
            showDatePicker()
        }

        binding.btnStartPicker.setOnDebouncedClickListener {
            clear()
            showTimePicker(true)
        }

        binding.btnEndPicker.setOnDebouncedClickListener {
            clear()
            showTimePicker(false)
        }

        binding.scroller.setOnDebouncedClickListener {
            clear()
        }

        binding.btnRequest.setOnDebouncedClickListener {
            clear()
            requestFaci()
            binding.scroller.visibility = View.GONE
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
        Global.isEditSchedule = false
        Global.isExtension = false
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            if (isPaused) {
                isPaused = false
                endProgress()
                if (Global.offset == null) getServerTime()
                checkTimeChange()
                if (isChecking) setupProgress("Checking schedule")
                if (isSubmitting) setupProgress("Submitting request")
                if (!isDoneSetup) setupData()
                else {
                    addListener(watchQuery, watchEventListener)
                    addListener(joinedQuery, joinedEventListener)
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

    override fun onCheckClick() {
        binding.btnRequest.isEnabled = adapter.getViewHolders().any { it.binding.checkBox.isChecked }
    }

    private fun initializations() {
        user = Global.userData!!
        account = Global.account!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        if (Global.isEditSchedule || Global.isExtension) {
            schedule = Global.schedule!!
            type = if (schedule.id!![1].isLetter()) "Permanents" else "Extras"
        }
        listenerMap = mutableMapOf()
        facilitator = arrayListOf()
        manager = arrayListOf()
        courses = arrayListOf()
        joined = arrayListOf()
        admin = arrayListOf()
    }

    private fun setupData() {
        setupProgress("Loading")
        watchFaculty {
            setupCourse {
                if (Global.isEditSchedule) {
                    watchJoined {
                        setupEdit(true)
                        isDoneSetup = true
                        endProgress()
                    }
                } else if (Global.isExtension) {
                    setupEdit(false)
                    isDoneSetup = true
                    endProgress()
                } else {
                    isDoneSetup = true
                    endProgress()
                }
            }
        }
    }

    private fun watchJoined(onComplete: () -> Unit) {
        joinedQuery = dbRef.child("/$type/${schedule.id}/faci")
        joinedEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                joined.clear()
                if (snapshot.exists()) for (faci in snapshot.children)
                    joined.add(faci.getValue(User::class.java)!!)
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(joinedQuery, joinedEventListener)
    }

    private fun watchFaculty(onComplete: () -> Unit) {
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

    private fun setupCourse(onComplete: () -> Unit) {
        dbRef.child("/Courses").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    courses.clear()
                    if (snapshot.exists()) for (course in snapshot.children)
                        courses.add(course.getValue(String::class.java)!!)
                    Global.courses = courses
                    courses.add(0, "None")
                    val course = courses.map { it.substringAfter('.').substringBeforeLast('.') }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, course)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.course.adapter = adapter
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun checkAll(checked: Boolean) {
        val viewHolders = adapter.getViewHolders()
        for (viewHolder in viewHolders) viewHolder.binding.checkBox.isChecked = checked
    }

    private fun setupEdit(isEdit: Boolean) {
        binding.title.setText("${schedule.title}${if (!isEdit) " - EXT" else ""}")
        binding.room.setText(schedule.room)
        if (schedule.restrict!!) {
            binding.checkBox.isChecked = true
            binding.course.isEnabled = false
            binding.year.isEnabled = false
        } else {
            binding.checkBox.isEnabled = false
            binding.checkBox.isChecked = false
        }
        if (schedule.subject != null) {
            binding.course.setSelection(courses.indexOfFirst {
                it.contains(schedule.subject!!.removeYear()) })
            yearPosition = when (schedule.subject!!.removeCourse()) {
                "11", "1" -> 0
                "12", "2" -> 1
                "3" -> 2
                else -> 3
            }
            init = true
        } else binding.course.setSelection(0)
        binding.detail.setText(schedule.detail)
        if (type == "Permanents") {
            binding.permanent.isChecked = true
            binding.dayPicker.setSelection(
                when (schedule.date) {
                    "Monday" -> 0
                    "Tuesday" -> 1
                    "Wednesday" -> 2
                    "Thursday" -> 3
                    "Friday" -> 4
                    "Saturday" -> 5
                    else -> 6
                })
            binding.dayPicker.isEnabled = false
        } else {
            binding.extra.isChecked = true
            binding.date.setText(schedule.date)
            binding.date.isEnabled = false
            binding.btnDatePicker.isEnabled = false
        }
        binding.permanent.isEnabled = false
        binding.extra.isEnabled = false
        binding.start.isEnabled = false
        binding.btnStartPicker.isEnabled = false
        if (isEdit) {
            binding.start.setText(schedule.time!!.substringBefore(" - "))
            binding.end.setText(schedule.time!!.substringAfter(" - "))
            binding.end.isEnabled = false
            binding.btnEndPicker.isEnabled = false
            binding.need.setText(schedule.need.toString())
        } else {
            binding.title.isEnabled = false
            binding.room.isEnabled = false
            binding.course.isEnabled = false
            binding.year.isEnabled = false
            binding.detail.isEnabled = false
            binding.checkBox.isEnabled = false
            binding.btnRequest.text = "Request extension"
            binding.start.setText(schedule.time!!.substringAfter(" - "))
            binding.need.visibility = View.GONE
            adapter = SchedFaciAdapter(Global.facilitators!!, this,
                true, isFaculty = true, isActiveOrDone = false)
            binding.rvFacilitators.adapter = adapter
            binding.facilitators.visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({
                binding.checkAll.isChecked = true
            }, 1)
        }
    }

    private fun checkSchedule(onComplete: (Boolean, Boolean, Int?) -> Unit) {
        isChecking = true
        dbRef.child("/$type/$id")
            .addListenerForSingleValueEvent( object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val sched = snapshot.getValue(Schedule::class.java)!!
                        val end = if (!sched.id!!.last().isLetter()) sched.id!!
                            .substringAfter(user.email!!.replace(".", "")
                                .substring(0, 6).uppercase()).toInt() else 0
                        if (sched.edited != null) onComplete(false, true, end)
                        else onComplete(true, false, null)
                    } else onComplete(false, false, null)
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    endProgress()
                }
            })
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun submitNewSchedule() {
        var newSchedule: Schedule
        val updates = hashMapOf<String, Any>()
        val today = todayDate(true)
        val notifyId = today.substring(2).replace("-", "") +
                "${todayHour().replace(":", "")}${id}NEWFA"
        val datetime = "${todayDate(false)} ${todayHour()}"
        GlobalScope.launch(Dispatchers.Main) {
            if (type == "Permanents" || Global.isExtension) {
                val (start, end) = getWeekRange(date!!)
                newSchedule = Schedule(id, title, user.name, user.email, need!!.toInt(),
                    room, date, time, 0, detail, subject, restrict, "$start - $end")
                if (Global.isExtension) {
                    val faci = hashMapOf<String, User>()
                    val viewHolders = adapter.getViewHolders()
                    viewHolders.forEach {
                        if (it.binding.checkBox.isChecked)
                            faci[it.user.email!!.hashSHA256()] = it.user.copy(
                                profileUrl = null,
                                timeIn = null,
                                timeOut = null)
                    }
                    newSchedule = newSchedule.copy(
                        id = "${id}EXT",
                        need = null,
                        joined = null,
                        restrict = null,
                        completed = null,
                        extension = true,
                        faci = faci)
                    updates["/Extras/${newSchedule.id}"] = newSchedule
                    updates["$type/${schedule.id}/extended"] = true
                    updates["/Data/Faculty/${user.email!!.hashSHA256()}/extension"] = true
                } else updates["/$type/${newSchedule.id}"] = newSchedule
                val title = "New ${if (Global.isExtension) "extension schedule" else "permanent facilitator"} request "
                val message = if (!Global.isExtension) "A new permanent facilitator request has been submitted by ${user.name}."
                else "A new extension schedule request has been submitted by ${user.name} for your approval."
                val notify = Notifications(notifyId, title, message, datetime, "SCHEDULE.${newSchedule.id}")
                fetchManagers()
                manager.forEach { user ->
                    updates["/Data/Manager/${user.email!!.hashSHA256()}/notifications/$notifyId"] = notify
                    updates["/Data/Manager/${user.email.hashSHA256()}/notified"] = true
                }
                fetchAdmins()
                admin.forEach { user ->
                    updates["/Data/Admin/${user.email!!.hashSHA256()}/notifications/$notifyId"] = notify
                    updates["/Data/Admin/${user.email.hashSHA256()}/notified"] = true
                }
            } else {
                newSchedule = Schedule(id, title, user.name, user.email, need!!.toInt(),
                    room, date, time, 0, detail, subject, restrict)
                val title = "New schedule available"
                val message = "A new schedule owned by ${user.name} is now available. Hurry up and join now!"
                val notify = Notifications(notifyId, title, message, datetime, "SCHEDULE.${newSchedule.id}")
                updates["/$type/${newSchedule.id}"] = newSchedule
                fetchFacilitators()
                facilitator.forEach { user ->
                    updates["/Data/Facilitator/${user.email!!.hashSHA256()}/notifications/$notifyId"] = notify
                    updates["/Data/Facilitator/${user.email.hashSHA256()}/notified"] = true
                }
            }
            dbRef.updateChildren(updates)
                .addOnSuccessListener {
                    snackBar(view, "${if (Global.isExtension) "Extension request" else "Request"} has been successfully submitted")
                    isSubmitting = false
                    endProgress()
                    clearForm()
                    if (Global.isExtension) {
                        Global.schedule!!.extended = true
                        findNavController().popBackStack()
                    }
                }
                .addOnFailureListener {
                    snackBar(view, "Error: ${it.message}")
                    isSubmitting = false
                    endProgress()
                }
        }
    }

    private suspend fun fetchManagers() {
        val snapshot = if (restrict == true) dbRef.child("/Data/Manager")
            .orderByChild("course").equalTo(subject!!.removeYear()).get().await()
        else dbRef.child("/Data/Manager").get().await()
        manager.clear()
        if (snapshot.exists()) for (user in snapshot.children)
            manager.add(user.getValue(User::class.java)!!)
    }

    private suspend fun fetchAdmins() {
        val snapshot = dbRef.child("/Data/Admin").get().await()
        admin.clear()
        if (snapshot.exists()) for (user in snapshot.children)
            admin.add(user.getValue(User::class.java)!!)
    }

    private suspend fun fetchFacilitators() {
        val snapshot = if (restrict == true) dbRef.child("/Data/Facilitator")
            .orderByChild("course").startAt(subject!!.removeYear())
            .endAt("${subject!!.removeYear()}\uF8FF").get().await()
        else dbRef.child("/Data/Facilitator").get().await()
        facilitator.clear()
        if (snapshot.exists()) for (user in snapshot.children)
            facilitator.add(user.getValue(User::class.java)!!)
    }

    private fun submitEditedSchedule() {
        val today = todayDate(true)
        val updates = hashMapOf<String, Any?>("/$type/${schedule.id}/edited" to true)
        if (schedule.title != title) updates["/$type/${schedule.id}/title"] = title
        if (schedule.room != room) updates["/$type/${schedule.id}/room"] = room
        if (schedule.subject != subject) updates["/$type/${schedule.id}/subject"] = subject
        if (schedule.restrict != restrict) updates["/$type/${schedule.id}/restrict"] = restrict
        if (schedule.detail != detail) updates["/$type/${schedule.id}/detail"] = detail
        if (schedule.need != need!!.toInt()) updates["/$type/${schedule.id}/need"] = need!!.toInt()
        val id = today.substring(2).replace("-", "") +
                "${todayHour().replace(":", "")}${schedule.id}EDITFA"
        val notifyTitle = "Schedule has been edited"
        val message = "The schedule owned by ${schedule.owner} for ${schedule.date} " +
                "${schedule.time} which you have ${if (type == "Extras")
                    "joined" else "assigned"} into has been modified. Please review " +
                "and be mindful of the changes."
        val datetime = "${todayDate(false)} ${todayHour()}"
        val notify = Notifications(id, notifyTitle, message, datetime, "SCHEDULE.${schedule.id}")
        for (user in joined) {
            updates["/Data/Facilitator/${user.email!!.hashSHA256()}/notifications/$id"] = notify
            updates["/Data/Facilitator/${user.email.hashSHA256()}/notified"] = true
        }
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                snackBar(view, "Schedule has been modified")
                endProgress()
                isSubmitting = false
                setupEdited()
                if (schedule.title != title) schedule.title = title
                if (schedule.room != room) schedule.room = room
                if (schedule.subject != subject) schedule.subject = subject
                if (schedule.restrict != restrict) schedule.restrict = restrict
                if (schedule.detail != detail) schedule.detail = detail
                if (schedule.need != need!!.toInt()) schedule.need = need!!.toInt()
                Global.schedule = schedule
                findNavController().popBackStack()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun setupEdited() {
        if (schedule.title != title) schedule.title = title
        if (schedule.room != room) schedule.room = room
        if (schedule.subject != subject) schedule.subject = subject
        if (schedule.restrict != restrict) schedule.restrict = restrict
        if (schedule.detail != detail) schedule.detail = detail
        if (schedule.need != need!!.toInt()) schedule.need = need!!.toInt()
    }

    private fun submitRequest() {
        isSubmitting = true
        if (!Global.isEditSchedule) {
            setupProgress("Submitting request")
            submitNewSchedule()
        } else {
            setupProgress("Submitting modifications")
            submitEditedSchedule()
        }
    }

    private fun setupNewSchedule(): Boolean {
        subject = if (course.equals("None") ||
            course.equals("Extension")) null else "$course$year"
        if (detail!!.isEmpty()) detail = null
        time = "$start - $end"
        if (!Global.isEditSchedule) {
            if (type.equals("Permanents")) {
                val prefix = when (day) {
                    "Monday" -> "1"
                    "Tuesday" -> "2"
                    "Wednesday" -> "3"
                    "Thursday" -> "4"
                    "Friday" -> "5"
                    "Saturday" -> "6"
                    else -> "7"
                }
                id = prefix + day!!.substring(0, 3).uppercase() +
                        start!!.replace(":", "") +
                        end!!.replace(":", "") +
                        room!!.replace("\\W".toRegex(), "") +
                        user.email!!.replace(".", "").substring(0, 6).uppercase()
                date = day
            } else {
                if (!checkDate(date!!)) return true
                id = date!!.substringAfterLast('-').substring(2) +
                        date!!.substringBeforeLast('-').replace("-", "") +
                        start!!.replace(":", "") +
                        end!!.replace(":", "") +
                        room!!.replace("\\W".toRegex(), "") +
                        user.email!!.replace(".", "").substring(0, 6).uppercase()
            }
        } else {
            id = schedule.id
            date = schedule.date
        }
        return false
    }

    private fun setupRequest(edited: Boolean, end: Int?) {
        if (edited && !Global.isEditSchedule) id = "$id${end!!+1}"
        val detail = setupDetails()
        confirmDialog = ConfirmDialog("SUBMIT REQUEST", "Please confirm the following " +
                "details:\n\n$detail", "Confirm", "Cancel", Global.isPINDisabled) {
            submitRequest()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun requestFaci() {
        readData()
        if (checkTitle(title!!) && checkRoom(room!!) && checkType(type) &&
            checkTime(start!!, true) && checkTime(end!!, false) &&
            checkTimeRange(start!!, end!!) && checkNeed(need!!, false))
        {
            if (setupNewSchedule()) return
            if (Global.isEditSchedule) if (schedule.title == title && schedule.room == room &&
                schedule.subject == subject && schedule.restrict == restrict &&
                schedule.detail == detail && schedule.need == need!!.toInt()) {
                snackBar(view, "No changes made")
                return
            }
            setupProgress("Checking schedule")
            checkSchedule { found, edited, end ->
                isChecking = false
                endProgress()
                if (!found || Global.isEditSchedule) setupRequest(edited, end)
                else {
                    snackBar(view, "This schedule already exists")
                    clearForm()
                }
            }
        } else {
            checkRoom(room!!)
            checkType(type)
            checkDate(date!!)
            checkTime(end!!, false)
            if (checkTime(start!!, true) && checkTime(end!!, false))
                checkTimeRange(start!!, end!!)
            checkNeed(need!!, false)
        }
    }

    private fun setupDetails(): String {
        return if (!Global.isExtension) "TITLE: \t$title" +
                "\nROOM: \t$room" +
                "\nSUBJECT: \t${if (subject.isNullOrBlank()) "None" else subject}" +
                "\nRESTRICTED: \t${if (restrict!!) "Restricted" else "Unrestricted"}" +
                "\nDETAILS: \t${if (detail != null) detail else "None"}" +
                "\n${if (type == "Extras") "DATE" else "DAY"} & TIME: \t$date $time" +
                "\nTYPE: \t${if (type == "Extras") "Extra" else "Permanent"}" +
                "\nFACILITATORS: \t$need" +
                "\n\nNOTES FOR SCHEDULE EDITING:\n\n" +
                (if (restrict!!) "You can remove restriction then change the " +
                        "subject but can not add restriction for the new " +
                        "subject" else if (course.equals("None"))
                    "You can change subject but not add restriction." else
                    "You can not add restriction but can change the subject.") +
                " You can not edit the ${if (type == "Extras") "DATE" else "DAY"} and TIME RANGE." +
                if (type == "Permanents") " Please submit PERMANENT facilitator requests only for PERIODIC schedules." else ""
        else "${if (type == "Extras") "DATE" else "DAY"} & TIME: \t$date $time" +
                "\n\nNOTE: This schedule extension will require the approval of a MANAGER or an ADMIN."
    }

    private fun readData() {
        title = binding.title.text.toString().trim()
        room = binding.room.text.toString().trim()
        course = binding.course.selectedItem.toString()
        year = binding.year.selectedItem.toString()
        restrict = binding.checkBox.isChecked
        detail = binding.detail.text.toString().trim()
        need = binding.need.text.toString().trim()
        date = binding.date.text.toString().trim()
        day = binding.dayPicker.selectedItem.toString()
        start = binding.start.text.toString().trim()
        end = binding.end.text.toString().trim()
    }

    private fun clearForm() {
        binding.title.text = null
        binding.room.text = null
        binding.course.setSelection(0)
        binding.year.setSelection(0)
        binding.checkBox.isChecked = false
        binding.checkBox.isEnabled = true
        binding.dayPicker.setSelection(0)
        binding.detail.text = null
        binding.need.text = null
        binding.date.text = null
        binding.type.clearCheck()
        binding.start.text = null
        binding.end.text = null
        title = null
        room = null
        course = null
        year = null
        subject = null
        restrict = null
        detail = null
        type = null
        date = null
        day = null
        start = null
        end = null
        need = null
    }

    private fun checkTimeRange(start: String, end: String): Boolean {
        if (start.substringAfter(':').toInt() < 60 &&
            end.substringAfter(':').toInt() < 60) {
            val timeStart = start.replace(":", "").toInt()
            val timeEnd = end.replace(":", "").toInt()
            return if (timeStart < timeEnd) {
                if (stringToTime(end) - stringToTime(start) < 30) {
                    binding.start.error = "Time range must be a minimum of 30 minutes"
                    binding.end.error = "Time range must be a minimum of 30 minutes"
                    false
                } else {
                    binding.start.error = null
                    binding.end.error = null
                    true
                }
            } else {
                binding.start.error = "Should be a valid time range"
                binding.end.error = "Should be a valid time range"
                false
            }
        }
        if (start.substringAfter(':').toInt() > 59)
            binding.start.error = "Should be a valid time"
        if (end.substringAfter(':').toInt() > 59)
            binding.end.error = "Should be a valid time"
        return false
    }

    private fun restrictListener(checked: Boolean) {
        if (checked) {
            when (binding.year.selectedItem.toString()) {
                "11", "12" -> binding.checkBox.text = "Restricted to facilitators of year 1–4 of different program"
                "1" -> binding.checkBox.text = "Restricted to facilitators of year 2–4 of different program"
                "2" -> binding.checkBox.text = "Restricted to facilitators of year 3 & 4 of different program"
                "3" -> binding.checkBox.text = "Restricted to facilitators of year 4 of different program"
            }
            if (Global.isEditSchedule) {
                binding.year.isEnabled = false
                binding.course.isEnabled = false
            }
        } else {
            if (Global.isEditSchedule) {
                binding.year.isEnabled = true
                binding.course.isEnabled = true
            }
            binding.checkBox.text = "Restrict facilitators to be of higher level of different program"
        }
    }

    private fun checkType(type: String?): Boolean {
        if (type == null) {
            snackBar(view, "Please select a schedule type")
            return false
        }
        return true
    }

    private fun subjectListener() {
        binding.course.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    binding.year.visibility = View.GONE
                    binding.checkBox.visibility = View.GONE
                    if (!Global.isEditSchedule) binding.checkBox.isChecked = false
                } else {
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
                        resources.getStringArray(if (Global.courses!![position].startsWith('0'))
                            R.array.year_tertiary else R.array.year_secondary))
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.year.adapter = adapter
                    binding.year.visibility = View.VISIBLE
                    binding.checkBox.visibility = View.VISIBLE
                    if (init) {
                        binding.year.setSelection(yearPosition!!)
                        init = false
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.year.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding.checkBox.isChecked) {
                    binding.checkBox.isEnabled = true
                    when (parent?.selectedItem.toString()) {
                        "11", "12" -> binding.checkBox.text = "Restricted to facilitators of year 1–4 of different program"
                        "1" -> binding.checkBox.text = "Restricted to facilitators of year 2–4 of different program"
                        "2" -> binding.checkBox.text = "Restricted to facilitators of year 3 & 4 of different program"
                        "3" -> binding.checkBox.text = "Restricted to facilitators of year 4 of different program"
                        else -> {
                            binding.checkBox.text = "Restrict facilitators to be of higher level of different program"
                            binding.checkBox.isChecked = false
                            binding.checkBox.isEnabled = false
                        }
                    }
                } else if (Global.isEditSchedule)
                    binding.checkBox.isEnabled = if (schedule.restrict!!)
                        binding.course.selectedItem.toString() == schedule.subject!!.removeYear() &&
                                position == yearPosition else false
                else binding.checkBox.isEnabled = position != 3
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun titleListener() {
        binding.title.setOnFocusChangeListener { _, focused ->
            val title = binding.title.text.toString().trim()
            if (!focused) checkTitle(title)
        }
    }

    private fun checkTitle(title: String): Boolean {
        if (title.isEmpty()) {
            binding.title.error = "Required"
            return false
        }
        return true
    }

    private fun roomListener() {
        binding.room.setOnFocusChangeListener { _, focused ->
            val room = binding.room.text.toString().trim()
            if (!focused) checkRoom(room)
        }
    }

    private fun checkRoom(room: String): Boolean {
        if (room.isEmpty()) {
            binding.room.error = "Required"
            return false
        }
        return true
    }

    private fun detailListener() {
        binding.detail.setOnFocusChangeListener { _, focused ->
            val detail = binding.detail.text.toString().trim()
            if (!focused) if (detail.isEmpty())
                binding.detail.error = "Optional but recommended"
        }
    }

    private fun typeListener(radio: RadioGroup, id: Int) {
        when(radio.indexOfChild(radio.findViewById(id))) {
            0 -> {
                type = "Permanents"
                binding.datePicker.visibility = View.GONE
                binding.dayPicker.visibility = View.VISIBLE
            }
            1 -> {
                type = "Extras"
                binding.dayPicker.visibility = View.GONE
                binding.datePicker.visibility = View.VISIBLE
            }
        }
    }

    private fun dateListener() {
        binding.date.setOnFocusChangeListener { _, focused ->
            val date = binding.date.text.toString().trim()
            if (!focused) checkDate(date)
        }
    }

    private fun checkDate(date: String): Boolean {
        if (date.isNotEmpty()) {
            val regex = Regex("^\\d{2}-\\d{2}-\\d{4}$")
            if (!regex.matches(date)) {
                binding.date.error = "Should follow the format MM-DD-YYYY"
                return false
            }
            return true
        } else {
            binding.date.error = "Required"
            return false
        }
    }

    private fun showDatePicker() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val datePicker = DatePickerDialog(requireContext(),
            { _: DatePicker, selectYear: Int, selectMonth: Int, selectDay: Int ->
                val padMonth = "${selectMonth+1}".padStart(2, '0')
                val padDay = "$selectDay".padStart(2, '0')
                binding.date.error = null
                binding.date.setText("$padMonth-$padDay-$selectYear")
            }, year, month, day)
        datePicker.show()
    }

    private fun startListener() {
        binding.start.setOnFocusChangeListener { _, focused ->
            val start = binding.start.text.toString().trim()
            if (!focused) checkTime(start, true)
        }
    }

    private fun endListener() {
        binding.end.setOnFocusChangeListener { _, focused ->
            val end = binding.end.text.toString().trim()
            if (!focused) checkTime(end, false)
        }
    }

    private fun checkTime(time: String, isStart: Boolean): Boolean {
        if (time.isNotEmpty()) {
            val regex = Regex("^\\d{2}:\\d{2}$")
            if (!regex.matches(time)) {
                if (isStart) binding.start.error = "Should follow the format HH:MM"
                else binding.end.error = "Should follow the format HH:MM"
                return false
            }
            if (isStart) binding.start.error = null
            else binding.end.error = null
            return true
        }
        if (isStart) binding.start.error = "Required"
        else binding.end.error = "Required"
        return false
    }

    private fun showTimePicker(isStart: Boolean) {
        showTimePicker(requireContext()) { time ->
            if (isStart) {
                binding.start.error = null
                binding.start.setText(time)
            } else {
                binding.end.error = null
                binding.end.setText(time)
            }
        }
    }

    private fun needListener() {
        binding.need.setOnFocusChangeListener { _, focused ->
            val need = binding.need.text.toString().trim()
            if (!focused) {
                binding.scroller.visibility = View.GONE
                checkNeed(need, true)
            } else {
                binding.scroller.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.form.scrollTo(0, binding.form.bottom)
                }, 5)
            }
        }
    }

    private fun checkNeed(need: String, isForFocus: Boolean): Boolean {
        return if (need.isNotEmpty()) {
            if (need.toInt() > 30 && isForFocus) binding.need.error = "Requesting too " +
                    "many facilitators is not recommended"
            true
        } else if (Global.isExtension) {
            this.need = "0"
            true
        } else {
            binding.need.error = "Required"
            false
        }
    }

    private fun removeListeners() {
        removeListener(watchQuery)
        removeListener(joinedQuery)
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

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}