package com.upang.hkfacilitator.ui.admin

import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
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
import com.upang.hkfacilitator.databinding.AdminFacuProfileBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.emailToKey
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isActive
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.ui.popup.ConfirmDialog
import com.upang.hkfacilitator.utils.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

class AdminFacuProfile : Fragment(), ConnectionStateListener,
    EvaluationClickListener, SchedClickListener {

    private lateinit var binding: AdminFacuProfileBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var facuEventListener: ValueEventListener
    private lateinit var facuEval: ArrayList<Evaluation>
    private lateinit var completed: ArrayList<Schedule>
    private lateinit var permanent: ArrayList<Schedule>
    private lateinit var facuSched: ArrayList<Schedule>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var extra: ArrayList<Schedule>
    private lateinit var facuRef: DatabaseReference
    private lateinit var dbRef: DatabaseReference
    private lateinit var stRef: StorageReference
    private lateinit var rvSched: RecyclerView
    private lateinit var rvEval: RecyclerView
    private lateinit var auth: FirebaseAuth
    private lateinit var facuQuery: Query
    private lateinit var user: User
    private var isDoneSetup = false
    private var unfinished = false
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
        binding = AdminFacuProfileBinding.inflate(inflater, container, false)
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
        fetchData()
        setupTabs()

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnRemove.setOnDebouncedClickListener {
            terminateAccount()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isConnected(requireContext())) {
            isPaused = true
            setupProgress("Waiting for connection")
            facuQuery.removeEventListener(facuEventListener)
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
        try { facuQuery.removeEventListener(facuEventListener) }
        catch (_: Exception) {}
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
                else facuQuery.addValueEventListener(facuEventListener)
            }
        }
    }

    override fun onNetworkLost() {
        requireActivity().runOnUiThread {
            isPaused = true
            setupProgress("Waiting for connection")
            if (isConnected(requireContext())) endProgress()
            else facuQuery.removeEventListener(facuEventListener)
        }
    }

    override fun onEvalEditClick(eval: Evaluation) {}
    override fun onEvalDeleteClick(id: String) {}

    override fun onSchedClick(sched: Schedule) {
        Global.schedule = sched
        findNavController().navigate(AdminFacuProfileDirections.actionAdminFacuProfileToAdminFacuSchedule())
    }

    private fun initializations() {
        user = Global.profiledUser!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        stRef = FirebaseStorage.getInstance(Global.firebase!!).reference
        facuRef = dbRef.child("/Data/Faculty/${user.email!!.hashSHA256()}")
        auth = FirebaseAuth.getInstance(Global.firebase!!)
        completed = arrayListOf()
        permanent = arrayListOf()
        extra = arrayListOf()
        facuSched = arrayListOf()
        facuEval = arrayListOf()
    }

    private fun fetchData() {
        setupProgress("Loading data")
        if (setupProfile()) watchFacu {
            fetchCompleted {
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
        facuQuery.addValueEventListener(facuEventListener)
    }

    private fun fetchEval(onComplete: () -> Unit) {
        facuRef.child("/evaluation").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    facuEval.clear()
                    if (snapshot.exists()) for (eval in snapshot.children)
                        facuEval.add(eval.getValue(Evaluation::class.java)!!)
                    facuEval.sortWith(compareByDescending { it.date })
                    rvEval.adapter = EvaluationAdapter(facuEval, this@AdminFacuProfile, true)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchExtraSched(onComplete: () -> Unit) {
        dbRef.child("/Extras").orderByChild("email").equalTo(user.email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    extra.clear()
                    facuSched.clear()
                    if (snapshot.exists()) for (sched in snapshot.children) {
                        val schedule = sched.getValue(Schedule::class.java)!!
                        if (schedule.extension == null) {
                            if (!isPast(schedule, true) && !isActive(schedule, true))
                                extra.add(schedule)
                        } else extra.add(schedule)
                    }
                    facuSched.addAll(permanent + extra)
                    rvSched.adapter = SchedAdapter(facuSched, this@AdminFacuProfile)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchCompleted(onComplete: () -> Unit) {
        dbRef.child("/Completed").orderByChild("email").equalTo(user.email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    completed.clear()
                    if (snapshot.exists()) for (sched in snapshot.children)
                        completed.add(sched.getValue(Schedule::class.java)!!)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun fetchPermanentSched(onComplete: () -> Unit) {
        dbRef.child("/Permanents").orderByChild("email").equalTo(user.email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    permanent.clear()
                    facuSched.clear()
                    if (snapshot.exists()) for (sched in snapshot.children) {
                        val schedule = sched.getValue(Schedule::class.java)!!
                        if (!isActive(schedule, true)) permanent.add(schedule)
                    }
                    facuSched.addAll(permanent + extra)
                    rvSched.adapter = SchedAdapter(facuSched, this@AdminFacuProfile)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun terminateAccount() {
        confirmDialog = ConfirmDialog("CONFIRM", "Please confirm account deletion",
            "Terminate", "Cancel", Global.isPINDisabled) {
            terminate()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun terminate() {
        unfinished = false
        val updates = hashMapOf<String, Any?>(
            "/Accounts/${emailToKey(user.email!!)}" to null,
            "/Data/Faculty/${user.email!!.hashSHA256()}" to null)
        updates.apply {
            facuSched.forEach { sched ->
                if (sched.id!![1].isLetter()) put("/Permanents/${sched.id}", null)
                else {
                    if (isPast(sched, true))
                        unfinished = true
                    put("/Extras/${sched.id}", null)
                }
            }
            unfinished = completed.isNotEmpty()
        }
        if (!unfinished) delete(updates)
        else {
            confirmDialog = ConfirmDialog("UNFINISHED SCHEDULES", "This account has " +
                    "unfinished schedules and deleting them will not give the facilitators who " +
                    "joined these the hours they should be getting. Please consider notifying the " +
                    "user to finish all their schedules before deleting their data. If you want to " +
                    "do otherwise, you can click Proceed.", "Proceed", "Cancel", Global.isPINDisabled) {
                delete(updates)
            }
            confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
        }
    }

    private fun delete(updates: HashMap<String, Any?>) {
        setupProgress("Deleting account")
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                stRef.child("/Profiles/${user.email!!.hashSHA256()}").delete()
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun setupProfile(): Boolean {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.userImage)
        binding.name.text = user.name
        binding.email.text = user.email!!.substringBefore('@')
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