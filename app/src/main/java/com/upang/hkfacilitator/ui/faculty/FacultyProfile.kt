package com.upang.hkfacilitator.ui.faculty

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
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
import com.upang.hkfacilitator.databinding.FacultyProfileBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.getUriBytes
import com.upang.hkfacilitator.models.Global.getZonedDateTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.ui.popup.*
import com.upang.hkfacilitator.utils.*
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

class FacultyProfile : Fragment(), ConnectionStateListener, EvaluationClickListener,
    FacultyClickListener {

    private lateinit var binding: FacultyProfileBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var listenerMap: MutableMap<Query, ValueEventListener>
    private var watchEventListener: ValueEventListener? = null
    private var faciEventListener: ValueEventListener? = null
    private var evalEventListener: ValueEventListener? = null
    private var evaluationDialog: EvaluationDialog? = null
    private lateinit var evaluation: ArrayList<Evaluation>
    private lateinit var facilitators: ArrayList<User>
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var userRef: DatabaseReference
    private lateinit var rvEvaluation: RecyclerView
    private lateinit var rvEvaluate: RecyclerView
    private lateinit var dbRef: DatabaseReference
    private var resetDialog: ResetDialog? = null
    private lateinit var stRef: StorageReference
    private lateinit var auth: FirebaseAuth
    private var watchQuery: Query? = null
    private lateinit var account: Account
    private var faciQuery: Query? = null
    private var evalQuery: Query? = null
    private lateinit var userData: User
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
            uploadProfile()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializations()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FacultyProfileBinding.inflate(inflater, container, false)
        rvEvaluate = LayoutInflater.from(requireContext()).inflate(R.layout.rv_evaluate, binding.root, false) as RecyclerView
        rvEvaluation = LayoutInflater.from(requireContext()).inflate(R.layout.rv_evaluation, binding.root, false) as RecyclerView
        binding.vpViews.adapter = TabAdapter(listOf(rvEvaluate, rvEvaluation))
        binding.vpViews.isUserInputEnabled = false
        binding.vpViews.offscreenPageLimit = 1
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        isPaused = false

        rvEvaluate.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        rvEvaluation.apply {
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
        if (evaluationDialog != null) if (evaluationDialog!!.isAdded) evaluationDialog!!.dismiss()
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
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
                    addListener(faciQuery, faciEventListener)
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
        userRef = dbRef.child("/Data/Faculty/${userData.email!!.hashSHA256()}")
        evaluation = arrayListOf()
        facilitators = arrayListOf()
        listenerMap = mutableMapOf()
    }

    private fun setupData() {
        setupProgress("Loading data")
        setupProfile()
        watchFacu {
            fetchFacilitators {
                fetchEval {
                    isDoneSetup = true
                    if (!isUploading) endProgress()
                }
            }
        }
    }

    private fun setupData(onComplete: () -> Unit) {
        dbRef.child("/Data/Facilitator").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) for (faci in snapshot.children) {
                        val data = faci.getValue(User::class.java)
                        val index = facilitators.indexOfFirst { it.email == data!!.email && data.profileUrl != null }
                        if (index != -1) facilitators[index].profileUrl = data!!.profileUrl
                    }
                    rvEvaluate.adapter = EvaluateAdapter(facilitators, this@FacultyProfile)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun watchFacu(onComplete: () -> Unit) {
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

    private fun fetchFacilitators(onComplete: () -> Unit) {
        faciQuery = userRef.child("/facilitator")
        faciEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    facilitators.clear()
                    if (snapshot.exists()) for (user in snapshot.children)
                        facilitators.add(user.getValue(User::class.java)!!)
                    setupData {
                        onComplete()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            }
        addListener(faciQuery, faciEventListener)
    }

    private fun fetchEval(onComplete: () -> Unit) {
        evalQuery = userRef.child("/evaluation")
        evalEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    evaluation.clear()
                    if (snapshot.exists()) for (eval in snapshot.children)
                        evaluation.add(eval.getValue(Evaluation::class.java)!!)
                    evaluation.sortWith(compareByDescending { it.date })
                    rvEvaluation.adapter = EvaluationAdapter(evaluation, this@FacultyProfile, false)
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete()
                }
            }
        addListener(evalQuery, evalEventListener)
    }

    private fun setupProfile(): Boolean {
        if (userData.profileUrl == null) {
            if (userData.gender.equals("Female"))
                binding.userImage.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(userData.profileUrl)
            .placeholder(if (userData.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.userImage)
        binding.name.text = userData.name
        binding.email.text = userData.email!!.substringBefore('@')
        return true
    }

    private fun changePassword() {
        resetDialog = ResetDialog(userData.email!!, auth, dbRef, "PASSWORD", false) {
            snackBar(view, "Password has been changed")
        }
        resetDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun showEvaluation(user: User?, eval: Evaluation?, isEdit: Boolean) {
        evaluationDialog = EvaluationDialog(user, userData, "Faculty", eval, isEdit) {
            if (isEdit) snackBar(view, "Evaluation has been modified")
            else snackBar(view, "Evaluation has been submitted")
        }
        evaluationDialog!!.show(childFragmentManager, "EvaluationDialog")
    }

    private fun setProfile() {
        confirmDialog = ConfirmDialog("UPLOAD PROFILE", "Please confirm profile " +
                "upload", "Select", "Cancel", Global.isPINDisabled) {
            upload()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun upload() {
        val currentMonth = getZonedDateTime().monthValue
        if (userData.lastProfile != null && userData.lastProfile!! == currentMonth)
            snackBar(view, "Profile upload limit for this month has been reached")
        else getImage.launch("image/*")
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
                                    Global.userData!!.lastProfile = month
                                    Global.userData!!.profileUrl = url.toString()
                                    userData.lastProfile = month
                                    userData.profileUrl = url.toString()
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
        removeListener(faciQuery)
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
}