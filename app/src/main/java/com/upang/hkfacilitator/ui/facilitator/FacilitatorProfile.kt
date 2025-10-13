package com.upang.hkfacilitator.ui.facilitator

import android.annotation.SuppressLint
import android.net.Uri
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.storage.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.*
import com.upang.hkfacilitator.databinding.FacilitatorProfileBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.getUriBytes
import com.upang.hkfacilitator.models.Global.getZonedDateTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.showDatePicker
import com.upang.hkfacilitator.ui.popup.*
import com.upang.hkfacilitator.utils.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class FacilitatorProfile : Fragment(), ConnectionStateListener, FaciReqClickListener,
    TimeClickListener, DTRClickListener, EvaluationClickListener, FacultyClickListener {

    private lateinit var binding: FacilitatorProfileBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private var dayTimePickerDialog: DayTimePickerDialog? = null
    private var watchEventListener: ValueEventListener? = null
    private var facuEventListener: ValueEventListener? = null
    private var evalEventListener: ValueEventListener? = null
    private lateinit var evaluation: ArrayList<Evaluation>
    private var evaluationDialog: EvaluationDialog? = null
    private lateinit var requirement: ArrayList<String>
    private lateinit var filtered: ArrayList<Schedule>
    private lateinit var vacant: ArrayList<Timestamp>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var userRef: DatabaseReference
    private lateinit var btnAddVacant: ImageButton
    private lateinit var faculty: ArrayList<User>
    private lateinit var dTR: ArrayList<Schedule>
    private lateinit var dbRef: DatabaseReference
    private lateinit var rvEvaluate: RecyclerView
    private lateinit var stRef: StorageReference
    private lateinit var datePicker: ImageButton
    private var imageDialog: ImageDialog? = null
    private var resetDialog: ResetDialog? = null
    private lateinit var rvVacant: RecyclerView
    private lateinit var searchBar: SearchView
    private lateinit var rvEval: RecyclerView
    private lateinit var rvDTR: RecyclerView
    private lateinit var schedImg: ImageView
    private lateinit var rvReq: RecyclerView
    private lateinit var reqItems: TextView
    private lateinit var auth: FirebaseAuth
    private var watchQuery: Query? = null
    private lateinit var infoLayout: View
    private lateinit var account: Account
    private lateinit var dtrLayout: View
    private var schedUrl: String? = null
    private var facuQuery: Query? = null
    private var evalQuery: Query? = null
    private lateinit var userData: User
    private var upload: String = ""
    private var isUploading = false
    private var isDoneSetup = false
    private var isPaused = false
    private var uri: Uri? = null
    private val callbackFalse = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {} }
    private val getImage = registerForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) {
            isUploading = true
            uri = it
            uploadImage()
        }
    }
    private var onComplete: ((Uri) -> Unit)? = null
    private val getProof = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onComplete?.invoke(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializations()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FacilitatorProfileBinding.inflate(inflater, container, false)
        dtrLayout = LayoutInflater.from(requireContext()).inflate(R.layout.dtr_layout, binding.root, false) as View
        infoLayout = LayoutInflater.from(requireContext()).inflate(R.layout.info_layout, binding.root, false) as View
        rvEval = LayoutInflater.from(requireContext()).inflate(R.layout.rv_evaluation, binding.root, false) as RecyclerView
        rvEvaluate = LayoutInflater.from(requireContext()).inflate(R.layout.rv_evaluate, binding.root, false) as RecyclerView
        searchBar = dtrLayout.findViewById(R.id.search_Bar)
        datePicker = dtrLayout.findViewById(R.id.btn_DatePicker)
        rvDTR = dtrLayout.findViewById(R.id.rv_DTR)
        schedImg = infoLayout.findViewById(R.id.schedule_Img)
        btnAddVacant = infoLayout.findViewById(R.id.btn_AddVacant)
        btnAddVacant.visibility = View.VISIBLE
        rvVacant = infoLayout.findViewById(R.id.rv_Vacant)
        reqItems = infoLayout.findViewById(R.id.req_Items)
        rvReq = infoLayout.findViewById(R.id.rv_Requirements)
        binding.vpViews.adapter = TabAdapter(listOf(dtrLayout, infoLayout, rvEvaluate, rvEval))
        binding.vpViews.isUserInputEnabled = false
        binding.vpViews.offscreenPageLimit = 3
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

        rvEvaluate.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt()
        )

        if (Global.offset == null) getServerTime()
        setupData()
        setupTabs()

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnPassword.setOnDebouncedClickListener {
            changePassword()
        }

        binding.userImage.setOnDebouncedClickListener {
            setProfile()
        }

        schedImg.setOnDebouncedClickListener {
            schedule()
        }

        btnAddVacant.setOnDebouncedClickListener {
            addVacant()
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
        Global.proofUri = mutableMapOf()
        Global.appeals = mutableMapOf()
        Global.homeTab = null
        if (dayTimePickerDialog != null) if (dayTimePickerDialog!!.isAdded) dayTimePickerDialog!!.dismiss()
        if (evaluationDialog != null) if (evaluationDialog!!.isAdded) evaluationDialog!!.dismiss()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (imageDialog != null) if (imageDialog!!.isAdded) imageDialog!!.dismiss()
        if (resetDialog != null) if (resetDialog!!.isAdded) resetDialog!!.dismiss()
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            if (isPaused) {
                isPaused = false
                if (Global.offset == null) getServerTime()
                checkTimeChange()
                if (!isUploading) endProgress()
                if (!isDoneSetup) setupData()
                else {
                    addListener(watchQuery, watchEventListener)
                    addListener(facuQuery, facuEventListener)
                    addListener(evalQuery, evalEventListener)
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

    override fun onManagerClick(user: User) {}

    override fun onReqClick(url: String) {
        imageDisplayDialog(url, "Requirement")
    }

    override fun onAddReqClick() {
        confirmDialog = ConfirmDialog("UPLOAD REQUIREMENT", "The images you upload " +
                "here should ONLY consist of the required images for your scholarship renewal. The " +
                "requirements may include proof/s of:\n* HK Discount\n* Community Engagements " +
                "(Lamparaan, Foundation Day, Seminars, Institutional Activities, College Night)\n* " +
                "HK Assembly Attendance\n* Other Online Engagements\nRefrain from uploading images " +
                "that does not satisfy the requirement and be mindful of the UPLOAD LIMIT.\n\nNOTE: " +
                "Images you upload here CAN NOT BE DELETED by you.", "Understood",
            "Cancel", Global.isPINDisabled) {
            upload = "REQUIREMENT"
            getImage.launch("image/*")
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    override fun onTimeDeleteClick(id: String) {
        confirmDialog = ConfirmDialog("DELETE VACANT", "Please confirm deleting this " +
                "vacant", "Delete", "Cancel", Global.isPINDisabled) {
            deleteVacant(id)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    override fun onBtnClick(sched: Schedule, uri: Uri?) {
        setupProgress("Submitting appeal")
        if (uri != null) {
            uri.let { proofUri ->
                stRef.child("/Proofs/${userData.email!!.hashSHA256()}/${sched.id}").putFile(proofUri)
                    .addOnSuccessListener { task ->
                        task.metadata!!.reference!!.downloadUrl
                            .addOnSuccessListener { url ->
                                userRef.updateChildren(hashMapOf<String, Any>(
                                    "/dtr/${sched.id}/proof" to url.toString(),
                                    "/dtr/${sched.id}/appeal" to sched.appeal!!,
                                    "/appeals" to if (userData.appeals != null) userData.appeals!! + 1 else 1))
                                    .addOnSuccessListener {
                                        Global.userData!!.appeals = userData.appeals?.let { it + 1 } ?: 1
                                        userData.appeals = userData.appeals?.let { it + 1 } ?: 1
                                        setupDTR {
                                            snackBar(view, "Appeal has been submitted")
                                            endProgress()
                                        }
                                    }
                                    .addOnFailureListener {
                                        snackBar(view, "Error: ${it.message}")
                                        endProgress()
                                    }
                            }
                    }
                    .addOnFailureListener {
                        snackBar(view, "Error: ${it.message}")
                        endProgress()
                    }
            }
        } else userRef.updateChildren(hashMapOf<String, Any>(
            "/dtr/${sched.id}/appeal" to sched.appeal!!,
            "/appeals" to if (userData.appeals != null) userData.appeals!! + 1 else 1))
            .addOnSuccessListener {
                Global.userData!!.appeals = userData.appeals?.let { it + 1 } ?: 1
                userData.appeals = userData.appeals?.let { it + 1 } ?: 1
                setupDTR {
                    snackBar(view, "Appeal has been submitted")
                    endProgress()
                }
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    override fun onAddProofClick(onComplete: (uri: Uri) -> Unit) {
        this.onComplete = onComplete
        getProof.launch("image/*")
    }

    override fun onViewProofClick(url: String) {
        imageDisplayDialog(url, "Proof")
    }

    override fun onEvalEditClick(eval: Evaluation) {
        val currentMonth = getZonedDateTime().monthValue
        val month = eval.date!!.substringAfter("-").substringBefore("-").toInt()
        if (month != currentMonth) {
            confirmDialog = ConfirmDialog("EDIT EVALUATION", "Please confirm editing this " +
                    "evaluation", "Edit", "Cancel", Global.isPINDisabled) {
                showEvaluation(null, eval, true)
            }
            confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
        } else snackBar(view, "Monthly edit limit has been reached")
    }

    override fun onEvalDeleteClick(id: String) {
        confirmDialog = ConfirmDialog("DELETE EVALUATION", "Are you sure to delete " +
                "this evaluation?", "Delete", "Cancel", Global.isPINDisabled) {
            userRef.child("/evaluation/$id").removeValue()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    override fun onFacultyClick(user: User) {
        confirmDialog = ConfirmDialog("EVALUATION", "Are you sure you want to " +
                "evaluate ${user.name}?", "Evaluate", "Cancel", Global.isPINDisabled) {
            showEvaluation(user, null, false)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun initializations() {
        userData = Global.userData!!
        account = Global.account!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        stRef = FirebaseStorage.getInstance(Global.firebase!!).reference
        auth = FirebaseAuth.getInstance(Global.firebase!!)
        userRef = dbRef.child("/Data/Facilitator/${userData.email!!.hashSHA256()}")
        vacant = arrayListOf()
        filtered = arrayListOf()
        evaluation = arrayListOf()
        dTR = arrayListOf()
        faculty = arrayListOf()
        requirement = arrayListOf()
        listenerMap = mutableMapOf()
    }

    private fun setupData() {
        setupProgress("Loading data")
        setupProfile()
        watchFaci {
            setupHour {
                setupCounter {
                    setupHistory {
                        setupDTR {
                            fetchSched {
                                fetchVacant {
                                    fetchRequirement {
                                        fetchFaculty {
                                            fetchEval {
                                                isDoneSetup = true
                                                if (!isUploading) endProgress()
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
    }

    private fun setupData(onComplete: () -> Unit) {
        dbRef.child("/Data/Faculty").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) for (facu in snapshot.children) {
                        val data = facu.getValue(User::class.java)
                        val index = faculty.indexOfFirst { it.email == data!!.email && data.profileUrl != null }
                        if (index != -1) faculty[index].profileUrl = data!!.profileUrl
                    }
                    rvEvaluate.adapter = EvaluateAdapter(faculty, this@FacilitatorProfile)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchFaculty(onComplete: () -> Unit) {
        facuQuery = userRef.child("/faculty")
        facuEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    faculty.clear()
                    if (snapshot.exists()) for (user in snapshot.children)
                        faculty.add(user.getValue(User::class.java)!!)
                    setupData {
                        onComplete()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            }
        addListener(facuQuery, facuEventListener)
    }

    private fun setupHistory(onComplete: () -> Unit) {
        userRef.child("/history").addListenerForSingleValueEvent(
            object : ValueEventListener {
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
            })
    }

    private fun fetchEval(onComplete: () -> Unit) {
        evalQuery = userRef.child("/evaluation")
        evalEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    evaluation.clear()
                    if (snapshot.exists()) for (eval in snapshot.children)
                        evaluation.add(eval.getValue(Evaluation::class.java)!!)
                    evaluation.sortWith(compareByDescending { it.date })
                    rvEval.adapter = EvaluationAdapter(evaluation, this@FacilitatorProfile, false)
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
        userRef.child("/render").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) userData.render = snapshot.getValue(Float::class.java)
                    binding.hours.text = "${"%.1f".format(userData.hrs)}/${"%.1f".format(userData.render)} HRS"
                    binding.percent.text = "${"%.1f".format(userData.hrs!! / userData.render!! * 100)} %"
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                }
            })
        userRef.child("/hrs").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) userData.hrs = snapshot.getValue(Float::class.java)
                    binding.hours.text = "${"%.1f".format(userData.hrs)}/${"%.1f".format(userData.render)} HRS"
                    binding.percent.text = "${"%.1f".format(userData.hrs!! / userData.render!! * 100)} %"
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun setupCounter(onComplete: () -> Unit) {
        userRef.child("/counter").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    binding.counter.removeAllViews()
                    if (snapshot.exists()) {
                        userData.counter = snapshot.value.toString().toInt()
                        for (i in 1..3) {
                            val view = View(requireContext())
                            val layoutParams = LinearLayout.LayoutParams(75, 15)
                            layoutParams.marginStart = 10
                            layoutParams.marginEnd = 10
                            view.layoutParams = layoutParams
                            if (i <= userData.counter!!) {
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
            })
    }

    private fun fetchVacant(onComplete: () -> Unit) {
        userRef.child("/vacant").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    vacant.clear()
                    if (snapshot.exists()) for (time in snapshot.children)
                        vacant.add(time.getValue(Timestamp::class.java)!!)
                    rvVacant.adapter = VacantAdapter(vacant, this@FacilitatorProfile, false)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchRequirement(onComplete: () -> Unit) {
        userRef.child("/reqUrl").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    requirement.clear()
                    if (snapshot.exists()) for (url in snapshot.children)
                        requirement.add(url.getValue(String::class.java)!!)
                    requirement.size.let {
                        reqItems.text = "$it/12"
                        if (it < 12) requirement.add("")
                    }
                    rvReq.adapter = FaciReqAdapter(requirement, this@FacilitatorProfile)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchSched(onComplete: () -> Unit) {
        userRef.child("/schedUrl").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        schedUrl = snapshot.getValue(String::class.java)
                        Picasso.get().load(schedUrl)
                            .placeholder(R.drawable.empty)
                            .into(schedImg)
                    } else schedImg.setImageResource(R.drawable.add)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun setupDTR(onComplete: () -> Unit) {
        if (userData.dtr != null) {
            dTR = ArrayList(userData.dtr!!.values)
            dTR.sortWith(compareByDescending { it.id })
            if (!Global.searching)
                rvDTR.adapter = FaciDTRAdapter(dTR, this@FacilitatorProfile, false)
            else filterDataOnSubmit(searchBar.query.toString().trim())
            onComplete()
        } else onComplete()
    }

    private fun watchFaci(onComplete: () -> Unit) {
        watchQuery = userRef.child("email")
        watchEventListener = object : ValueEventListener {
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
        addListener(watchQuery, watchEventListener)
    }

    private fun setupProfile() {
        if (userData.profileUrl == null) {
            if (userData.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(userData.profileUrl)
            .placeholder(if (userData.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.userImage)
        binding.name.text = userData.name
        binding.id.text = userData.id
        val year = userData.course!!.removeCourse()
        binding.course.text = "${Global.courses?.filter { it.contains(userData.course!!
            .removeYear()) }?.map{ it.substringAfterLast('.') }?.get(0)} $year"
        binding.hk.text = userData.hk
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

    private fun changePassword() {
        resetDialog = ResetDialog(userData.email!!, auth, dbRef, "PASSWORD", false) {
            snackBar(view, "Password has been changed")
        }
        resetDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun showEvaluation(user: User?, eval: Evaluation?, isEdit: Boolean) {
        evaluationDialog = EvaluationDialog(user, userData, "Facilitator", eval, isEdit) {
            if (isEdit) snackBar(view, "Evaluation has been modified")
            else snackBar(view, "Evaluation has been submitted")
        }
        evaluationDialog!!.show(childFragmentManager, "EvaluationDialog")
    }

    private fun deleteVacant(id: String) {
        setupProgress("Deleting vacant")
        userRef.child("/vacant/$id").removeValue()
            .addOnSuccessListener {
                fetchVacant {
                    snackBar(view, "Vacant has been deleted")
                    endProgress()
                }
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun showDayTimePicker() {
        dayTimePickerDialog = DayTimePickerDialog { timestamp ->
            if (!vacant.contains(timestamp)) {
                setupProgress("Adding new vacant")
                userRef.child("/vacant/${timestamp.id}").setValue(timestamp)
                    .addOnSuccessListener {
                        fetchVacant {
                            snackBar(view, "Added a new vacant")
                            endProgress()
                        }
                    }
                    .addOnFailureListener {
                        snackBar(view, "Error: ${it.message}")
                        endProgress()
                    }
            } else snackBar(view, "Vacant already exist")
        }
        dayTimePickerDialog!!.show(childFragmentManager, "DayTimePickerDialog")
    }

    private fun addVacant() {
        confirmDialog = ConfirmDialog("ADD VACANT", "Please confirm adding new vacant",
            "Add", "Cancel", Global.isPINDisabled) {
            showDayTimePicker()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun selectSchedule() {
        val currentDay = getZonedDateTime().dayOfYear
        if (userData.lastSchedule == null || userData.lastSchedule != currentDay) {
            confirmDialog = ConfirmDialog("UPLOAD SCHEDULE", "The image you upload here " +
                    "must contain your complete and OFFICIAL class schedule with DATE AND TIME. This " +
                    "includes an image of your ORF, SIS Schedule, or any image that satisfy the " +
                    "requirement.", "Select", "Cancel", Global.isPINDisabled) {
                upload = "SCHEDULE"
                getImage.launch("image/*")
            }
            confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
        } else snackBar(view, "Schedule upload limit for today has been reached")
    }

    private fun schedule() {
        if (schedUrl.isNullOrBlank()) selectSchedule()
        else imageDisplayDialog(schedUrl!!, "Schedule")
    }

    private fun imageDisplayDialog(url: String, title: String) {
        imageDialog = if (title == "Schedule") ImageDialog(title, url, false) { selectSchedule() }
        else ImageDialog(title, url, false, null)
        imageDialog!!.show(childFragmentManager, "ImageDialog")
    }

    private fun setProfile() {
        confirmDialog = ConfirmDialog("UPLOAD PROFILE", "Please confirm profile " +
                "upload", "Select", "Cancel", Global.isPINDisabled) {
            upload()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun upload() {
        upload = "PROFILE"
        val currentMonth = getZonedDateTime().monthValue
        if (userData.lastProfile != null && userData.lastProfile!! == currentMonth) {
            snackBar(view, "Profile upload limit for this month has been reached")
        } else getImage.launch("image/*")
    }

    private fun uploadImage() {
        when (upload) {
            "PROFILE" -> uploadProfile()
            "SCHEDULE" -> uploadSchedule()
            "REQUIREMENT" -> uploadRequirement()
        }
    }

    private fun uploadRequirement() {
        setupProgress("Uploading requirement")
        val bytes = getUriBytes(requireContext(), uri!!, 1_000_000)
        bytes.let { byte ->
            stRef.child("/Requirements/${userData.email!!.hashSHA256()}/${requirement.size}").putBytes(byte)
                .addOnSuccessListener { task ->
                    task.metadata!!.reference!!.downloadUrl
                        .addOnSuccessListener { url ->
                            requirement.remove("")
                            if (!requirement.contains(url.toString())) requirement.add(url.toString())
                            userRef.child("/reqUrl")
                                .setValue(requirement)
                                .addOnSuccessListener {
                                    fetchRequirement {
                                        snackBar(view, "Requirement has been uploaded")
                                        isUploading = false
                                        endProgress()
                                    }
                                }
                                .addOnFailureListener {
                                    snackBar(view, "Error: ${it.message}")
                                    isUploading = false
                                    endProgress()
                                }
                        }
                }
                .addOnFailureListener {
                    snackBar(view, "Error: ${it.message}")
                    isUploading = false
                    endProgress()
                }
        }
    }

    private fun uploadSchedule() {
        setupProgress("Uploading schedule")
        val bytes = getUriBytes(requireContext(), uri!!, 500_000)
        bytes.let { byte ->
            stRef.child("/Schedules/${userData.email!!.hashSHA256()}").putBytes(byte)
                .addOnSuccessListener { task ->
                    task.metadata!!.reference!!.downloadUrl
                        .addOnSuccessListener { url ->
                            val day = getZonedDateTime().dayOfYear
                            userRef.updateChildren(hashMapOf<String, Any>(
                                "/schedUrl" to url.toString(),
                                "/lastSchedule" to day))
                                .addOnSuccessListener {
                                    schedImg.setImageURI(uri)
                                    userData.lastSchedule = day
                                    Global.userData = userData
                                    isUploading = false
                                    endProgress()
                                }
                                .addOnFailureListener {
                                    snackBar(view, "Error: ${it.message}")
                                    isUploading = false
                                    endProgress()
                                }
                        }
                }
                .addOnFailureListener {
                    snackBar(view, "Error: ${it.message}")
                    isUploading = false
                    endProgress()
                }
        }
    }

    private fun uploadProfile() {
        setupProgress("Uploading profile")
        val bytes = getUriBytes(requireContext(), uri!!, 100_000)
        bytes.let { byte ->
            stRef.child("/Profiles/${userData.email!!.hashSHA256()}").putBytes(byte)
                .addOnSuccessListener { task ->
                    task.metadata!!.reference!!.downloadUrl
                        .addOnSuccessListener { url ->
                            val month = getZonedDateTime().monthValue
                            userRef.updateChildren(hashMapOf<String, Any>(
                                    "/profileUrl" to url.toString(),
                                    "/lastProfile" to month))
                                .addOnSuccessListener {
                                    binding.userImage.setImageURI(uri)
                                    userData.lastProfile = month
                                    userData.profileUrl = url.toString()
                                    Global.userData = userData
                                    isUploading = false
                                    endProgress()
                                }
                                .addOnFailureListener {
                                    snackBar(view, "Error: ${it.message}")
                                    isUploading = false
                                    endProgress()
                                }
                        }
                }
                .addOnFailureListener {
                    snackBar(view, "Error: ${it.message}")
                    isUploading = false
                    endProgress()
                }
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
            rvDTR.adapter = FaciDTRAdapter(dTR, this, true)
        }
    }

    private fun filterDataOnSubmit(query: String?) {
        filtered.clear()
        if (!query.isNullOrBlank()) {
            Global.searching = true
            val regex = Regex("^\\d{2}-\\d{2}-\\d{4}$")
            if (regex.matches(query)) filtered.addAll(dTR.filter { item ->
                item.date!!.contains(query, true)
            })
            else filtered.addAll(dTR.filter { item ->
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
        if (Global.homeTab != null) binding.vpViews.post {
            binding.tab.selectTab(binding.tab.getTabAt(Global.homeTab!!))
        }

        binding.tab.getTabAt(0)!!.view.setOnLongClickListener {
            true
        }

        binding.tab.getTabAt(1)!!.view.setOnLongClickListener {
            true
        }

        binding.tab.getTabAt(2)!!.view.setOnLongClickListener {
            true
        }

        binding.tab.getTabAt(3)!!.view.setOnLongClickListener {
            true
        }
    }

    private fun removeListeners() {
        removeListener(watchQuery)
        removeListener(facuQuery)
        removeListener(evalQuery)
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