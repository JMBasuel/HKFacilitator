package com.upang.hkfacilitator.ui.admin

import android.annotation.SuppressLint
import android.net.Uri
import android.os.*
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import com.google.firebase.database.*
import com.google.gson.Gson
import com.upang.hkfacilitator.databinding.AdminAddAccBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.checkTimeChange
import com.upang.hkfacilitator.models.Global.emailToKey
import com.upang.hkfacilitator.models.Global.getServerTime
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.hideKeyboard
import com.upang.hkfacilitator.models.Global.isConnected
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.ui.popup.ConfirmDialog
import com.upang.hkfacilitator.utils.*
import org.apache.poi.ss.usermodel.*
import java.util.Locale
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

@SuppressLint("ClickableViewAccessibility, SetTextI18n")
class AdminAddAcc : Fragment(), ConnectionStateListener {

    private lateinit var binding: AdminAddAccBinding
    private lateinit var connectionStateMonitor: ConnectionStateMonitor
    private var confirmDialog: ConfirmDialog? = null
    private lateinit var courses: ArrayList<String>
    private lateinit var dbRef: DatabaseReference
    private var courseLevel: String? = null
    private lateinit var account: Account
    private var mCourse: String? = null
    private var course: String? = null
    private var suffix: String? = null
    private var gender: String? = null
    private var fName: String? = null
    private var mName: String? = null
    private var lName: String? = null
    private var login: String? = null
    private var email: String? = null
    private var hours: String? = null
    private var render: Float? = null
    private var name: String? = null
    private var pass: String? = null
    private var year: String? = null
    private var id: String? = null
    private var hk: String? = null
    private var isPaused = false
    private val regex = Regex("^[A-Za-z ]+$")
    private val callbackFalse = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {} }
    private val getExcel = registerForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) excelToJson(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializations()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AdminAddAccBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val executor = Executors.newSingleThreadExecutor()
        connectionStateMonitor = ConnectionStateMonitor(this, executor)

        isPaused = false

        binding.loading.progress.setIndicatorColor(
            "#15B34E".toColorInt(),
            "#D9BD2D".toColorInt())

        if (Global.offset == null) getServerTime()
        loadCourses()
        fNameListener()
        mNameListener()
        lNameListener()
        suffixListener()
        emailListener()
        passListener()
        idListener()
        hourListener()

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> clear()
            }
            true
        }

        binding.scroller.setOnDebouncedClickListener {
            clear()
        }

        binding.back.setOnDebouncedClickListener {
            findNavController().popBackStack()
        }

        binding.btnExcel.setOnDebouncedClickListener {
            getExcelFile()
        }

        binding.gender.setOnCheckedChangeListener { rg, id ->
            clear()
            genderListener(rg, id)
        }

        binding.login.setOnCheckedChangeListener { rg, id ->
            clear()
            loginListener(rg, id)
        }

        binding.btnAdd.setOnDebouncedClickListener {
            clear()
            binding.scroller.visibility = View.GONE
            addAccount()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isConnected(requireContext())) {
            isPaused = true
            setupProgress("Waiting for connection")
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
        if (confirmDialog != null) if (confirmDialog!!.isAdded) confirmDialog!!.dismiss()
    }

    override fun onNetworkAvailable() {
        requireActivity().runOnUiThread {
            if (isPaused) {
                isPaused = false
                endProgress()
                if (Global.offset == null) getServerTime()
                checkTimeChange()
            }
        }
    }

    override fun onNetworkLost() {
        requireActivity().runOnUiThread {
            isPaused = true
            setupProgress("Waiting for connection")
            if (isConnected(requireContext())) endProgress()
        }
    }

    private fun initializations() {
        account = Global.account!!
        dbRef = FirebaseDatabase.getInstance(Global.firebase!!).reference
        courses = arrayListOf()
    }

    private fun loadCourses() {
        if (Global.courses == null) {
            dbRef.child("/Courses").addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        courses.clear()
                        if (snapshot.exists()) for (course in snapshot.children)
                            courses.add(course.value as String)
                        Global.courses = courses
                        setupCourses()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        snackBar(view, "Error: ${error.message}")
                    }
                })
        } else setupCourses()
    }

    private fun setupCourses() {
        val courseCode = Global.courses!!.filter { it.startsWith('0') }.map { it.substringAfter('.').substringBeforeLast('.') }
        val courseFull = Global.courses!!.filter { it.startsWith('0') }.map { it.substringAfterLast('.') }
        val codeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, courseCode)
        codeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val fullAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, courseFull)
        fullAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.course.adapter = codeAdapter
        binding.mCourse.adapter = fullAdapter
    }

    private fun registerUser() {
        setupProgress("Registering account")
        val account = Account(login, pass!!.hashSHA256())
        val user = User(email, name, gender, id, courseLevel, hk, hours?.toFloat(),
            render, if (login == "Facilitator") 3 else null)
        val updates = hashMapOf(
            "/Accounts/${emailToKey(email!!)}" to account,
            "/Data/$login/${email!!.hashSHA256()}" to user)
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                snackBar(view, "Account has been successfully registered")
                endProgress()
                clearForm()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun addAccount() {
        readData()
        if (checkFName(fName!!) && checkMName(mName!!) && checkLName(lName!!)
            && checkSuffix(suffix!!) && checkEmail(email!!)
            && checkPass(pass!!) && checkRadios(gender, login)) {
            if (mName!!.isNotEmpty()) mName = "$mName "
            if (suffix!!.isNotEmpty()) suffix = " $suffix"
            name = "$fName $mName$lName$suffix"
            if (login.equals("Facilitator")) {
                courseLevel = "$course$year"
                if (hours!!.isEmpty()) hours = "0"
                if (!checkHours(hours!!)) return
                if (!checkID(id!!)) return
                when (hk) {
                    "HK25" -> render = 45F
                    "HK50" -> render = 90F
                    "HK75" -> render = 120F
                    "HK100" -> render = 150F
                }
            } else if (login.equals("Manager")) {
                courseLevel = Global.courses!!.filter { it.contains(mCourse!!) }
                    .map { it.substringAfter('.').substringBeforeLast('.') }[0]
                id = null
                hk = null
                hours = null
            } else {
                id = null
                courseLevel = null
                mCourse = null
                hk = null
                hours = null
            }
            setupProgress("Checking account")
            checkUser { level, found ->
                endProgress()
                if (!found) {
                    val details = if (login.equals("Facilitator")) {
                        "Name: $name\nGender: $gender\nLevel: " +
                                "$login\nEmail: $email\nPassword: $pass\nID: " +
                                "$id\nCourse: $courseLevel\nHK: $hk\nHours: $hours"
                    } else if (login.equals("Manager")) {
                        "Name: $name\nGender: $gender\nLevel: " +
                                "$login\nEmail: $email\nPassword: $pass\nCourse: $mCourse"
                    } else "Name: $name\nGender: $gender\nLevel: $login" +
                            "\nEmail: $email\nPassword: $pass"
                    confirmDialog = ConfirmDialog("REGISTER", "Please confirm the following " +
                            "details:\n\n$details", "Confirm", "Cancel", Global.isPINDisabled) {
                        registerUser()
                    }
                    confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
                } else {
                    snackBar(view, "This account is already registered as $level")
                    clearForm()
                }
            }
        } else {
            checkMName(mName!!)
            checkLName(lName!!)
            checkSuffix(suffix!!)
            checkEmail(email!!)
            checkPass(pass!!)
            checkRadios(gender, login)
            if (login == "Facilitator") {
                checkID(id!!)
                checkHours(hours!!)
            }
        }
    }

    private fun checkUser(onComplete: (String?, Boolean) -> Unit) {
        dbRef.child("/Accounts/${emailToKey(email!!)}").addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val account = snapshot.getValue(Account::class.java)!!
                        onComplete(account.login, true)
                    } else onComplete(null, false)
                }
                override fun onCancelled(error: DatabaseError) {
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun readData() {
        fName = binding.first.text.toString().trim()
        mName = binding.middle.text.toString().trim()
        lName = binding.last.text.toString().trim()
        suffix = binding.suffix.text.toString().trim()
        email = binding.email.text.toString().trim()
        pass = binding.password.text.toString().trim()
        id = binding.studentId.text.toString().trim()
        course = binding.course.selectedItem.toString()
        year = binding.year.selectedItem.toString()
        hk = binding.scholarship.selectedItem.toString()
        hours = binding.hours.text.toString().trim()
        mCourse = binding.mCourse.selectedItem.toString()
    }

    private fun clearForm() {
        binding.first.text = null
        binding.middle.text = null
        binding.last.text = null
        binding.suffix.text = null
        binding.gender.clearCheck()
        binding.login.clearCheck()
        binding.email.text = null
        binding.password.text = null
        binding.studentId.text = null
        binding.course.setSelection(0)
        binding.year.setSelection(0)
        binding.scholarship.setSelection(0)
        binding.mCourse.setSelection(0)
        binding.hours.text = null
        name = null
        fName = null
        mName = null
        lName = null
        suffix = null
        email = null
        pass = null
        id = null
        mCourse = null
        courseLevel = null
        course = null
        year = null
        hk = null
        hours = null
    }

    private fun getExcelFile() {
        confirmDialog = ConfirmDialog("UPLOAD EXCEL", "You are about to upload user " +
                "data from an Excel file. Please ensure that the Excel workbook STRICTLY follow these " +
                "conditions:\n\nWorkbook may contain more than one sheet and each sheet must be named as " +
                "the following account types:\n\nADMIN | MANAGER | FACULTY | FACILITATOR\n\nSheet column names " +
                "must ONLY consist (if applies) of these headers:\n\nEMAIL | NAME | GENDER | PASSWORD > (applies " +
                "to all)\nCOURSE* > (only MANAGER & FACILITATOR)\nID | HK | RENDER** | HOURS*** > (only FACILITATOR)" +
                "\n\n  * Code (ie.BSA2), omit year level for MANAGER\n ** Required duty hours for HK " +
                "level\n*** Advanced duty hours, 0 if none\n\nNOTE: SHEET AND COLUMN NAMES MUST " +
                "BE EXACTLY AS STATED ABOVE TO AVOID DATABASE ERRORS", "Select",
            "Cancel", Global.isPINDisabled) {
            getExcel.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun upload(accounts: Map<String, Any>, users: Map<String, Map<String, Any>>) {
        setupProgress("Uploading data")
        val updates = hashMapOf<String, Any>()
        updates.apply {
            accounts.forEach { (key, value) ->
                put("/Accounts/$key", value)
            }
            users.forEach { (key1, value1) ->
                value1.forEach { (key2, value2) ->
                    put("/Data/$key1/$key2", value2)
                }
            }
        }
        val dataSize = Gson().toJson(updates).toByteArray(Charsets.UTF_8).size
        if (dataSize > 1000000) batchedUpload(updates)
        else executeUpload(updates)
    }

    private fun batchedUpload(updates: Map<String, Any>) {
        val entries = updates.entries.toList()
        val batches = entries.chunked(100) { chunk ->
            chunk.associate { it.key to it.value }
        }
        batches.forEach { batch ->
            executeUpload(batch)
        }
    }

    private fun executeUpload(updates: Map<String, Any>) {
        dbRef.updateChildren(updates)
            .addOnSuccessListener {
                snackBar(view, "Data has been uploaded")
                endProgress()
            }
            .addOnFailureListener {
                snackBar(view, "Error: ${it.message}")
                endProgress()
            }
    }

    private fun uploadJson(accounts: Map<String, Any>, users: Map<String, Map<String, Any>>) {
        confirmDialog = ConfirmDialog("CONFIRM DATA UPLOAD", "Please confirm data upload",
            "Upload", "Cancel", Global.isPINDisabled) {
            upload(accounts, users)
        }
        confirmDialog!!.show(childFragmentManager, "ConfirmDialog")
    }

    private fun excelToJson(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val accounts = mutableMapOf<String, Map<String, Any?>>()
            val users = mutableMapOf<String, Map<String, Any>>()
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val login = sheet.sheetName.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
                val headerRow = sheet.getRow(0)
                val headers = mutableListOf<String>()
                val userRowsData = mutableMapOf<String, Any>()
                for (cell in headerRow) headers.add(cell.stringCellValue)
                for (rowIndex in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex)
                    val accountRowData = mutableMapOf<String, Any?>()
                    val userRowData = mutableMapOf<String, Any?>()
                    var email = ""
                    for (cellIndex in 0 until row.lastCellNum) {
                        val cell = row.getCell(cellIndex)
                        val header = headers[cellIndex].lowercase()
                        val cellValue: Any? = when {
                            cell == null -> null
                            cell.cellType == CellType.BLANK -> null
                            cell.cellType == CellType.STRING -> cell.stringCellValue
                            cell.cellType == CellType.NUMERIC -> cell.numericCellValue
                            cell.cellType == CellType.BOOLEAN -> cell.booleanCellValue
                            else -> cell.toString()
                        }
                        if (header == "password") {
                            accountRowData[header] = (cellValue as String).hashSHA256()
                            accountRowData["login"] = login
                        } else {
                            if (header == "hours") userRowData["hrs"] = cellValue
                            else userRowData[header] = cellValue
                            if (cellIndex == row.lastCellNum - 2) userRowData["counter"] = 3
                        }
                        if (header == "email") email = row.getCell(cellIndex).stringCellValue
                    }
                    accounts[emailToKey(email)] = accountRowData
                    userRowsData[email.hashSHA256()] = userRowData
                }
                users[login] = userRowsData
            }
            uploadJson(accounts, users)
            inputStream?.close()
        } catch (e: Exception) {
            snackBar(view, "Error reading file: ${e.message}")
        }
    }

    private fun checkRadios(gender: String?, login: String?): Boolean {
        if (gender == null && login == null) {
            snackBar(view, "Please select a gender and a level")
            return false
        } else if (gender == null) {
            snackBar(view, "Please select a gender")
            return false
        } else if (login == null) {
            snackBar(view, "Please select a level")
            return false
        }
        return true
    }

    private fun hourListener() {
        binding.hours.setOnFocusChangeListener { _, focused ->
            val hours = binding.hours.text.toString()
            if (!focused) {
                binding.scroller.visibility = View.GONE
                checkHours(hours)
            } else {
                binding.scroller.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.form.scrollTo(0, binding.form.bottom)
                }, 5)
            }
        }
    }

    private fun checkHours(hrs: String): Boolean {
        if (hrs.isNotEmpty()) {
            if (hrs.matches(Regex("^-?\\d+$"))) {
                hk = binding.scholarship.selectedItem.toString()
                when (hk) {
                    "HK25" -> if (hrs.toInt() >= 45) {
                        binding.hours.error = "Have this facilitator rendered duties in advance?"
                        return true
                    }
                    "HK50" -> if (hrs.toInt() >= 90) {
                        binding.hours.error = "Have this facilitator rendered duties in advance?"
                        return true
                    }
                    "HK75" -> if (hrs.toInt() >= 120) {
                        binding.hours.error = "Have this facilitator rendered duties in advance?"
                        return true
                    }
                    "HK100" -> if (hrs.toInt() >= 150) {
                        binding.hours.error = "Have this facilitator rendered duties in advance?"
                        return true
                    }
                }
            } else {
                binding.hours.error = "Must be a valid number"
                return false
            }
        }
        return true
    }

    private fun idListener() {
        binding.studentId.setOnFocusChangeListener { _, focused ->
            val id = binding.studentId.text.toString()
            if (!focused) {
                binding.scroller.visibility = View.GONE
                checkID(id)
            } else {
                binding.scroller.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.form.smoothScrollTo(0, binding.form.bottom)
                }, 5)
            }
        }
    }

    private fun checkID(id: String): Boolean {
        if (id.isNotEmpty()) {
            val regex = Regex("^\\d{2}-\\d{4}-\\d{5,}$")
            if (!regex.matches(id)) {
                binding.studentId.error = "Should follow the format 00-0000-00000..."
                return false
            }
            return true
        } else {
            binding.studentId.error = "Required"
            return false
        }
    }

    private fun passListener() {
        binding.password.setOnFocusChangeListener { _, focused ->
            val pass = binding.password.text.toString()
            if (!focused) {
                binding.scroller.visibility = View.GONE
                checkPass(pass)
            } else {
                binding.scroller.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.form.scrollTo(0, binding.form.bottom)
                }, 5)
            }
        }
    }

    private fun checkPass(pass: String): Boolean {
        if (pass.isNotEmpty()) {
            if (pass.length < 8) {
                binding.password.error = "Minimum of 8 characters"
                return false
            }
            return true
        } else {
            binding.password.error = "Required"
            return false
        }
    }

    private fun emailListener() {
        binding.email.setOnFocusChangeListener { _, focused ->
            val email = binding.email.text.toString()
            if (!focused) checkEmail(email)
        }
    }

    private fun checkEmail(email: String): Boolean {
        if (email.isNotEmpty()) {
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.email.error = "Should follow the format ...@domain.com"
                return false
            }
            return true
        } else {
            binding.email.error = "Required"
            return false
        }
    }

    private fun suffixListener() {
        binding.suffix.setOnFocusChangeListener { _, focused ->
            val suffix = binding.suffix.text.toString()
            if (!focused) checkSuffix(suffix)
        }
    }

    private fun checkSuffix(suffix: String): Boolean {
        if (suffix.isNotEmpty()) {
            val regex1 = Regex("^[A-Za-z]{2}\\.$")
            val regex2 = Regex("^[A-Za-z]{3}$")
            if (!(regex1.matches(suffix) || regex2.matches(suffix))) {
                binding.suffix.error = "Should be in abbreviation " +
                        "form or roman numeral"
                return false
            }
            return true
        }
        return true
    }

    private fun lNameListener() {
        binding.last.setOnFocusChangeListener { _, focused ->
            val last = binding.last.text.toString().trim()
            if (!focused) {
                if (checkLName(last) && binding.first.text.isNotBlank()) {
                    val fir = binding.first.text.toString().trim().lowercase().substring(0, 2)
                    val mid = if (binding.middle.text.isNotBlank())
                        binding.middle.text.toString().trim().lowercase().substring(0, 2)
                    else binding.first.text.toString().trim().lowercase().substring(2, 4)
                    val las = last.lowercase().substring(0)
                    binding.email.setText("$fir$mid.$las.up@phinmaed.com")
                }
            }
        }
    }

    private fun checkLName(last: String): Boolean {
        if (last.isNotEmpty()) {
            if (!regex.matches(last)) {
                binding.last.error = "Must not contain " +
                        "numbers and/or symbols"
                return false
            }
            return true
        } else {
            binding.last.error = "Required"
            return false
        }
    }

    private fun mNameListener() {
        binding.middle.setOnFocusChangeListener { _, focused ->
            val middle = binding.middle.text.toString().trim()
            if (!focused) {
                if (checkMName(middle) && binding.first.text.isNotBlank() &&
                    binding.last.text.isNotBlank()) {
                    val fir = binding.first.text.toString().trim().lowercase().substring(0, 2)
                    val mid = if (middle.isNotBlank()) middle.lowercase().substring(0, 2)
                    else binding.first.text.toString().trim().lowercase().substring(2, 4)
                    val las = binding.last.text.toString().trim().lowercase().substring(0)
                    binding.email.setText("$fir$mid.$las.up@phinmaed.com")
                }
            }
        }
    }

    private fun checkMName(middle: String): Boolean {
        if (middle.isNotEmpty()) {
            if (!regex.matches(middle)) {
                binding.middle.error = "Must not contain " +
                        "numbers and/or symbols"
                return false
            }
            return true
        }
        return true
    }

    private fun fNameListener() {
        binding.first.setOnFocusChangeListener { _, focused ->
            val first = binding.first.text.toString().trim()
            if (!focused) {
                if (checkFName(first) && binding.last.text.isNotBlank()) {
                    val fir = first.lowercase().substring(0, 2)
                    val mid = if (binding.middle.text.isNotBlank())
                        binding.middle.text.toString().trim().lowercase().substring(0, 2)
                    else first.lowercase().substring(2, 4)
                    val las = binding.last.text.toString().trim().lowercase().substring(0)
                    binding.email.setText("$fir$mid.$las.up@phinmaed.com")
                }
            }
        }
    }

    private fun checkFName(first: String): Boolean {
        if (first.isNotEmpty()) {
            if (!regex.matches(first)) {
                binding.first.error = "Must not contain " +
                        "numbers and/or symbols"
                return false
            }
            return true
        } else {
            binding.first.error = "Required"
            return false
        }
    }

    private fun genderListener(radio: RadioGroup, id: Int) {
        gender = when (radio.indexOfChild(radio.findViewById(id))) {
            0 -> "Male"
            1 -> "Female"
            else -> null
        }
    }

    private fun loginListener(radio: RadioGroup, id: Int) {
        when (radio.indexOfChild(radio.findViewById(id))) {
            0 -> {
                login = "Facilitator"
                binding.mCourse.visibility = View.GONE
                binding.student.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.form.scrollTo(0, binding.form.bottom)
                }, 5)
            }
            1 -> {
                login = "Faculty"
                binding.student.visibility = View.GONE
                binding.mCourse.visibility = View.GONE
            }
            2 -> {
                login = "Manager"
                binding.student.visibility = View.GONE
                binding.mCourse.visibility = View.VISIBLE
            }
            3 -> {
                login = "Admin"
                binding.student.visibility = View.GONE
                binding.mCourse.visibility = View.GONE
            }
            else -> {
                login = null
                binding.student.visibility = View.GONE
                binding.mCourse.visibility = View.GONE
            }
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