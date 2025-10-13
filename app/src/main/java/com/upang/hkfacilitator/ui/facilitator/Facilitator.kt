package com.upang.hkfacilitator.ui.facilitator

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.os.*
import android.provider.Settings
import android.transition.*
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.SearchView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.*
import com.upang.hkfacilitator.databinding.FacilitatorBinding
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.showDatePicker
import com.upang.hkfacilitator.models.Global.isWithinTimeRange
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.createNotificationChannel
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isActive
import com.upang.hkfacilitator.models.Global.isAlarmDisabled
import com.upang.hkfacilitator.models.Global.isBatteryOptimized
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.isNotRestricted
import com.upang.hkfacilitator.models.Global.isNotificationDisabled
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.startNotificationWorker
import com.upang.hkfacilitator.models.Global.startSyncWorker
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.timeRangeTo24
import com.upang.hkfacilitator.ui.popup.*
import com.upang.hkfacilitator.utils.*
import com.upang.hkfacilitator.viewholders.NotificationViewHolder
import java.util.concurrent.*
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.net.toUri

@SuppressLint("ClickableViewAccessibility, SetTextI18n, BatteryLife")
class Facilitator : Fragment(), ConnectionStateListener, SchedClickListener,
    NotificationClickListener {

    private lateinit var binding: FacilitatorBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var notifications: ArrayList<Notifications>
    private var watchEventListener: ValueEventListener? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var filteredJoined: ArrayList<Schedule>
    private lateinit var filteredActive: ArrayList<Schedule>
    private lateinit var joinedToFilter: ArrayList<Schedule>
    private lateinit var extraToFilter: ArrayList<Schedule>
    private lateinit var filteredExtra: ArrayList<Schedule>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var joined: ArrayList<Schedule>
    private lateinit var active: ArrayList<Schedule>
    private lateinit var extra: ArrayList<Schedule>
    private lateinit var userRef: DatabaseReference
    private lateinit var courses: ArrayList<String>
    private lateinit var dbRef: DatabaseReference
    private var resetDialog: ResetDialog? = null
    private lateinit var transition: Transition
    private var permanent: Schedule? = null
    private lateinit var auth: FirebaseAuth
    private var watchQuery: Query? = null
    private lateinit var account: Account
    private lateinit var user: User
    private var isDoneSetup = false
    private var isPaused = false
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
            Global.tabPosition = 0
            Global.isPINDisabled = false
            Global.searching = false
            Global.permanent = null
            Global.joined = null
            Global.extras = null
            Global.notifications = null
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
        if (sharedPreferences.getString("Login", null) == "Facilitator") {
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
        binding = FacilitatorBinding.inflate(inflater, container, false)
        if (isBatteryOptimized(requireContext()) || isAlarmDisabled(requireContext()))
            binding.notifications.btnPermissions.visibility = View.VISIBLE
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

        binding.rvActive.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.rvJoined.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.rvExtra.apply {
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

        binding.menuOpen.setOnClickListener {
            clear()
            menuOpen()
        }

        binding.faciMenu.menuClose.setOnClickListener {
            menuClose()
        }

        binding.faciMenu.navBg.setOnClickListener {
            menuClose()
        }

        binding.faciMenu.menuAccount.setOnDebouncedClickListener {
            findNavController().navigate(FacilitatorDirections.actionHomeFacilitatorToFacilitatorProfile())
        }

        binding.faciMenu.menuPIN.setOnDebouncedClickListener {
            menuClose()
            changePIN()
        }

        binding.faciMenu.menuSignout.setOnDebouncedClickListener {
            menuClose()
            signOut()
        }

        binding.faciMenu.menuFeedback.setOnDebouncedClickListener {
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

        binding.btnDatePicker.setOnDebouncedClickListener {
            showDatePicker(requireContext(), binding.searchBar)
        }

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

        binding.btnDebug.setOnDebouncedClickListener {
            findNavController().navigate(FacilitatorDirections.actionHomeFacilitatorToTest())
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
            watchEventListener?.let { watchQuery?.removeEventListener(it) }
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
        watchEventListener?.let { watchQuery?.removeEventListener(it) }
        auth.removeAuthStateListener(authListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
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
                if (binding.loading.container.isVisible) endProgress()
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
            auth.removeAuthStateListener(authListener)
            if (isConnected(requireContext())) endProgress()
            else watchEventListener?.let { watchQuery?.removeEventListener(it) }
        }
    }

    override fun onSchedClick(sched: Schedule) {
        navigateToSched(sched)
    }

    override fun getViewHolder(id: String): List<NotificationViewHolder> {
        return notificationAdapter.getNotificationViewHolders(id)
    }

    override fun readNotification(id: String) {
        val updates = hashMapOf<String, Any>( "/read" to true, "/notified" to true )
        userRef.child("/notifications/$id").updateChildren(updates)
    }

    override fun navigateToLink(link: String, destination: String) {
        when (destination) {
            "HOME" -> findNavController().navigate(FacilitatorDirections.actionHomeFacilitatorToFacilitatorProfile())
            "SCHEDULE" -> {
                val schedule = if (Global.permanent != null && Global.permanent!!.id == link)
                    Global.permanent else active.firstOrNull { it.id == link }
                    ?: Global.joined?.firstOrNull { it.id == link }
                    ?: Global.extras?.firstOrNull { it.id == link }
                if (schedule != null) navigateToSched(schedule)
                else snackBar(view, "Schedule does not exist")
            }
        }
    }

    private fun initializations() {
        user = Global.userData!!
        account = Global.account!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        userRef = dbRef.child("/Data/Facilitator/${user.email!!.hashSHA256()}")
        courses = arrayListOf()
        active = arrayListOf()
        extra = arrayListOf()
        joined = arrayListOf()
        extraToFilter = arrayListOf()
        joinedToFilter = arrayListOf()
        filteredActive = arrayListOf()
        filteredJoined = arrayListOf()
        filteredExtra = arrayListOf()
        notifications = arrayListOf()
    }

    private fun fetchData() {
        setupMenu()
        if (Global.courses == null || Global.permanent == null ||
            Global.joined == null || Global.extras == null ||
            Global.notifications == null)
            setupProgress("Loading data")
        else setupPermanent()
        watchFaci {
            loadData(false)
        }
    }

    private fun loadData(isRefresh: Boolean) {
        fetchCourses {
            fetchHour {
                checkNotice {
                    fetchPermanent {
                        fetchExtras {
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

    private fun setupPermanent() {
        binding.Permanent.title.text = Global.permanent!!.title
        binding.Permanent.owner.text = Global.permanent!!.owner
        binding.Permanent.room.text = Global.permanent!!.room
        binding.Permanent.date.text = Global.permanent!!.date
        binding.Permanent.time.text = timeRangeTo12(Global.permanent!!.time)
        if (Global.permanent!!.isActive == true)
            binding.Permanent.activeNotice.visibility = View.VISIBLE
        else binding.Permanent.activeNotice.visibility = View.GONE
        binding.Permanent.cvSchedule.setOnDebouncedClickListener {
            navigateToSched(permanent!!)
        }
        binding.Permanent.container.visibility = View.VISIBLE
        binding.noPermanent.visibility = View.GONE
    }

    private fun setupData(onComplete: () -> Unit) {
        if (!Global.searching) {
            if (permanent != null) setupPermanent()
            else {
                binding.Permanent.container.visibility = View.GONE
                binding.noPermanent.visibility = View.VISIBLE
            }
            if (active.isNotEmpty()) binding.active.visibility = View.VISIBLE
            else binding.active.visibility = View.GONE
            if (joined.isNotEmpty()) binding.joined.visibility = View.VISIBLE
            else binding.joined.visibility = View.GONE
            if (extra.isNotEmpty()) binding.extra.visibility = View.VISIBLE
            else binding.extra.visibility = View.GONE
            binding.rvActive.adapter = SchedAdapter(active, this)
            binding.rvJoined.adapter = SchedAdapter(joined, this)
            binding.rvExtra.adapter = SchedAdapter(extra, this)
        } else filterDataOnSubmit(binding.searchBar.query.toString().trim())
        if (notifications.isNotEmpty()) {
            notificationAdapter = NotificationAdapter(notifications, this)
            binding.notifications.rvNotifications.adapter = notificationAdapter
            binding.notifications.btnDelete.visibility = View.VISIBLE
        }
        onComplete()
    }

    private fun fetchNotifications(onComplete: () -> Unit) {
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

    private fun fetchExtras(onComplete: () -> Unit) {
        dbRef.child("/Extras").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    extra.clear()
                    active.clear()
                    joined.clear()
                    extraToFilter.clear()
                    joinedToFilter.clear()
                    if (snapshot.exists()) for (sched in snapshot.children) {
                        val schedule = sched.getValue(Schedule::class.java)!!
                        if (sched.child("faci").hasChild(user.email!!.hashSHA256())) {
                            val blacklisted = sched.child("/faci/${user.email!!.hashSHA256()}/blacklisted").getValue(Boolean::class.java)
                            if (blacklisted == null) {
                                joinedToFilter.apply {
                                    schedule.isJoinedIn = true
                                    add(schedule)
                                }
                            }
                        } else extraToFilter.add(schedule)
                    }
                    extra.addAll(extraToFilter.filter { item ->
                        item.joined!! < item.need!! && !isActive(item, true) &&
                                !isPast(item, true) &&
                                isNotRestricted(item, user.course!!) &&
                                item.extension == null
                    })
                    active.apply {
                        joinedToFilter.filter { item ->
                            (item.suspended == null) &&
                                    isActive(item, false)
                        }.forEach{ item ->
                            item.isActive = true
                            add(item)
                        }
                    }
                    joined.addAll(joinedToFilter.filter { item ->
                        !active.any { filter ->
                            item.id!!.equals(filter.id!!, true)
                        } && !isPast(item, true)
                    })
                    Global.joined = joined
                    Global.extras = extra
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchPermanent(onComplete: () -> Unit) {
        if (user.assignment != null) {
            dbRef.child("/Permanents/${user.assignment}").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            permanent = snapshot.getValue(Schedule::class.java)!!
                            permanent!!.isJoinedIn = true
                            if ((permanent!!.suspended == null) &&
                                isActive(permanent!!, false)) permanent!!.isActive = true
                            Global.permanent = permanent
                        }
                        onComplete()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                        onComplete()
                    }
                })
        } else onComplete()
    }

    private fun checkNotice(onComplete: () -> Unit) {
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

    private fun fetchHour(onComplete: () -> Unit) {
        userRef.child("/render").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) user.render = snapshot.getValue(Float::class.java)
                    binding.faciHours.text = "${"%.1f".format(user.hrs)}/${"%.1f".format(user.render)} HRS"
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                }
            })
        userRef.child("/hrs").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) user.hrs = snapshot.getValue(Float::class.java)
                    binding.faciHours.text = "${"%.1f".format(user.hrs)}/${"%.1f".format(user.render)} HRS"
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchCourses(onComplete: () -> Unit) {
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

    private fun watchFaci(onComplete: () -> Unit) {
        watchQuery = userRef.child("email")
        watchEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() && snapshot.value == null) {
                    snackBar(view, "Your account has been deleted")
                    auth.signOut()
                }
                onComplete()
            }
            override fun onCancelled(error: DatabaseError) {
                snackBar(view, "Error: ${error.message}")
                onComplete()
            }
        }
        watchEventListener?.let { watchQuery?.addValueEventListener(it) }
    }

    private fun deleteNotifications() {
        confirmDialog = ConfirmDialog("DELETE NOTIFICATIONS", "Are you sure to delete " +
                "all notifications?", "Delete", "Cancel", true) {
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
                binding.faciMenu.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.faciMenu.userImage)
        binding.faciMenu.userName.text = user.name
        binding.faciMenu.userEmail.text = user.email!!.substringBefore('@')
        binding.faciMenu.userLevel.text = account.login
    }

    private fun navigateToSched(sched: Schedule) {
        Global.schedule = sched
        findNavController().navigate(FacilitatorDirections.actionHomeFacilitatorToFacilitatorSchedule())
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
            if (active.isNotEmpty()) binding.active.visibility = View.VISIBLE
            else binding.active.visibility = View.GONE
            if (joined.isNotEmpty()) binding.joined.visibility = View.VISIBLE
            else binding.joined.visibility = View.GONE
            if (extra.isNotEmpty()) binding.extra.visibility = View.VISIBLE
            else binding.extra.visibility = View.GONE
            binding.rvActive.adapter = SchedAdapter(active, this)
            binding.rvJoined.adapter = SchedAdapter(joined, this)
            binding.rvExtra.adapter = SchedAdapter(extra, this)
        }
    }

    private fun filterDataOnSubmit(query: String?) {
        filteredActive.clear()
        filteredJoined.clear()
        filteredExtra.clear()
        if (!query.isNullOrBlank()) {
            Global.searching = true
            filteredActive.addAll(filterData(query, active))
            filteredJoined.addAll(filterData(query, joined))
            filteredExtra.addAll(filterData(query, extra))
            if (filteredActive.isNotEmpty()) binding.active.visibility = View.VISIBLE
            else binding.active.visibility = View.GONE
            if (filteredJoined.isNotEmpty()) binding.joined.visibility = View.VISIBLE
            else binding.joined.visibility = View.GONE
            if (filteredExtra.isNotEmpty()) binding.extra.visibility = View.VISIBLE
            else binding.extra.visibility = View.GONE
            binding.rvActive.adapter = SchedAdapter(filteredActive, this)
            binding.rvJoined.adapter = SchedAdapter(filteredJoined, this)
            binding.rvExtra.adapter = SchedAdapter(filteredExtra, this)
        }
    }

    private fun filterData(query: String, array: ArrayList<Schedule>): List<Schedule> {
        val dateRegex = Regex("^\\d{2}-\\d{2}-\\d{2}$")
        val time24Regex = Regex("^\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}$")
        val time12Regex = Regex("^\\d{2}:\\d{2}\\s*(AM|PM)\\s*-\\s*\\d{2}:\\d{2}\\s*(AM|PM)$")
        return if (time24Regex.matches(query) || time12Regex.matches(query)) {
            array.filter { item ->
                isWithinTimeRange(if (time24Regex.matches(query)) query else timeRangeTo24(query), item.time!!)
            }
        } else if (dateRegex.matches(query)) {
            array.filter { item ->
                item.date!!.contains(query, true)
            }
        } else array.filter { item ->
            item.title!!.contains(query, true) ||
                    item.room!!.contains(query, true) ||
                    item.date!!.contains(query, true) ||
                    item.time!!.contains(query, true)
        }
    }

    private fun removeNotice() {
        if (!isNotificationDisabled(requireContext()) && !isBatteryOptimized(requireContext()))
            binding.notifications.btnPermissions.visibility = View.GONE
        if (binding.notice.isVisible) {
            userRef.child("/notified").setValue(null)
                .addOnSuccessListener {
                    binding.notice.visibility = View.GONE
                }
        }
    }

    private fun report() {
        val bugReport = BugReport { desc, rep ->
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = "mailto:".toUri()
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("hk.faci.dev@gmail.com"))
            intent.putExtra(Intent.EXTRA_SUBJECT, "BUG REPORT/SUGGESTION: HK FACILITATOR/FACILITATOR")
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

    private fun signOut() {
        confirmDialog = ConfirmDialog("SIGN-OUT", "Are you sure you want to sign out?",
            "Yes", "No", true) {
            auth.signOut()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
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
        binding.faciMenu.navBg.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            transition = Slide(Gravity.START)
            TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
            binding.faciMenu.navMenu.visibility = View.VISIBLE
        }, 25)
    }

    private fun menuClose() {
        transition = Fade()
        transition.duration = 100
        TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
        binding.faciMenu.navBg.visibility = View.GONE
        Handler(Looper.getMainLooper()).postDelayed({
            transition = Slide(Gravity.START)
            TransitionManager.beginDelayedTransition(view as ViewGroup, transition)
            binding.faciMenu.navMenu.visibility = View.GONE
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