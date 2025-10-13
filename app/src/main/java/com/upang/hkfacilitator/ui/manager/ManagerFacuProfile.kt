package com.upang.hkfacilitator.ui.manager

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.*
import com.upang.hkfacilitator.databinding.ManagerFacuProfileBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isActive
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

class ManagerFacuProfile : Fragment(), ConnectionStateListener,
    EvaluationClickListener, SchedClickListener {

    private lateinit var binding: ManagerFacuProfileBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private var watchEventListener: ValueEventListener? = null
    private var facuEventListener: ValueEventListener? = null
    private lateinit var facuEval: ArrayList<Evaluation>
    private lateinit var permanent: ArrayList<Schedule>
    private lateinit var facuSched: ArrayList<Schedule>
    private lateinit var extra: ArrayList<Schedule>
    private lateinit var facuRef: DatabaseReference
    private lateinit var dbRef: DatabaseReference
    private lateinit var rvSched: RecyclerView
    private lateinit var rvEval: RecyclerView
    private lateinit var profiledUser: User
    private var watchQuery: Query? = null
    private var facuQuery: Query? = null
    private lateinit var user: User
    private var isDoneSetup = false
    private var isPaused = false
    private val callbackFalse = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializations()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ManagerFacuProfileBinding.inflate(inflater, container, false)
        rvSched = LayoutInflater.from(requireContext()).inflate(R.layout.rv_sched, binding.root, false) as RecyclerView
        rvEval = LayoutInflater.from(requireContext()).inflate(R.layout.rv_evaluation, binding.root, false) as RecyclerView
        binding.vpViews.adapter = TabAdapter(listOf(rvSched, rvEval))
        binding.vpViews.isUserInputEnabled = false
        binding.vpViews.offscreenPageLimit = 1
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        isPaused = false

        rvSched.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvEval.apply {
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
        try { removeListeners() }
        catch (_: Exception) {}
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
                    addListener(watchQuery, watchEventListener)
                    addListener(facuQuery, facuEventListener)
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

    override fun onEvalEditClick(eval: Evaluation) {}
    override fun onEvalDeleteClick(id: String) {}

    override fun onSchedClick(sched: Schedule) {
        Global.schedule = sched
        findNavController().navigate(ManagerFacuProfileDirections.actionManagerFacuProfileToManagerFacuSchedule())
    }

    private fun initializations() {
        user = Global.userData!!
        profiledUser = Global.profiledUser!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        facuRef = dbRef.child("/Data/Faculty/${profiledUser.email!!.hashSHA256()}")
        permanent = arrayListOf()
        extra = arrayListOf()
        facuSched = arrayListOf()
        facuEval = arrayListOf()
        listenerMap = mutableMapOf()
    }

    private fun setupData() {
        setupProgress("Loading data")
        if (setupProfile()) watchManager {
            watchFacu {
                fetchPermanentSched {
                    fetchExtraSched {
                        fetchEval {
                            isDoneSetup = true
                            endProgress()
                        }
                    }
                }
            }
        }
    }

    private fun watchManager(onComplete: () -> Unit) {
        watchQuery = dbRef.child("/Data/Manager/${user.email!!.hashSHA256()}/email")
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

    private fun watchFacu(onComplete: () -> Unit) {
        facuQuery = facuRef.child("email")
        facuEventListener = object : ValueEventListener {
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
        addListener(facuQuery, facuEventListener)
    }

    private fun fetchEval(onComplete: () -> Unit) {
        facuRef.child("/evaluation").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    facuEval.clear()
                    if (snapshot.exists()) for (eval in snapshot.children)
                        facuEval.add(eval.getValue(Evaluation::class.java)!!)
                    facuEval.sortWith(compareByDescending { it.date })
                    rvEval.adapter = EvaluationAdapter(facuEval, this@ManagerFacuProfile, true)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchExtraSched(onComplete: () -> Unit) {
        dbRef.child("/Extras").orderByChild("email").equalTo(profiledUser.email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    extra.clear()
                    facuSched.clear()
                    if (snapshot.exists()) for (sched in snapshot.children) {
                        val schedule = sched.getValue(Schedule::class.java)!!
                        if (schedule.extension == null) {
                            if (!isPast(schedule, true) && !isActive(schedule, true) &&
                                (!schedule.restrict!! || (schedule.restrict!! && schedule.subject!!
                                    .removeYear() != user.course))) extra.add(schedule)
                        } else extra.add(schedule)
                    }
                    facuSched.addAll(permanent + extra)
                    rvSched.adapter = SchedAdapter(facuSched, this@ManagerFacuProfile)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchPermanentSched(onComplete: () -> Unit) {
        dbRef.child("/Permanents").orderByChild("email").equalTo(profiledUser.email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    permanent.clear()
                    facuSched.clear()
                    if (snapshot.exists()) for (sched in snapshot.children) {
                        val schedule = sched.getValue(Schedule::class.java)!!
                        if (!isActive(schedule, true) && (!schedule.restrict!! ||
                                    (schedule.restrict!! && schedule.subject!!
                                        .removeYear() != user.course))) permanent.add(schedule)
                    }
                    facuSched.addAll(permanent + extra)
                    rvSched.adapter = SchedAdapter(facuSched, this@ManagerFacuProfile)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun setupProfile(): Boolean {
        if (profiledUser.profileUrl == null) {
            if (profiledUser.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get().load(profiledUser.profileUrl).into(binding.userImage)
        binding.name.text = profiledUser.name
        binding.email.text = profiledUser.email!!.substringBefore('@')
        return true
    }

    private fun setupTabs() {
        binding.tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.vpViews.currentItem = tab!!.position
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
    }

    private fun removeListeners() {
        removeListener(watchQuery)
        removeListener(facuQuery)
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