package com.upang.hkfacilitator.ui.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.*
import android.transition.*
import android.view.*
import android.widget.SearchView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.storage.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.*
import com.upang.hkfacilitator.databinding.AdminBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.createNotificationChannel
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.getUriBytes
import com.upang.hkfacilitator.models.Global.getZonedDateTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isActive
import com.upang.hkfacilitator.models.Global.isAlarmDisabled
import com.upang.hkfacilitator.models.Global.isBatteryOptimized
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.isNotificationDisabled
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.startNotificationWorker
import com.upang.hkfacilitator.models.Global.startSyncWorker
import com.upang.hkfacilitator.models.Global.todayHour
import com.upang.hkfacilitator.ui.popup.*
import com.upang.hkfacilitator.utils.*
import com.upang.hkfacilitator.viewholders.NotificationViewHolder
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.util.concurrent.*
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.net.toUri

@SuppressLint("ClickableViewAccessibility, SetTextI18n, BatteryLife")
class Admin : Fragment(), ConnectionStateListener, FacilitatorClickListener,
    FacultyClickListener, NotificationClickListener {

    private lateinit var binding: AdminBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var notifications: ArrayList<Notifications>
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var facilitators: ArrayList<User>
    private lateinit var rvFacilitators: RecyclerView
    private var coursesDialog: CoursesDialog? = null
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var courses: ArrayList<String>
    private lateinit var userRef: DatabaseReference
    private lateinit var managers: ArrayList<User>
    private lateinit var filtered: ArrayList<User>
    private lateinit var rvManagers: RecyclerView
    private lateinit var dbRef: DatabaseReference
    private lateinit var faculty: ArrayList<User>
    private lateinit var stRef: StorageReference
    private lateinit var rvFaculty: RecyclerView
    private var resetDialog: ResetDialog? = null
    private lateinit var transition: Transition
    private var userDialog: UserDialog? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var account: Account
    private lateinit var user: User
    private var isDoneSetup = false
    private var isPaused = false
    private var uri: Uri? = null
    private var press = 0
    private var prev = 0L
    private val callbackTrue = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            back()
        }
    }
    private val callbackFalse = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {}
    }
    private val getImage = registerForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) {
            uri = it
            uploadImage()
        }
    }
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission())
    { isGranted ->
        if (isGranted) {
            createNotificationChannel(requireContext())
            startNotificationWorker(requireContext())
        }
    }
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) {
            Global.account = null
            Global.userData = null
            Global.courses = null
            Global.facilitators = null
            Global.faculty = null
            Global.managers = null
            Global.tabPosition = 0
            Global.profiledUser = null
            Global.isPINDisabled = false
            Global.searching = false
            endProgress()
            sharedPreferences.edit {
                putString("Login", null)
                apply()
            }
            findNavController().popBackStack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = requireActivity().getSharedPreferences("Preferences", Context.MODE_PRIVATE)
        auth = FirebaseAuth.getInstance(Global.firebase!!)
        if (sharedPreferences.getString("Login", null) == "Admin") {
            initializations()
            startSyncWorker(requireContext())
        } else {
            snackBar(view, "Invalid credentials")
            if (auth.currentUser != null) auth.signOut()
            else findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AdminBinding.inflate(inflater, container, false)
        rvFacilitators = LayoutInflater.from(requireContext()).inflate(R.layout.rv_facilitators, binding.root, false) as RecyclerView
        rvFaculty = LayoutInflater.from(requireContext()).inflate(R.layout.rv_faculty, binding.root, false) as RecyclerView
        rvManagers = LayoutInflater.from(requireContext()).inflate(R.layout.rv_managers, binding.root, false) as RecyclerView
        binding.vpViews.adapter = TabAdapter(listOf(rvFacilitators, rvFaculty, rvManagers))
        binding.vpViews.isUserInputEnabled = false
        binding.vpViews.offscreenPageLimit = 2
        if (isBatteryOptimized(requireContext()) || isAlarmDisabled(requireContext())) {
            binding.notifications.btnPermissions.visibility = View.VISIBLE
            binding.notifications.btnPermissions.text = getString(R.string.enable_notifications_admin)
        }
        if (Global.VERSION.endsWith("DEBUG")) binding.btnDebug.visibility = View.VISIBLE
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

        rvFacilitators.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvFaculty.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvManagers.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.notifications.rvNotifications.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt()
        )

        if (Global.offset == null) getServerTime()
        fetchData()
        setupTabs()

        binding.menuOpen.setOnClickListener {
            clear()
            menuOpen()
        }

        binding.btnExport.setOnDebouncedClickListener {
            exportData()
        }

        binding.btnCourses.setOnDebouncedClickListener {
            openCourses()
        }

        binding.adminMenu.menuClose.setOnClickListener {
            menuClose()
        }

        binding.adminMenu.navBg.setOnClickListener {
            menuClose()
        }

        binding.adminMenu.userImage.setOnDebouncedClickListener {
            setImage()
        }

        binding.adminMenu.menuAccount.setOnDebouncedClickListener {
            menuClose()
            changePassword()
        }

        binding.adminMenu.menuPIN.setOnDebouncedClickListener {
            menuClose()
            changePIN()
        }

        binding.adminMenu.menuSignout.setOnDebouncedClickListener {
            menuClose()
            signOut()
        }

        binding.adminMenu.menuFeedback.setOnDebouncedClickListener {
            menuClose()
            report()
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.green, R.color.yellow)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)
        binding.swipeRefresh.viewTreeObserver.addOnGlobalLayoutListener {
            val distance = (binding.swipeRefresh.height * 0.3).toInt()
            binding.swipeRefresh.setDistanceToTriggerSync(distance)
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadData(true)
        }

        binding.searchBar.setOnQueryTextListener(searchListener())

        binding.btnNotifications.setOnDebouncedClickListener {
            notificationOpen()
            removeNotice()
        }

        binding.notifications.notificationBg.setOnClickListener {
            notificationClose()
        }

        binding.notifications.btnDelete.setOnDebouncedClickListener {
            deleteNotifications()
        }

        binding.notifications.btnPermissions.setOnDebouncedClickListener {
            permissions()
        }

        binding.notifications.notificationClose.setOnClickListener {
            notificationClose()
        }

        binding.btnAddAccount.setOnDebouncedClickListener {
            findNavController().navigate(AdminDirections.actionHomeAdminToAdminAddAcc())
        }

        binding.btnDebug.setOnDebouncedClickListener {
            findNavController().navigate(AdminDirections.actionHomeAdminToTest())
        }
    }

    override fun onStart() {
        super.onStart()
        notifications()
        alarmPermission()
        batteryOptimization()
        if (!isConnected(requireContext())) {
            isPaused = true
            setupProgress("Waiting for connection")
        }
    }

    override fun onResume() {
        super.onResume()
        binding.swipeRefresh.isRefreshing = false
        connectionStateMonitor.enable(requireContext())
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackTrue)
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
        callbackTrue.remove()
        connectionStateMonitor.disable(requireContext())
        auth.removeAuthStateListener(authListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (coursesDialog != null) if (coursesDialog!!.isAdded) coursesDialog!!.dismiss()
        if (userDialog != null) if (userDialog!!.isAdded) userDialog!!.dismiss()
        if (resetDialog != null) if (resetDialog!!.isAdded) resetDialog!!.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPaused = false
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            auth.addAuthStateListener(authListener)
            if (isPaused) {
                isPaused = false
                endProgress()
                if (Global.offset == null) getServerTime()
                checkTimeChange()
                if (!isDoneSetup) fetchData()
            }
        }
    }

    override fun onNetworkLost() {
        requireActivity().runOnUiThread {
            isPaused = true
            auth.removeAuthStateListener(authListener)
            setupProgress("Waiting for connection")
            if (isConnected(requireContext())) endProgress()
        }
    }

    override fun onFacilitatorClick(user: User) {
        Global.profiledUser = user
        findNavController().navigate(AdminDirections.actionHomeAdminToAdminFaciProfile())
    }

    override fun onFacultyClick(user: User) {
        Global.profiledUser = user
        findNavController().navigate(AdminDirections.actionHomeAdminToAdminFacuProfile())
    }

    override fun onManagerClick(user: User) {
        Global.profiledUser = user
        userDialog = UserDialog(user)
        userDialog!!.show(childFragmentManager, "UserDialog")
    }

    override fun getViewHolder(id: String): List<NotificationViewHolder> {
        return notificationAdapter.getNotificationViewHolders(id)
    }

    override fun readNotification(id: String) {
        val updates = hashMapOf<String, Any>( "/read" to true, "/notified" to true )
        userRef.child("/notifications/$id").updateChildren(updates)
    }

    override fun navigateToLink(link: String, destination: String) {
        if (destination == "SCHEDULE") fetchSchedule(if (link[1].isLetter() &&
            !link.endsWith("EXT")) "Permanents" else "Extras", link)
    }

    private fun initializations() {
        user = Global.userData!!
        account = Global.account!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        stRef = FirebaseStorage.getInstance(Global.firebase!!).reference
        userRef = dbRef.child("/Data/Admin/${user.email!!.hashSHA256()}")
        courses = arrayListOf()
        facilitators = arrayListOf()
        faculty = arrayListOf()
        managers = arrayListOf()
        filtered = arrayListOf()
        notifications = arrayListOf()
    }

    private fun openCourses() {
        coursesDialog = CoursesDialog()
        coursesDialog!!.show(childFragmentManager, "CoursesDialog")
    }

    private fun fetchData() {
        binding.tab.selectTab(binding.tab.getTabAt(Global.tabPosition))
        if (Global.tabPosition == 0) binding.btnExport.visibility = View.VISIBLE
        else binding.btnExport.visibility = View.GONE
        setupMenu()
        if (Global.courses == null || Global.facilitators == null ||
            Global.faculty == null || Global.managers == null)
            setupProgress("Loading data")
        loadData(false)
    }

    private fun setupData(onComplete: () -> Unit) {
        if (!Global.searching) {
            rvFacilitators.adapter = Global.facilitators?.let { FacilitatorAdapter(it, this) }
            rvFaculty.adapter = Global.faculty?.let { FacultyAdapter(it, this, true) }
            rvManagers.adapter = Global.managers?.let { FacultyAdapter(it, this, false) }
        } else filterDataOnSubmit(binding.searchBar.query.toString().trim())
        if (notifications.isNotEmpty()) {
            notificationAdapter = NotificationAdapter(notifications, this)
            binding.notifications.rvNotifications.adapter = notificationAdapter
            binding.notifications.btnDelete.visibility = View.VISIBLE
        }
        onComplete()
    }

    private fun loadData(isRefresh: Boolean) {
        fetchCourses {
            checkNotice {
                fetchFacilitatorData {
                    fetchFacultyData {
                        fetchManagerData {
                            fetchNotifications {
                                setupData {
                                    if (isRefresh) binding.swipeRefresh.isRefreshing = false
                                    isDoneSetup = true
                                    if (binding.loading.container.isVisible) endProgress()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fetchCourses(onComplete: () -> Unit) {
        if (auth.currentUser != null) {
            dbRef.child("/Courses").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        courses.clear()
                        if (snapshot.exists()) for (course in snapshot.children)
                            courses.add(course.value as String)
                        Global.courses = courses
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                        onComplete()
                    }
                })
        }
    }

    private fun fetchNotifications(onComplete: () -> Unit) {
        if (auth.currentUser != null) {
            userRef.child("/notifications").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        notifications.clear()
                        if (snapshot.exists()) for (notification in snapshot.children)
                            notifications.add(notification.getValue(Notifications::class.java)!!)
                        notifications.sortByDescending { it.id }
                        Global.notifications = notifications
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                        onComplete()
                    }
                })
        }
    }

    private fun checkNotice(onComplete: () -> Unit) {
        if (auth.currentUser != null) {
            userRef.child("/notified").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) binding.notice.visibility = View.VISIBLE
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                        onComplete()
                    }
                })
        }
    }

    private fun fetchManagerData(onComplete: () -> Unit) {
        if (auth.currentUser != null) {
            dbRef.child("/Data/Manager").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        managers.clear()
                        if (snapshot.exists()) for (user in snapshot.children)
                            managers.add(user.getValue(User::class.java)!!)
                        Global.managers = managers
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                        onComplete()
                    }
                })
        }
    }

    private fun fetchFacilitatorData(onComplete: () -> Unit) {
        if (auth.currentUser != null) {
            dbRef.child("/Data/Facilitator").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        facilitators.clear()
                        if (snapshot.exists()) {
                            for (user in snapshot.children)
                                facilitators.add(user.getValue(User::class.java)!!)
                        }
                        Global.facilitators = facilitators
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                        onComplete()
                    }
                })
        }
    }

    private fun fetchFacultyData(onComplete: () -> Unit) {
        if (auth.currentUser != null) {
            dbRef.child("/Data/Faculty").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        faculty.clear()
                        if (snapshot.exists()) for (user in snapshot.children)
                            faculty.add(user.getValue(User::class.java)!!)
                        Global.faculty = faculty
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                        onComplete()
                    }
                })
        }
    }

    private fun fetchSchedule(type: String, id: String) {
        setupProgress("Loading")
        dbRef.child("/$type/$id").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    endProgress()
                    if (snapshot.exists()) {
                        val schedule = snapshot.getValue(Schedule::class.java)!!
                        if (!isPast(schedule, type == "Extras") && !isActive(schedule, true)) {
                            Global.schedule = schedule
                            findNavController().navigate(AdminDirections.actionHomeAdminToAdminFacuSchedule())
                        } else snackBar(view, "Schedule does not exist")
                    } else snackBar(view, "Schedule does not exist")
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    endProgress()
                }
            })
    }

    private fun export() {
        setupProgress("Downloading data")
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Facilitator")
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Student ID")
        headerRow.createCell(1).setCellValue("Name")
        headerRow.createCell(2).setCellValue("Email")
        headerRow.createCell(3).setCellValue("Course")
        headerRow.createCell(4).setCellValue("Render")
        headerRow.createCell(5).setCellValue("Hours")
        headerRow.createCell(6).setCellValue("DTR")
        headerRow.createCell(7).setCellValue("Time")
        headerRow.createCell(8).setCellValue("In")
        headerRow.createCell(9).setCellValue("Out")
        headerRow.createCell(10).setCellValue("Remark")
        var rowIndex = 1
        facilitators.forEach { faci ->
            val row = sheet.createRow(rowIndex)
            row.createCell(0).setCellValue(faci.id)
            row.createCell(1).setCellValue(faci.name)
            row.createCell(2).setCellValue(faci.email)
            row.createCell(3).setCellValue(faci.course)
            row.createCell(4).setCellValue(faci.render!!.toDouble())
            row.createCell(5).setCellValue(faci.hrs!!.toDouble())
            if (faci.dtr != null) {
                val dtr = ArrayList(faci.dtr.values)
                dtr.sortWith(compareByDescending { it.id })
                row.createCell(6).setCellValue(dtr[0].date)
                row.createCell(7).setCellValue(dtr[0].time)
                row.createCell(8).setCellValue(dtr[0].timeIn ?: "N/A")
                row.createCell(9).setCellValue(dtr[0].timeOut ?: "N/A")
                row.createCell(10).setCellValue(dtr[0].remark)
                rowIndex++
                for (index in 1..<dtr.size) {
                    val dtrRow = sheet.createRow(rowIndex)
                    dtrRow.createCell(6).setCellValue(dtr[index].date)
                    dtrRow.createCell(7).setCellValue(dtr[index].time)
                    dtrRow.createCell(8).setCellValue(dtr[index].timeIn ?: "N/A")
                    dtrRow.createCell(9).setCellValue(dtr[index].timeOut ?: "N/A")
                    dtrRow.createCell(10).setCellValue(dtr[index].remark)
                    rowIndex++
                }
            } else {
                row.createCell(6).setCellValue("N/A")
                row.createCell(7).setCellValue("N/A")
                row.createCell(8).setCellValue("N/A")
                row.createCell(9).setCellValue("N/A")
                row.createCell(10).setCellValue("N/A")
                rowIndex++
            }
            rowIndex++
        }
        val resolver = requireContext().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Facilitators_${todayHour().replace(":", "")}.xlsx")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            val outputStream = resolver.openOutputStream(it)
            outputStream?.use { os ->
                workbook.write(os)
                os.flush()
            }
            workbook.close()
            snackBar(view, "Data has been exported")
            Handler(Looper.getMainLooper()).postDelayed({
                endProgress()
            }, 1)
        } ?: {
            snackBar(view, "Failed to export data")
            endProgress()
        }
    }

    private fun exportData() {
        confirmDialog = ConfirmDialog("EXPORT FACILITATOR DATA", "Are you sure to export " +
                "facilitator data to Excel?", "Export", "Cancel", Global.isPINDisabled) {
            export()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun deleteNotifications() {
        confirmDialog = ConfirmDialog("DELETE NOTIFICATIONS", "Are you sure to delete all " +
                "notifications?", "Delete", "Cancel", true) {
            delNotifications()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun delNotifications() {
        setupProgress("Deleting notifications")
        userRef.child("/notifications").removeValue()
            .addOnSuccessListener {
                notifications.clear()
                notificationAdapter = NotificationAdapter(notifications, this)
                binding.notifications.rvNotifications.adapter = notificationAdapter
                binding.notifications.btnDelete.visibility = View.GONE
                snackBar(view, "Notifications have been deleted")
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun setupMenu() {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.adminMenu.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.adminMenu.userImage)
        binding.adminMenu.editImage.visibility = View.VISIBLE
        binding.adminMenu.userName.text = user.name
        binding.adminMenu.userEmail.text = user.email!!.substringBefore('@')
        binding.adminMenu.userLevel.text = account.login
        binding.adminMenu.menuAccount.text = "Change password"
        binding.adminMenu.menuAccount.setIconResource(R.drawable.password)
    }

    private fun resetData(onComplete: () -> Unit) {
        if (Global.searching) {
            Global.searching = false
            binding.searchBar.setQuery(null, false)
            Handler(Looper.getMainLooper()).postDelayed({
                onComplete()
            }, 50)
        } else onComplete()
    }

    private fun report() {
        val bugReport = BugReport { desc, rep ->
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = "mailto:".toUri()
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("hk.faci.dev@gmail.com"))
            intent.putExtra(Intent.EXTRA_SUBJECT, "BUG REPORT/SUGGESTION: HK FACILITATOR/ADMIN")
            intent.putExtra(Intent.EXTRA_TEXT, Global.bugReport(desc, rep))
            startActivity(Intent.createChooser(intent, "Submit report/suggestion"))
        }
        bugReport.show(childFragmentManager, "BugReport")
    }

    private fun changePIN() {
        resetDialog = ResetDialog(user.email!!, auth, dbRef, "PIN", false) { pin ->
            account.pin = pin!!.hashSHA256()
            Global.account = account
            snackBar(view, "PIN has been changed")
        }
        resetDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun changePassword() {
        resetDialog = ResetDialog(user.email!!, auth, dbRef, "PASSWORD", false) {
            snackBar(view, "Password has been changed")
        }
        resetDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun setImage() {
        confirmDialog = ConfirmDialog("UPLOAD PROFILE", "Please confirm profile upload",
            "Proceed", "Cancel", Global.isPINDisabled) {
            getImage()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun getImage() {
        val currentMonth = getZonedDateTime().monthValue
        if (user.lastProfile != null) {
            if (user.lastProfile!! == currentMonth)
                snackBar(view, "Profile upload limit for this month has been reached")
            else getImage.launch("image/*")
        } else getImage.launch("image/*")
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
            when (Global.tabPosition) {
                0 -> rvFacilitators.adapter = FacilitatorAdapter(facilitators, this)
                1 -> rvFaculty.adapter = FacultyAdapter(faculty, this, true)
                2 -> rvManagers.adapter = FacultyAdapter(managers, this, false)
            }
        }
    }

    private fun filterDataOnSubmit(query: String?) {
        filtered.clear()
        if (!query.isNullOrBlank()) {
            Global.searching = true
            when (Global.tabPosition) {
                0 -> {
                    val regex = Regex("^\\d{2}-\\d{4}-\\d{5,}$")
                    if (regex.matches(query)) filtered.addAll(Global.facilitators!!.filter { item ->
                        item.id!!.contains(query, true)
                    })
                    else if (query.startsWith("appeal", true))
                        filtered.addAll(Global.facilitators!!.filter { item ->
                            item.appeals != null
                        })
                    else filtered.addAll(Global.facilitators!!.filter { item ->
                        item.name!!.contains(query, true) ||
                                item.course!!.contains(query, true) ||
                                item.gender!!.contains(query, true) ||
                                item.email!!.contains(query, true) ||
                                item.hk!!.contains(query, true) ||
                                item.hrs.toString().contains(query, true) ||
                                item.toString().contains(query, true)
                    })
                    rvFacilitators.adapter = FacilitatorAdapter(filtered, this)
                }
                1 -> {
                    filtered.addAll(Global.faculty!!.filter { item ->
                        item.name!!.contains(query, true)
                    })
                    rvFaculty.adapter = FacultyAdapter(filtered, this, true)
                }
                2 -> {
                    filtered.addAll(Global.managers!!.filter { item ->
                        item.name!!.contains(query, true) ||
                                item.course!!.contains(query, true)
                    })
                    rvManagers.adapter = FacultyAdapter(filtered, this, false)
                }
            }
        }
    }

    private fun signOut() {
        confirmDialog = ConfirmDialog("SIGN-OUT", "Are you sure you want to sign out?",
            "Yes", "No", true) {
            auth.signOut()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun uploadImage() {
        setupProgress("Uploading profile")
        val bytes = getUriBytes(requireContext(), uri!!, 100_000)
        bytes.let { byte ->
            stRef.child("/Profiles/${user.email!!.hashSHA256()}").putBytes(byte)
                .addOnSuccessListener { task ->
                    task.metadata!!.reference!!.downloadUrl
                        .addOnSuccessListener { url ->
                            val month = getZonedDateTime().monthValue
                            dbRef.child("/Data/Admin/${user.email!!.hashSHA256()}")
                                .updateChildren(hashMapOf<String, Any>(
                                    "/profileUrl" to url.toString(),
                                    "/lastProfile" to month))
                                .addOnSuccessListener {
                                    binding.adminMenu.userImage.setImageURI(uri)
                                    Global.userData!!.lastProfile = month
                                    user.lastProfile = month
                                    Global.userData!!.profileUrl = url.toString()
                                    user.profileUrl = url.toString()
                                    endProgress()
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
    }

    private fun removeNotice() {
        if (!isNotificationDisabled(requireContext()) && !isBatteryOptimized(requireContext()))
            binding.notifications.btnPermissions.visibility = View.GONE
        if (binding.notice.isVisible) {
            dbRef.child("/Data/Admin/${user.email!!.hashSHA256()}/notified")
                .setValue(null)
                .addOnSuccessListener {
                    binding.notice.visibility = View.GONE
                }
        }
    }

    private fun setupTabs() {
        binding.tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Global.tabPosition = tab!!.position
                if (tab.position == 0) binding.btnExport.visibility = View.VISIBLE
                else binding.btnExport.visibility = View.GONE
                clear()
                resetData {
                    binding.vpViews.currentItem = tab.position
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

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackFalse)
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackTrue)
    }

    private fun back() {
        val current = System.currentTimeMillis()
        if (current - prev > 2000) {
            press = 1
            snackBar(view, "Press back again to exit")
            Handler(Looper.getMainLooper()).postDelayed({
                cancelSnackBar()
            }, 2000)
        } else {
            press++
            if (press == 2) {
                cancelSnackBar()
                requireActivity().finish()
            }
        }
        prev = current
    }

    private fun menuOpen() {
        transition = Fade()
        transition.duration = 100
        TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
        binding.adminMenu.navBg.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            transition = Slide(Gravity.START)
            TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
            binding.adminMenu.navMenu.visibility = View.VISIBLE
        }, 25)
    }

    private fun menuClose() {
        transition = Fade()
        transition.duration = 100
        TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
        binding.adminMenu.navBg.visibility = View.GONE
        Handler(Looper.getMainLooper()).postDelayed({
            transition = Slide(Gravity.START)
            TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
            binding.adminMenu.navMenu.visibility = View.GONE
        }, 25)
    }

    private fun notificationOpen() {
        transition = Fade()
        transition.duration = 100
        TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
        binding.notifications.notificationBg.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            transition = Slide(Gravity.END)
            TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
            binding.notifications.notificationContainer.visibility = View.VISIBLE
        }, 25)
    }

    private fun notificationClose() {
        transition = Fade()
        transition.duration = 100
        TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
        binding.notifications.notificationBg.visibility = View.GONE
        Handler(Looper.getMainLooper()).postDelayed({
            transition = Slide(Gravity.END)
            TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
            binding.notifications.notificationContainer.visibility = View.GONE
        }, 25)
    }

    private fun permissions() {
        if (isAlarmDisabled(requireContext())) alarmSetting()
        if (isBatteryOptimized(requireContext())) batterySetting()
    }

    private fun notifications() {
        val isRequested = sharedPreferences.getBoolean("notificationPermission", false)
        if (isNotificationDisabled(requireContext())) {
            if (!isRequested) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                sharedPreferences.edit {
                    putBoolean("notificationPermission", true)
                    apply()
                }
            }
        } else startNotificationWorker(requireContext())
    }

    private fun batteryOptimization() {
        val isRequested = sharedPreferences.getBoolean("batteryOptimization", false)
        if (isBatteryOptimized(requireContext()) && !isRequested) {
            confirmDialog = ConfirmDialog("NOTIFICATIONS AND SYNC", "To allow your app to " +
                    "sync data${if (isNotificationDisabled(requireContext())) "" else " and receive notifications" +
                            "(if enabled)"}, you may be REQUIRED to disable battery optimizations " +
                    "for this application in your device settings. Please select 'No restrictions'.",
                "Change settings", "Later", true) {
                batterySetting()
            }
            confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
            sharedPreferences.edit {
                putBoolean("batteryOptimization", true)
                apply()
            }
        }
    }

    private fun batterySetting() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${requireContext().packageName}".toUri()
        }
        startActivity(intent)
    }

    private fun alarmPermission() {
        val isRequested = sharedPreferences.getBoolean("alarmPermission", false)
        if (isAlarmDisabled(requireContext()) && !isRequested) {
            confirmDialog = ConfirmDialog("ALARMS AND REMINDERS", "To make sure you receive " +
                    "reminders for your schedules regularly and in real-time, you may be REQUIRED to turn " +
                    "on 'Alarms and Reminders' for this application in your device settings.",
                "Change settings", "Later", true) {
                alarmSetting()
            }
            confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
            sharedPreferences.edit {
                putBoolean("alarmPermission", true)
                apply()
            }
        }
    }

    private fun alarmSetting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${requireContext().packageName}".toUri()
            }
            startActivity(intent)
        }
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}