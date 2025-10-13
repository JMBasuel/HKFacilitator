package com.upang.hkfacilitator.ui.admin

import android.annotation.SuppressLint
import android.net.Uri
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.*
import com.google.firebase.storage.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.*
import com.upang.hkfacilitator.databinding.AdminFaciProfileBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.emailToKey
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.showDatePicker
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.ui.popup.*
import com.upang.hkfacilitator.utils.*
import java.util.Calendar
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class AdminFaciProfile : Fragment(), ConnectionStateListener, FaciReqClickListener,
    TimeClickListener, DTRClickListener, EvaluationClickListener {

    private lateinit var binding: AdminFaciProfileBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private var schedEventListener: ValueEventListener? = null
    private var evalEventListener: ValueEventListener? = null
    private var faciEventListener: ValueEventListener? = null
    private var hisEventListener: ValueEventListener? = null
    private var dtrEventListener: ValueEventListener? = null
    private var reqEventListener: ValueEventListener? = null
    private var vacEventListener: ValueEventListener? = null
    private var ctrEventListener: ValueEventListener? = null
    private var hrsEventListener: ValueEventListener? = null
    private var renEventListener: ValueEventListener? = null
    private var editCourseDialog: EditCourseDialog? = null
    private lateinit var faciVacant: ArrayList<Timestamp>
    private lateinit var faciEval: ArrayList<Evaluation>
    private lateinit var filtered: ArrayList<Schedule>
    private lateinit var faciDTR: ArrayList<Schedule>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var faciReq: ArrayList<String>
    private lateinit var faciRef: DatabaseReference
    private var assignDialog: AssignDialog? = null
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private lateinit var datePicker: ImageButton
    private var imageDialog: ImageDialog? = null
    private lateinit var rvVacant: RecyclerView
    private lateinit var searchBar: SearchView
    private lateinit var rvEval: RecyclerView
    private lateinit var rvDTR: RecyclerView
    private lateinit var schedImg: ImageView
    private lateinit var rvReq: RecyclerView
    private lateinit var account: Account
    private var schedQuery: Query? = null
    private lateinit var infoLayout: View
    private var evalQuery: Query? = null
    private var faciQuery: Query? = null
    private lateinit var dtrLayout: View
    private var schedUrl: String? = null
    private var hisQuery: Query? = null
    private var renQuery: Query? = null
    private var hrsQuery: Query? = null
    private var ctrQuery: Query? = null
    private var vacQuery: Query? = null
    private var reqQuery: Query? = null
    private var dtrQuery: Query? = null
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
        binding = AdminFaciProfileBinding.inflate(inflater, container, false)
        dtrLayout = LayoutInflater.from(requireContext()).inflate(R.layout.dtr_layout, binding.root, false) as View
        searchBar = dtrLayout.findViewById(R.id.search_Bar)
        datePicker = dtrLayout.findViewById(R.id.btn_DatePicker)
        rvDTR = dtrLayout.findViewById(R.id.rv_DTR)
        infoLayout = LayoutInflater.from(requireContext()).inflate(R.layout.info_layout, binding.root, false) as View
        rvEval = LayoutInflater.from(requireContext()).inflate(R.layout.rv_evaluation, binding.root, false) as RecyclerView
        schedImg = infoLayout.findViewById(R.id.schedule_Img)
        rvVacant = infoLayout.findViewById(R.id.rv_Vacant)
        rvReq = infoLayout.findViewById(R.id.rv_Requirements)
        binding.vpViews.adapter = TabAdapter(listOf(dtrLayout, infoLayout, rvEval))
        binding.vpViews.isUserInputEnabled = false
        binding.vpViews.offscreenPageLimit = 2
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

        rvDTR.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvEval.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvVacant.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvReq.apply {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(activity, 4)
        }

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        if (Global.offset == null) getServerTime()
        setupData()
        setupTabs()

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnAssign.setOnDebouncedClickListener {
            assignPermanent()
        }

        binding.btnRemove.setOnDebouncedClickListener {
            terminateAccount()
        }

        binding.btnEdit.setOnDebouncedClickListener {
            editCourse()
        }

        binding.btnNotify.setOnDebouncedClickListener {
            sendNotification()
        }

        binding.btnRenew.setOnDebouncedClickListener {
            renewRecords()
        }

        schedImg.setOnDebouncedClickListener {
            imageDisplayDialog(schedUrl, "Schedule")
        }

        searchBar.setOnQueryTextListener(searchListener())

        datePicker.setOnDebouncedClickListener {
            showDatePicker(requireContext(), searchBar)
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
        if (editCourseDialog != null) if (editCourseDialog!!.isAdded) editCourseDialog!!.dismiss()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (assignDialog != null) if (assignDialog!!.isAdded) assignDialog!!.dismiss()
        if (imageDialog != null) if (imageDialog!!.isAdded) imageDialog!!.dismiss()
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            if (isPaused) {
                isPaused = false
                endProgress()
                if (Global.offset == null) getServerTime()
                checkTimeChange()
                if (!isDoneSetup) setupData()
                else {
                    addListener(faciQuery, faciEventListener)
                    addListener(dtrQuery, dtrEventListener)
                    addListener(reqQuery, reqEventListener)
                    addListener(schedQuery, schedEventListener)
                    addListener(vacQuery, vacEventListener)
                    addListener(renQuery, renEventListener)
                    addListener(ctrQuery, ctrEventListener)
                    addListener(hrsQuery, hrsEventListener)
                    addListener(evalQuery, evalEventListener)
                    addListener(hisQuery, hisEventListener)
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

    override fun onBtnClick(sched: Schedule, uri: Uri?) {
        confirmDialog = ConfirmDialog("CONFIRM", "Do you want to mark this appeal as " +
                "${if (sched.approved!!) "APPROVED" else "NOT APPROVED"}?", "Confirm", "Cancel", true) {
            processAppeal(sched)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    override fun onViewProofClick(url: String) {
        imageDisplayDialog(url, "Proof")
    }

    override fun onReqClick(url: String) {
        imageDisplayDialog(url, "Requirement")
    }

    override fun onAddReqClick() {}
    override fun onEvalEditClick(eval: Evaluation) {}
    override fun onEvalDeleteClick(id: String) {}
    override fun onTimeDeleteClick(id: String) {}
    override fun onAddProofClick(onComplete: (uri: Uri) -> Unit) {}

    private fun initializations() {
        user = Global.profiledUser!!
        account = Global.account!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        stRef = FirebaseStorage.getInstance(Global.firebase!!).reference
        faciRef = dbRef.child("/Data/Facilitator/${user.email!!.hashSHA256()}")
        faciVacant = arrayListOf()
        filtered = arrayListOf()
        faciEval = arrayListOf()
        faciDTR = arrayListOf()
        faciReq = arrayListOf()
        listenerMap = mutableMapOf()
    }

    private fun setupData() {
        setupProgress("Loading data")
        setupProfile()
        watchFaci {
            setupHour {
                setupCounter {
                    setupHistory {
                        fetchDTR {
                            fetchSched {
                                fetchVacant {
                                    fetchRequirement {
                                        fetchEval {
                                            isDoneSetup = true
                                            endProgress()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun watchFaci(onComplete: () -> Unit) {
        faciQuery = faciRef.child("email")
        faciEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() && snapshot.value == null) {
                    snackBar(view, "This account has been deleted")
                    findNavController().popBackStack()
                }
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(faciQuery, faciEventListener)
    }

    private fun setupHistory(onComplete: () -> Unit) {
        hisQuery = faciRef.child("/history")
        hisEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val history = snapshot.value as String
                    val day = history.substring(3, 5)
                    val year = history.substring(6, 10)
                    val month = when (history.substringBefore('-').toInt()) {
                        1 -> "Jan"
                        2 -> "Feb"
                        3 -> "Mar"
                        4 -> "Apr"
                        5 -> "May"
                        6 -> "Jun"
                        7 -> "Jul"
                        8 -> "Aug"
                        9 -> "Sep"
                        10 -> "Oct"
                        11 -> "Nov"
                        else -> "Dec"
                    }
                    val hours = "${history.substringAfter('.')} hrs"
                    binding.history.text = "Renewed: $month $day, $year | $hours"
                }
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(hisQuery, hisEventListener)
    }

    private fun fetchEval(onComplete: () -> Unit) {
        evalQuery = faciRef.child("/evaluation")
        evalEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                faciEval.clear()
                if (snapshot.exists()) for (eval in snapshot.children)
                    faciEval.add(eval.getValue(Evaluation::class.java)!!)
                faciEval.sortWith(compareByDescending { it.date })
                rvEval.adapter = EvaluationAdapter(faciEval, this@AdminFaciProfile, true)
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(evalQuery, evalEventListener)
    }

    private fun setupHour(onComplete: () -> Unit) {
        renQuery = faciRef.child("/render")
        renEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) user.render = snapshot.getValue(Float::class.java)
                binding.hours.text = "${"%.1f".format(user.hrs)}/${"%.1f".format(user.render)} HRS"
                binding.percent.text = "${"%.1f".format(user.hrs!! / user.render!! * 100)} %"
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
            }
        }
        addListener(renQuery, renEventListener)
        hrsQuery = faciRef.child("/hrs")
        hrsEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) user.hrs = snapshot.getValue(Float::class.java)
                binding.hours.text = "${"%.1f".format(user.hrs)}/${"%.1f".format(user.render)} HRS"
                binding.percent.text = "${"%.1f".format(user.hrs!! / user.render!! * 100)} %"
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(hrsQuery, hrsEventListener)
    }

    private fun setupCounter(onComplete: () -> Unit) {
        ctrQuery = faciRef.child("/counter")
        ctrEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.counter.removeAllViews()
                if (snapshot.exists()) {
                    user.counter = snapshot.value.toString().toInt()
                    for (i in 1..3) {
                        val view = View(context)
                        val layoutParams = LinearLayout.LayoutParams(75, 15)
                        layoutParams.marginStart = 10
                        layoutParams.marginEnd = 10
                        view.layoutParams = layoutParams
                        if (i <= user.counter!!) {
                            view.setBackgroundColor("#D9BD2D".toColorInt())
                            binding.counter.addView(view)
                        } else {
                            view.setBackgroundColor("#FFCC0000".toColorInt())
                            binding.counter.addView(view)
                        }
                    }
                }
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(ctrQuery, ctrEventListener)
    }

    private fun fetchVacant(onComplete: () -> Unit) {
        vacQuery = faciRef.child("/vacant")
        vacEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                faciVacant.clear()
                if (snapshot.exists()) for (time in snapshot.children)
                    faciVacant.add(time.getValue(Timestamp::class.java)!!)
                rvVacant.adapter = VacantAdapter(faciVacant, this@AdminFaciProfile, true)
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(vacQuery, vacEventListener)
    }

    private fun fetchRequirement(onComplete: () -> Unit) {
        reqQuery = faciRef.child("/reqUrl")
        reqEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                faciReq.clear()
                if (snapshot.exists()) for (url in snapshot.children)
                    faciReq.add(url.value as String)
                rvReq.adapter = FaciReqAdapter(faciReq, this@AdminFaciProfile)
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(reqQuery, reqEventListener)
    }

    private fun fetchSched(onComplete: () -> Unit) {
        schedQuery = faciRef.child("/schedUrl")
        schedEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    schedUrl = snapshot.getValue(String::class.java)
                    Picasso.get()
                        .load(schedUrl)
                        .placeholder(R.drawable.empty)
                        .into(schedImg)
                }
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(schedQuery, schedEventListener)
    }

    private fun fetchDTR(onComplete: () -> Unit) {
        dtrQuery = faciRef.child("/dtr")
        dtrEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                faciDTR.clear()
                if (snapshot.exists()) for (sched in snapshot.children)
                    faciDTR.add(sched.getValue(Schedule::class.java)!!)
                faciDTR.sortWith(compareByDescending { it.id })
                if (!Global.searching)
                    rvDTR.adapter = FaciDTRAdapter(faciDTR, this@AdminFaciProfile, true)
                else filterDataOnSubmit(searchBar.query.toString().trim())
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        addListener(dtrQuery, dtrEventListener)
    }

    private fun processAppeal(sched: Schedule) {
        setupProgress("Processing")
        faciRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.child("/dtr/${sched.id}/approved").value != null)
                    return Transaction.abort()
                val counter = currentData.child("/counter").getValue(Int::class.java)!!
                val render = currentData.child("/render").getValue(Float::class.java)!!
                val appeals = currentData.child("/appeals").getValue(Int::class.java)!!
                if (sched.approved!!) {
                    if (counter + 1 > 3) {
                        currentData.child("/render").value = render - 10F
                        currentData.child("/counter").value = 1
                    } else currentData.child("/counter").value = counter + 1
                }
                currentData.child("/appeals").value = if (appeals-1 == 0) null
                else appeals - 1
                currentData.child("/dtr/${sched.id}/approved").value = sched.approved
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?,
                committed: Boolean, currentData: DataSnapshot?
            ) {
                endProgress()
                if (error != null) snackBar(view, "Error: ${error.message}")
            }
        })
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
        binding.id.text = user.id
        val year = user.course!!.removeCourse()
        binding.course.text = "${Global.courses?.filter { it.contains(user.course!!
                .removeYear()) }?.map{ it.substringAfterLast('.') }?.get(0)
        } $year"
        binding.hk.text = user.hk
    }

    private fun resetData(onComplete: () -> Unit) {
        if (Global.searching) {
            Global.searching = false
            searchBar.setQuery(null, false)
            Handler(Looper.getMainLooper()).postDelayed({
                onComplete()
            }, 50)
        } else onComplete()
    }

    private fun showEditCourseDialog() {
        editCourseDialog = EditCourseDialog(user, "Facilitator") {
            user = Global.profiledUser!!
            val year = user.course!!.removeCourse()
            binding.course.text = "${Global.courses?.filter { it.contains(user.course!!
                .removeYear()) }?.map{ it.substringAfterLast('.') }?.get(0)} $year"
        }
        editCourseDialog!!.show(childFragmentManager, "EditCourseDialog")
    }

    private fun editCourse() {
        confirmDialog = ConfirmDialog("EDIT COURSE", "Do you want to edit this user's course?",
            "Proceed", "Cancel", Global.isPINDisabled) {
            showEditCourseDialog()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun sendNotification() {
        confirmDialog = ConfirmDialog("NOTIFY INCOMPLETE", "Please confirm notifying this " +
                "facilitator about incomplete requirements.", "Notify", "Cancel", Global.isPINDisabled) {
            notification()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun notification() {
        setupProgress("Sending notification")
        val today = todayDate(true)
        faciRef.child("notifications").runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val notification = currentData.child("/${today.substring(2)
                    .replace("-", "")}REQUIREMENT").getValue(Notifications::class.java)
                if (notification != null) return Transaction.abort()
                val id = "${today.substring(2).replace("-", "")}REQUIREMENT"
                val title = "Incomplete requirements"
                val message = "Please fulfill all the requirements for your scholarship renewal to avoid termination."
                val datetime = "${todayDate(false)} ${todayHour()}"
                val notify = Notifications(id, title, message, datetime, "HOMEREQ")
                currentData.child("/$id").value = notify
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?,
                committed: Boolean, currentData: DataSnapshot?
            ) {
                endProgress()
                if (error != null) snackBar(view, "Error: ${error.message}")
                else if (committed) {
                    faciRef.child("notified").setValue(true)
                        .addOnSuccessListener {
                            snackBar(view, "Notification sent")
                        }
                } else snackBar(view, "This facilitator has already been notified today")
            }
        })
    }

    private fun renewRecords() {
        confirmDialog = ConfirmDialog("CONFIRM", "Are you sure you want to renew this " +
                "account?${if (user.hrs!! < user.render!!) " This facilitator did not reach their " +
                        "required hours to render." else ""}", "Renew", "Cancel", Global.isPINDisabled) {
            renew()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun renew() {
        setupProgress("Processing")
        faciRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val hour = currentData.child("/hrs").getValue(Float::class.java)
                    ?: return Transaction.success(currentData)
                val render = currentData.child("/render").getValue(Float::class.java)!!
                val last = currentData.child("/history").getValue(String::class.java) ?: ""
                if (last.isNotEmpty()) {
                    val lastMonth = last.substringBefore('-').toInt()
                    val calendar = Calendar.getInstance()
                    var now = calendar.get(Calendar.MONTH)+1
                    if (lastMonth > now) now += 12
                    if (now-lastMonth < 1) return Transaction.abort()
                }
                currentData.child("/hrs").value = hour-render
                currentData.child("/render").value = when (user.hk) {
                    "HK25" -> 45F
                    "HK50" -> 90F
                    "HK75" -> 120F
                    else -> 150F
                }
                currentData.child("/counter").value = 3
                currentData.child("/history").value = "${todayDate(false)}.$hour"
                currentData.child("/dtr").value = null
                currentData.child("/reqUrl").value = null
                currentData.child("/schedUrl").value = null
                currentData.child("/vacant").value = null
                currentData.child("/notifications").value = null
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?,
                committed: Boolean, currentData: DataSnapshot?
            ) {
                endProgress()
                if (error != null) snackBar(view, "Error: ${error.message}")
                else if (committed) {
                    stRef.child("/Schedules/${user.email!!.hashSHA256()}").delete()
                    stRef.child("/Requirements/${user.email!!.hashSHA256()}").delete()
                    stRef.child("/Proofs/${user.email!!.hashSHA256()}").delete()
                } else snackBar(view, "This record has already been renewed this semester")
            }
        })
    }

    private fun terminateAccount() {
        confirmDialog = ConfirmDialog("CONFIRM", "Please confirm account deletion",
            "Confirm", "Cancel", Global.isPINDisabled) {
            terminate()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun terminate() {
        setupProgress("Deleting account")
        val updates = hashMapOf<String, Any?>(
            "/Accounts/${emailToKey(user.email!!)}" to null,
            "/Data/Facilitator/${user.email!!.hashSHA256()}" to null)
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                stRef.child("/Profiles/${user.email!!.hashSHA256()}").delete()
                stRef.child("/Schedules/${user.email!!.hashSHA256()}").delete()
                stRef.child("/Requirements/${user.email!!.hashSHA256()}").delete()
                stRef.child("/Proofs/${user.email!!.hashSHA256()}").delete()
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun assignPermanent() {
        assignDialog = AssignDialog(user, faciVacant.ifEmpty { null }, dbRef)
        assignDialog!!.show(childFragmentManager, "AssignDialog")
    }

    private fun imageDisplayDialog(url: String?, title: String) {
        if (!url.isNullOrBlank()) {
            imageDialog = ImageDialog(title, url, true, null)
            imageDialog!!.show(childFragmentManager, "ImageDialog")
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
                resetDataOnClear(query)
                return true
            }
        }
    }

    private fun resetDataOnClear(query: String?) {
        if (query.isNullOrBlank()) {
            Global.searching = false
            rvDTR.adapter = FaciDTRAdapter(faciDTR, this, true)
        }
    }

    private fun filterDataOnSubmit(query: String?) {
        filtered.clear()
        if (!query.isNullOrBlank()) {
            Global.searching = true
            val regex = Regex("^\\d{2}-\\d{2}-\\d{4}$")
            if (regex.matches(query)) filtered.addAll(faciDTR.filter { item ->
                item.date!!.contains(query, true)
            })
            else filtered.addAll(faciDTR.filter { item ->
                item.remark!!.contains(query, true) ||
                        item.owner!!.contains(query, true) ||
                        item.title!!.contains(query, true)
            })
            rvDTR.adapter = FaciDTRAdapter(filtered, this, true)
        }
    }

    private fun setupTabs() {
        binding.tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                clear()
                resetData {
                    binding.vpViews.currentItem = tab!!.position
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.tab.getTabAt(0)!!.view.setOnLongClickListener {
            true
        }

        binding.tab.getTabAt(1)!!.view.setOnLongClickListener {
            true
        }

        binding.tab.getTabAt(2)!!.view.setOnLongClickListener {
            true
        }
    }

    private fun removeListeners() {
        removeListener(faciQuery)
        removeListener(dtrQuery)
        removeListener(reqQuery)
        removeListener(schedQuery)
        removeListener(vacQuery)
        removeListener(ctrQuery)
        removeListener(hrsQuery)
        removeListener(renQuery)
        removeListener(evalQuery)
        removeListener(hisQuery)
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