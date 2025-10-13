package com.upang.hkfacilitator.ui.login

import android.annotation.SuppressLint
import android.content.*
import android.os.*
import android.text.*
import android.text.style.UnderlineSpan
import android.util.*
import android.view.*
import androidx.core.content.edit
import androidx.fragment.app.*
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.*
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.*
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.databinding.LoginBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.utils.*
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.adapters.SchoolsAdapter
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.emailToKey
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.ui.popup.*
import org.json.JSONObject
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri

@SuppressLint("SetTextI18n, ClickableViewAccessibility")
class Login : Fragment(), ConnectionStateListener, SchoolClickListener {

    private lateinit var binding: LoginBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var schools: ArrayList<School>
    private var forgotDialog: ForgotDialog? = null
    private lateinit var dbRef: DatabaseReference
    private var resetDialog: ResetDialog? = null
    private lateinit var auth: FirebaseAuth
    private var userSchool: String? = null
    private lateinit var email: String
    private lateinit var pass: String
    private var flow: String? = null
    private var pin: String? = null
    private var isDoneSetup = false
    private var isPaused = false
    private var outDated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializations()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = LoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        isPaused = false
        flow = null

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    clear()
                }
            }
            true
        }

        binding.rvSchools.apply {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(activity, 4)
        }

        val version = SpannableString("v${Global.VERSION}")
        version.setSpan(UnderlineSpan(), 0, version.length, 0)
        binding.version.text = version

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        if (Global.offset == null) getServerTime()
        setupData()
        emailListener()
        passwordListener()

        binding.btnForgot.setOnDebouncedClickListener {
            clear()
            forgot()
        }

        binding.btnSchool.setOnClickListener {
            binding.rvSchools.adapter = SchoolsAdapter(schools, this@Login)
            binding.cancel.visibility = View.VISIBLE
            binding.schools.visibility = View.VISIBLE
            binding.loginLayout.visibility = View.GONE
        }

        binding.cancel.setOnClickListener {
            binding.schools.visibility = View.GONE
            binding.loginLayout.visibility = View.VISIBLE
        }

        binding.btnLogin.setOnDebouncedClickListener {
            clear()
            login()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isConnected(requireContext())) isPaused = true
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
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        if (forgotDialog != null) if (forgotDialog!!.isAdded) forgotDialog!!.dismiss()
        if (resetDialog != null) if (resetDialog!!.isAdded) resetDialog!!.dismiss()
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            if (isPaused) {
                isPaused = false
                if (Global.offset == null) getServerTime()
                checkTimeChange()
                if (!isDoneSetup) setupData()
                else {
                    if (Global.app!!.version!! != Global.VERSION && !Global.VERSION.endsWith("DEBUG")) showRequireUpdate()
                    else if (auth.currentUser != null) {
                        binding.loading.message.visibility = View.VISIBLE
                        when (flow) {
                            "CHECK" -> setupProgress("Checking login")
                            "FETCH" -> setupProgress("Fetching data")
                            "CREATE" -> setupProgress("Creating account")
                            "SIGN" -> setupProgress("Logging in")
                            else -> {
                                setupProgress("Checking login")
                                email = auth.currentUser!!.email!!
                                checkLogin(email) { accountData ->
                                    checkAccount(accountData as Account)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNetworkLost() {
        requireActivity().runOnUiThread {
            isPaused = true
            binding.loading.container.visibility = View.GONE
            snackBar(view, "Network is not connected")
        }
    }

    override fun onSchoolClick(school: String) {
        Global.firebase = FirebaseApp.getInstance(school)
        setupSchool(school)
        sharedPreferences.edit {
            putString("School", school)
            apply()
        }
    }

    private fun initializations() {
        sharedPreferences = requireActivity().getSharedPreferences("Preferences", Context.MODE_PRIVATE)
        dbRef = Firebase.database.reference
        auth = Firebase.auth
        schools = arrayListOf()
    }

    private fun setupData() {
        if (isConnected(requireContext())) {
            binding.loading.message.visibility = View.GONE
            setupProgress("")
            userSchool = sharedPreferences.getString("School", null)
            getSchools {
                fetchApp {
                    initFirebase()
                    setupSchools()
                    isDoneSetup = true
                    binding.loading.container.visibility = View.GONE
                    if (Global.app!!.version!! != Global.VERSION && !Global.VERSION.endsWith("DEBUG")) showRequireUpdate()
                    else if (auth.currentUser != null) {
                        binding.loading.message.visibility = View.VISIBLE
                        when (flow) {
                            "CHECK" -> setupProgress("Checking login")
                            "FETCH" -> setupProgress("Fetching data")
                            "CREATE" -> setupProgress("Creating account")
                            "SIGN" -> setupProgress("Logging in")
                            else -> {
                                setupProgress("Checking login")
                                email = auth.currentUser!!.email!!
                                checkLogin(email) { accountData ->
                                    checkAccount(accountData as Account)
                                }
                            }
                        }
                    }
                }
            }
        } else snackBar(view, "Network is not connected")
    }

    private fun getSchools(onComplete: () -> Unit) {
        remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.fetchAndActivate().addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                schools.clear()
                val firebase = JSONObject(remoteConfig.getString("database"))
                firebase.keys().forEach { key ->
                    val school = Gson().fromJson(firebase[key].toString(), School::class.java)
                    schools.add(school)
                }
                onComplete()
            }
        }
    }

    private fun initFirebase() {
        if (Global.firebase == null) {
            schools.forEach { school ->
                val app = FirebaseOptions.Builder()
                    .setApplicationId(school.appID!!)
                    .setApiKey(school.apiKey!!)
                    .setDatabaseUrl(school.dbUrl)
                    .setStorageBucket(school.storage)
                    .build()
                if (FirebaseApp.getApps(requireContext()).find { it.name == school.name } == null)
                    FirebaseApp.initializeApp(requireContext(), app, school.name!!)
            }
            Global.firebase = if (userSchool != null) FirebaseApp.getInstance(userSchool!!)
            else null
        }
    }

    private fun setupSchools() {
        if (Global.firebase != null) setupSchool(userSchool!!)
        else {
            binding.rvSchools.adapter = SchoolsAdapter(schools, this@Login)
            binding.schools.visibility = View.VISIBLE
            binding.loginLayout.visibility = View.GONE
        }
    }

    private fun setupSchool(school: String) {
        dbRef = FirebaseDatabase.getInstance(FirebaseApp.getInstance(school)).reference
        auth = FirebaseAuth.getInstance(FirebaseApp.getInstance(school))
        binding.schools.visibility = View.GONE
        binding.loginLayout.visibility = View.VISIBLE
        schools.filter { item ->
            item.name!! == school
        }.forEach { item ->
            Picasso.get()
                .load(item.icon)
                .placeholder(R.drawable.empty)
                .into(binding.btnSchool)
        }
    }

    private fun fetchApp(onComplete: () -> Unit) {
        Firebase.database.reference.child("/App").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val app = snapshot.getValue(App::class.java)
                        if (app != null) Global.app = app
                    }
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    binding.loading.container.visibility = View.GONE
                }
            })
    }

    private fun showRequireUpdate() {
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
        var changes = ""
        Global.app!!.changes!!.split(".").forEach { changes += "\n\t-- $it" }
        confirmDialog = ConfirmDialog("UPDATE REQUIRED", "Your app is not the latest " +
                "(official) version. Update to version ${Global.app!!.version}.\n\nWhat's new in " +
                "${Global.app!!.version}:$changes", "UPDATE", null, true) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Global.app!!.link?.toUri()
            startActivity(Intent.createChooser(intent, "Open with"))
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun login() {
        if (Global.offset == null) getServerTime()
        if (Global.app == null) {
            fetchApp { if (Global.app!!.version!! != Global.VERSION && !Global.VERSION.endsWith("DEBUG")) showRequireUpdate() }
        } else {
            outDated = false
            val isEmail = binding.emailContainer.helperText == null &&
                    binding.emailContainer.error == null
            val isPass = binding.passwordContainer.helperText == null &&
                    binding.passwordContainer.error == null
            email = binding.emailEditText.text.toString()
            pass = binding.passwordEditText.text.toString()
            if (isEmail && isPass) {
                if (isConnected(requireContext())) {
                    binding.loading.message.visibility = View.VISIBLE
                    setupProgress("Checking account")
                    checkLogin(email) { accountData ->
                        if (accountData != null) {
                            if ((accountData as Account).pin == null && accountData.password != null)
                                createUser(accountData)
                            else {
                                setupProgress("Logging in")
                                signIn(accountData)
                            }
                        } else signIn(null)
                    }
                } else snackBar(view, "Network error. Please check your " +
                        "connection and try again")
            } else if (isEmail) {
                if (binding.passwordContainer.error != "Incorrect password")
                    binding.passwordContainer.error = "Minimum of 8 characters"
            } else if (isPass) {
                binding.emailContainer.error = "Invalid email address"
            } else {
                if (binding.passwordContainer.error != "Incorrect password")
                    binding.passwordContainer.error = "Minimum of 8 characters"
                binding.emailContainer.error = "Invalid email address"
            }
        }
    }

    private fun signIn(account: Account?) {
        flow = "SIGN"
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                binding.loading.container.visibility = View.GONE
                checkAccount(account)
            }
            .addOnFailureListener {
                binding.loading.container.visibility = View.GONE
                if (it is FirebaseAuthInvalidCredentialsException)
                    snackBar(view, "Login failed. " +
                            "Please double check your\ncredentials or contact your " +
                            "administrator then try again")
                else snackBar(view, "Error: ${it.message}")
                flow = null
            }
    }

    private fun checkAccount(account: Account?) {
        if (account != null) {
            setupProgress("Fetching data")
            fetchData(account.login!!, email.hashSHA256()) { userData ->
                binding.loading.container.visibility = View.GONE
                if (userData != null) {
                    Global.userData = userData
                    if (account.pin != null && account.password == null) {
                        Global.account = account
                        navigate(account.login)
                    } else setupAccount(account)
                }
            }
        } else discontinued()
    }

    private fun createUser(account: Account) {
        if (checkPass(account)) {
            setupProgress("Creating account")
            flow = "CREATE"
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    checkAccount(account)
                }
                .addOnFailureListener {
                    binding.loading.container.visibility = View.GONE
                    snackBar(view, "Error: ${it.message}")
                    flow = null
                }
        }
    }

    private fun setupAccount(account: Account) {
        if (account.pin == null && account.password != null) {
            requireNotice {
                setupProgress("Processing")
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.loading.container.visibility = View.GONE
                    getPin {
                        changePassword {
                            setupProgress("Processing")
                            Handler(Looper.getMainLooper()).postDelayed({
                                binding.loading.container.visibility = View.GONE
                                account.pin = pin!!.hashSHA256()
                                Global.account = account
                                navigate(account.login!!)
                            }, 500)
                        }
                    }
                }, 500)
            }
        } else if (account.pin == null) {
            getPin {
                setupProgress("Processing")
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.loading.container.visibility = View.GONE
                    account.pin = pin!!.hashSHA256()
                    Global.account = account
                    navigate(account.login!!)
                }, 500)
            }
        } else {
            changePassword {
                setupProgress("Processing")
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.loading.container.visibility = View.GONE
                    account.password = null
                    Global.account = account
                    navigate(account.login!!)
                }, 500)
            }
        }
    }

    private fun requireNotice(onComplete: () -> Unit) {
        confirmDialog = ConfirmDialog("SECURITY NOTICE", "To ensure the safety of " +
                "your account and prevent unauthorized access, a mandatory security measure is " +
                "required.\n\nACTION REQUIRED\n* Create a new PIN for your " +
                "account.\n* Change your password upon successful PIN creation." +
                "\n\nPlease be advised that all of these information should be kept PERSONAL for " +
                "your account security.", "Understood", null, true) {
            onComplete()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun getPin(onComplete: () -> Unit) {
        resetDialog = ResetDialog(email, auth, dbRef, "PIN", true) { newPIN ->
            pin = newPIN
            onComplete()
        }
        resetDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun changePassword(onComplete: () -> Unit) {
        resetDialog = ResetDialog(email, auth, dbRef, "PASSWORD", true) {
            dbRef.child("/Accounts/${emailToKey(email)}/password").removeValue()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        snackBar(view, "Password has been changed")
                        onComplete()
                    }
                }
        }
        resetDialog!!.show(childFragmentManager, "ResetDialog")
    }

    private fun fetchData(login: String, key: String, onComplete: (User?) -> Unit) {
        flow = "FETCH"
        dbRef.child("/Data/$login/$key").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) onComplete(snapshot.getValue(User::class.java)!!)
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete(null)
                    flow = null
                }
            })
    }

    private fun checkPass(account: Account): Boolean {
        if (account.password!! == pass.hashSHA256()) return true
        binding.loading.container.visibility = View.GONE
        snackBar(view, "Login failed. " +
                "Please double check your\ncredentials or contact your " +
                "administrator then try again")
        return false
    }

    private fun discontinued() {
        confirmDialog = ConfirmDialog("SORRY", "Sorry, but your HK Facilitator account " +
                "appears to have been discontinued by the administrator. If you think that this was " +
                "a mistake, you may try to contact the administrator for clarification. Otherwise, " +
                "you may now exit and close the application. It is advised that you uninstall this " +
                "application if you have no further use of it. Thank you and goodbye.", "Okay",
            null, true) {
            auth.currentUser!!.delete()
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun checkLogin(email: String?, onComplete: (Any?) -> Unit) {
        flow = "CHECK"
        dbRef.child("/Accounts/${emailToKey(email!!)}").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) onComplete(snapshot.getValue(Account::class.java)!!)
                    else onComplete(null)
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                    onComplete(null)
                    flow = null
                }
            })
    }

    private fun forgot() {
        forgotDialog = ForgotDialog(auth, dbRef) {
            snackBar(view, "Password reset link has been sent")
        }
        forgotDialog!!.show(childFragmentManager, "ForgotDialog")
    }

    private fun navigate(login: String) {
        resetLogin()
        sharedPreferences.edit {
            putString("Login", login)
            apply()
        }
        when (login) {
            "Admin" -> try { findNavController().navigate(LoginDirections.actionNavLoginToHomeAdmin())
            } catch (_: Exception) {}
            "Manager" -> try { findNavController().navigate(LoginDirections.actionNavLoginToHomeManager())
            } catch (_: Exception) {}
            "Faculty" -> try { findNavController().navigate(LoginDirections.actionNavLoginToHomeFaculty())
            } catch (_: Exception) {}
            "Facilitator" -> try { findNavController().navigate(LoginDirections.actionNavLoginToHomeFacilitator())
            } catch (_: Exception) {}
        }
    }

    private fun emailListener() {
        binding.emailEditText.setOnFocusChangeListener { _, focused ->
            val emailText = binding.emailEditText.text.toString()
            if (!focused) {
                if (emailText.isNotEmpty()) {
                    binding.emailContainer.helperText = null
                    binding.emailContainer.error = null
                    if (!Patterns.EMAIL_ADDRESS.matcher(emailText).matches())
                        binding.emailContainer.error = "Invalid email address"
                } else binding.emailContainer.helperText = "Required"
            } else binding.emailContainer.helperText = "Required"
        }
        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s!!.endsWith('@') && before < count) {
                    binding.emailEditText.setText("${binding.emailEditText.text}phinmaed.com")
                    binding.passwordContainer.requestFocus()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.emailContainer.setEndIconOnClickListener {
            binding.emailContainer.requestFocus()
            binding.emailEditText.text = null
        }
    }

    private fun passwordListener() {
        binding.passwordEditText.setOnFocusChangeListener { _, focused ->
            val passwordText = binding.passwordEditText.text.toString()
            if (!focused) {
                if (passwordText.isNotEmpty()) {
                    binding.passwordContainer.helperText = null
                    binding.passwordContainer.error = null
                    if (passwordText.length < 8) binding.passwordContainer.error =
                        "Minimum of 8 characters"
                } else binding.passwordContainer.helperText = "Required"
            } else binding.passwordContainer.helperText = "Required"
        }
    }

    private fun setupProgress(message: String) {
        binding.loading.message.text = message
        binding.loading.container.visibility = View.VISIBLE
    }

    private fun resetLogin() {
        binding.emailContainer.helperText = "Required"
        binding.emailEditText.text = null
        binding.passwordContainer.helperText = "Required"
        binding.passwordEditText.text = null
    }

    private fun clear() {
        binding.root.clearFocus()
        hideKeyboard()
    }
}