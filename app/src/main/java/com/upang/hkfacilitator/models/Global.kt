package com.upang.hkfacilitator.models

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.*
import android.os.*
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.*
import com.google.firebase.*
import com.google.firebase.database.*
import com.upang.hkfacilitator.utils.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object Global {
    const val VERSION = "1.3.7_DEBUG"   // CURRENT: 1.3.6
    var firebase: FirebaseApp? = null
    var tabPosition: Int = 0
    var account: Account? = null
    var userData: User? = null
    var profiledUser: User? = null
    var assignPrev: String? = null
    var isPINDisabled: Boolean = false
    var searching: Boolean = false
    var schedule: Schedule? = null
    var courses: ArrayList<String>? = null
    var facilitators: ArrayList<User>? = null
    var faculty: ArrayList<User>? = null
    var managers: ArrayList<User>? = null
    var isEditSchedule: Boolean = false
    var notificationPrev: String? = null
    var permanent: Schedule? = null
    var completed: ArrayList<Schedule>? = null
    var permanents: ArrayList<Schedule>? = null
    var joined: ArrayList<Schedule>? = null
    var extras: ArrayList<Schedule>? = null
    var notifications: ArrayList<Notifications>? = null
    var proofUri: MutableMap<String, Uri> = mutableMapOf()
    var appeals: MutableMap<String, String?> = mutableMapOf()
    var isExtension: Boolean = false
    var homeTab: Int? = null
    var offset: Long? = null
    var app: App? = null
    private var lastClickTime = 0L
    private var pauseTime: Long? = null
    private var pauseElapsedTime: Long? = null
    private const val THRESHOLD = 60 * 1000

    fun emailToKey(email: String): String {
        return email.replace(".", "!")
            .replace("#", "%")
            .replace("$", "^")
            .replace("[", "&")
            .replace("]", "*")
    }

    fun getZonedDateTime(): ZonedDateTime {
        return ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(
                System.currentTimeMillis() + (offset ?: 0L)),
            ZoneId.of("Asia/Manila"))
    }

    fun setPause() {
        pauseTime = System.currentTimeMillis()
        pauseElapsedTime = SystemClock.elapsedRealtime()
    }

    fun checkTimeChange() {
        if (pauseTime != null && pauseElapsedTime != null) {
            val resumeTime = System.currentTimeMillis()
            val resumeElapsedTime = SystemClock.elapsedRealtime()
            val expectedResumeTime = pauseTime!! + (resumeElapsedTime - pauseElapsedTime!!)
            if (abs(expectedResumeTime - resumeTime) > THRESHOLD) {
                offset = offset?.plus((expectedResumeTime - resumeTime))
                pauseTime = null
                pauseElapsedTime = null
            }
        }
    }

    fun getServerTime() {
        Firebase.database.reference.child(".info/serverTimeOffset")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val offSet = snapshot.getValue(Long::class.java) ?: 0L
                    offset = offSet
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun View.setOnDebouncedClickListener(interval: Long = 750, onClick: (View) -> Unit) {
        setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime >= interval) {
                lastClickTime = currentTime
                onClick(it)
            }
        }
    }

    fun isNotRestricted(sched: Schedule, course: String): Boolean {
        if (sched.subject != null) {
            val year = sched.subject!!.removeCourse().toInt()
            return if (sched.restrict == true) year < course.removeCourse().toInt() &&
                    course != sched.subject!!.removeYear()
            else if (course.removeCourse().toInt() > 3) year == course.removeCourse().toInt() &&
                    course != sched.subject!!.removeYear()
            else if (year > 10) true
            else year < course.removeCourse().toInt()
        } else return true
    }

    fun showTimePicker(context: Context, onComplete: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timePickerDialog = TimePickerDialog(context,
            { _: TimePicker, hr: Int, min: Int ->
                val padHour = "$hr".padStart(2, '0')
                val padMinute = "$min".padStart(2, '0')
                onComplete("$padHour:$padMinute")
            }, hour, minute, false)
        timePickerDialog.show()
    }

    fun showDatePicker(context: Context, searchBar: SearchView) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val datePicker = DatePickerDialog(context,
            { _: DatePicker, selectYear: Int, selectMonth: Int, selectDay: Int ->
                val padMonth = "${selectMonth + 1}".padStart(2, '0')
                val padDay = "$selectDay".padStart(2, '0')
                searchBar.setQuery("$padMonth-$padDay-$selectYear", true)
            }, year, month, day)
        datePicker.show()
    }

    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    private fun Context.hideKeyboard(view: View) {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun isDayBefore(date: String, isDate: Boolean, dayCount: Int): Boolean {
        val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
        val schedDate = LocalDate.parse(if (isDate) date
        else getZonedDateTime().with(TemporalAdjusters.nextOrSame(dayIndex(date)))
            .toLocalDate().format(formatter), formatter)
        val today = LocalDate.parse(todayDate(false), formatter)
        val diff = today.until(schedDate).days
        return diff in 0..dayCount
    }

    fun isDayAfter(date: String, isDate: Boolean, dayCount: Int): Boolean {
        val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
        val schedDate = LocalDate.parse(if (isDate) date
        else getZonedDateTime().with(TemporalAdjusters.previousOrSame(dayIndex(date)))
            .toLocalDate().format(formatter), formatter)
        val today = LocalDate.parse(todayDate(false), formatter)
        val diff = ChronoUnit.DAYS.between(schedDate, today)
        return diff in 0..dayCount
    }

    fun isActive(sched: Schedule, isExact: Boolean): Boolean {
        return (sched.date!!.contains(todayDate(false), true) ||
                sched.date!!.contains(todayDay(), true)) &&
                isNow(sched.time!!, isExact)
    }

    private fun isNow(target: String, isExact: Boolean): Boolean {
        val targets = target.split(" - ")
        val now = stringToTime(todayHour())
        val start = stringToTime(targets[0])
        val end = stringToTime(targets[1])
        val adjustedStart = if (!isExact) (start - 30 + 1440) % 1440 else start
        val adjustedEnd = if (!isExact) (end + 30) % 1440 else end
        if (adjustedEnd < adjustedStart) return now >= adjustedStart || now <= adjustedEnd
        return now in adjustedStart..adjustedEnd
    }

    fun todayHour(): String {
        val now = getZonedDateTime()
        val hour = now.hour.toString().padStart(2, '0')
        val minute = now.minute.toString().padStart(2, '0')
        return "$hour:$minute"
    }

    fun isWithinTimeRange(filter: String, target: String): Boolean {
        val filters = filter.split("-")
        val targets = target.split("-")
        val filterStart = stringToTime(filters[0].trim())
        val filterEnd = stringToTime(filters[1].trim())
        val targetStart = stringToTime(targets[0].trim())
        val targetEnd = stringToTime(targets[1].trim())
        return if (filterEnd >= filterStart) targetStart >= filterStart && targetEnd <= filterEnd
        else (targetStart >= filterStart || targetEnd <= filterEnd)
    }

    fun isLateTimeIn(schedStart: String, timeIn: String): Boolean {
        val startTime = stringToTime(schedStart)
        val timeInTime = stringToTime(timeIn)
        return timeInTime > startTime && timeInTime - startTime >= 30
    }

    fun isEarlyLate(schedStartEnd: String, timeInOut: String, isForStart: Boolean): Boolean {
        val schedTime = stringToTime(schedStartEnd)
        val timeInTime = stringToTime(timeInOut)
        return if (isForStart) schedTime > timeInTime
        else schedTime < timeInTime
    }

    fun calculateTimeRange(start: String, end: String): Float {
        val startTime = stringToTime(start)
        val endTime = stringToTime(end)
        return (endTime - startTime) / 60F
    }

    fun stringToTime(time: String): Float {
        val (timeHour, timeMinute) = time.split(":").map { it.toFloat() }
        return timeHour * 60 + timeMinute
    }

    fun todayDay(): String {
        val now = getZonedDateTime()
        return "${now.dayOfWeek}".lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    fun todayDate(isForSorting: Boolean): String {
        val now = getZonedDateTime()
        val year = now.year.toString()
        val month = now.monthValue.toString().padStart(2, '0')
        val day = now.dayOfMonth.toString().padStart(2, '0')
        return if (isForSorting) "$year-$month-$day"
        else "$month-$day-$year"
    }

    fun getWeekRange(date: String): Pair<String, String> {
        val schedDayIndex = dayIndex(date).ordinal + 1
        val todayDayIndex = dayIndex(todayDay()).ordinal + 1
        val today = getZonedDateTime()
        val indexDiff = (todayDayIndex - schedDayIndex + 7) % 7
        val startDate = today.minusDays(indexDiff.toLong())
        val start = dateToString(startDate)
        val end = dateToString(startDate.plusDays(6))
        return Pair(start, end)
    }

    private fun dateToString(date: ZonedDateTime): String {
        val month = date.monthValue.toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val year = date.year.toString()
        return "$year-$month-$day"
    }

    private fun dayIndex(day: String): DayOfWeek {
        return DayOfWeek.valueOf(day.uppercase())
    }

    fun timeRangeTo12(time: String?): String {
        val (start, end) = time!!.split(" - ")
        return "${timeTo12(start)} - ${timeTo12(end)}"
    }

    fun timeRangeTo24(time: String?): String {
        val (start, end) = time!!.split(" - ")
        return "${timeTo24(start)} - ${timeTo24(end)}"
    }

    private fun timeTo24(time: String?): String {
        return time?.let {
            val (hours, minutes, period) = it.split(":", " ")
            val hour = (hours.toInt() % 12 + if (period.equals("PM", ignoreCase = true)) 12 else 0)
                .toString().padStart(2, '0')
            "$hour:${minutes.padStart(2, '0')}"
        } ?: "No data"
    }

    fun timeTo12(time: String?): String {
        return time?.let {
            val (hours, minutes) = it.split(":").map(String::toInt)
            val period = if (hours < 12) "AM" else "PM"
            val hour = (if (hours % 12 == 0) 12 else hours % 12).toString().padStart(2, '0')
            "$hour:${minutes.toString().padStart(2, '0')} $period"
        } ?: "No data"
    }

    fun isFinished(schedule: Schedule): Boolean {
        val today = todayDate(true)
        return isDateInRange(today,
            schedule.completed!!.substringBefore(" - "),
            schedule.completed!!.substringAfter(" - "))
    }

    private fun isDateInRange(date: String, startDate: String, endDate: String): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val targetDate = sdf.parse(date)
        val start = sdf.parse(startDate)
        val end = sdf.parse(endDate)
        return targetDate != null && start != null && end != null &&
                (targetDate == start || targetDate == end ||
                        (targetDate.after(start) && targetDate.before(end)))
    }

    fun isPast(sched: Schedule, isExtra: Boolean): Boolean {
        val now = getZonedDateTime()
        val endDateTime = if (isExtra) {
            val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
            val localDate = LocalDate.parse(sched.date, formatter)
            ZonedDateTime.of(
                localDate,
                LocalTime.parse("${sched.time!!.split(" - ")[1]}:00"),
                ZoneId.of("Asia/Manila"))
        } else {
            val targetDay = now.with(TemporalAdjusters.previousOrSame(dayIndex(sched.date!!)))
            ZonedDateTime.of(
                targetDay.toLocalDate(),
                LocalTime.parse("${sched.time!!.split(" - ")[1]}:00"),
                ZoneId.of("Asia/Manila"))
        }
        val targetDateTime = endDateTime.plusMinutes(30)
        return now.isAfter(targetDateTime)
    }

    fun String.removeYear(): String = replace("\\d+$".toRegex(), "")

    fun String.removeCourse(): String = replace("\\D".toRegex(), "")

    fun String.hashSHA256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(this.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun bugReport(desc: String, rep: String?): String {
        val deviceModel = "Device: ${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "Android Version: ${Build.VERSION.RELEASE}"
        val appVersion = "App Version: $VERSION"
        val bug = Bug(deviceModel, androidVersion, appVersion, desc, rep)
        return "BUG REPORT/SUGGESTION\n\n${bug.deviceModel}\n" +
                "${bug.androidVersion}\n${bug.appVersion}\n\n" +
                "BUG/SUGGESTION DESCRIPTION\n${bug.bugDescription}\n\n" +
                "STEPS TO ACHIEVE\n${bug.toAchieve}"
    }

    fun isConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    fun syncWake(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WakeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms())
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12),
                    pendingIntent)
        } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12),
            pendingIntent)
    }

    fun notificationWake(context: Context, notification: Notifications) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WakeReceiver::class.java).apply {
            putExtra("title", notification.title)
            putExtra("message", notification.message)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    if (calendar.timeInMillis <= System.currentTimeMillis())
                        System.currentTimeMillis()
                    else calendar.timeInMillis, pendingIntent)
            }
        } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            if (calendar.timeInMillis <= System.currentTimeMillis())
                System.currentTimeMillis()
            else calendar.timeInMillis, pendingIntent)
    }

    fun startSyncWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<SyncPermanentWorker>(
            12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(12, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "SyncPermanentWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest)
    }

    fun startNotificationWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<NotificationCheckWorker>(
            3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(3, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "NotificationCheckWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest)
    }

    fun isChannelAvailable(context: Context): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return notificationManager.getNotificationChannel("Notification") != null
    }

    fun createNotificationChannel(context: Context) {
        val name = "Notification Channel"
        val description = "Notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("Notification", name, importance).apply {
            this.description = description
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun isAlarmDisabled(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()
    }

    fun isBatteryOptimized(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isNotificationDisabled(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == context.packageName) {
                return true
            }
        }
        return false
    }

    fun getUriBytes(context: Context, uri: Uri, maxSize: Int): ByteArray {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)
        options.inSampleSize = sizing(options)
        options.inJustDecodeBounds = false
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ByteArray(0)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return ByteArray(0)
        val format = if (options.outMimeType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outputStream = ByteArrayOutputStream()
        var quality = 100
        do {
            outputStream.reset()
            bitmap.compress(format, quality, outputStream)
            quality -= 5
        } while (outputStream.toByteArray().size > maxSize && quality > 0)
        return outputStream.toByteArray()
    }

    private fun sizing(options: BitmapFactory.Options): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > 1080 || width > 1080) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= 1080 && (halfWidth / inSampleSize) >= 1080)
                inSampleSize *= 2
        }
        return inSampleSize
    }
}