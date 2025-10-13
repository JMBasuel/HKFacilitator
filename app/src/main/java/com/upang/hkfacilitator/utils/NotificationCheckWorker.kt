package com.upang.hkfacilitator.utils

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import androidx.core.app.*
import androidx.work.*
import com.google.firebase.*
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.upang.hkfacilitator.*
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isAppInForeground
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.notificationWake
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.Global.todayDay
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class NotificationCheckWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("Preferences", Context.MODE_PRIVATE)
    private val userSchool = sharedPreferences.getString("School", null)
    private var dbRef: DatabaseReference = Firebase.database.reference
    private val login = sharedPreferences.getString("Login", null)
    private val notifications = arrayListOf<Notifications>()
    private val schedules = arrayListOf<Schedule>()
    private val permanent = arrayListOf<Schedule>()
    private val extras = arrayListOf<Schedule>()
    private lateinit var firebase: FirebaseApp
    private var hour = Calendar.HOUR_OF_DAY
    private lateinit var auth: FirebaseAuth
    private var school: School? = null
    private var index: Int = 0

    override suspend fun doWork(): Result {
        try {
            checkUpdates()
            if (userSchool != null) {
                val (fDatabase, fAuth) = setupReferences()
                dbRef = fDatabase
                auth = fAuth
                if (auth.currentUser != null) {
                    if (login == "Facilitator") checkActiveSchedules(false)
                    if (login == "Faculty") checkActiveSchedules(true)
                    checkNotifications()
                }
            }
            return Result.success()
        } catch (_: Exception) {
            return Result.failure()
        }
    }

    private suspend fun checkUpdates() {
        if (hour in 1..6 || hour in 18..23) {
            val appSnap = Firebase.database.reference.child("/App").get().await()
            if (appSnap.exists()) {
                val app = appSnap.getValue(App::class.java)
                if (app != null && app.version!! != Global.VERSION && !Global.VERSION.endsWith("DEBUG")) {
                    val title = "Update available"
                    val message = "Your app is not up-to-date. ${app.version} is now available. Please update as soon as possible to avoid problems."
                    val notify = Notifications(title = title, message = message)
                    showNotification(notify)
                }
            }
        }
    }

    private suspend fun checkActiveSchedules(isFaculty: Boolean) {
        fetchExtras(isFaculty)
        fetchPermanents(isFaculty)
        var toBeActive = 0
        var toBeActiveTimes = ""
        val updates = hashMapOf<String, Any>()
        updates.apply {
            schedules.forEach { sched ->
                if ((sched.date.equals(todayDate(false)) || sched.date.equals(todayDay())) &&
                    ((sched.date!!.contains('-') && !isPast(sched, true)) ||
                            (!sched.date!!.contains('-') && !isPast(sched, false)))) {
                    toBeActive += 1
                    toBeActiveTimes = if (toBeActive > 1) toBeActiveTimes + "& ${timeRangeTo12(sched.time)} "
                    else toBeActiveTimes + "${timeRangeTo12(sched.time)} "
                    val today = todayDate(true).replace("-", "")
                    updates["/${sched.id}/notified"] = if (sched.notified == null || today !in sched.notified) "$today${login!!.uppercase()}"
                    else "${sched.notified}${login!!.uppercase()}"
                }
            }
        }
        if (toBeActive > 0) {
            val title = "You have ${if (toBeActive > 1) "active schedules" else "an active schedule"} today"
            val message = "You have $toBeActive active schedule${if (toBeActive > 1) "s" else ""} today at $toBeActiveTimes." +
                    if (!isFaculty) " Please ensure that you always attend your schedules " +
                            "to avoid penalties." else ""
            val notify = Notifications(title = title, message = message)
            notificationWake(context, notify)
        }
        dbRef.child("/Permanents").updateChildren(updates).await()
    }

    private suspend fun fetchExtras(isFaculty: Boolean) {
        val snapshot = dbRef.child("/Extras").orderByChild("joined")
            .startAt(1.0).get().await()
        extras.clear()
        schedules.clear()
        if (snapshot.exists()) {
            for (sched in snapshot.children) {
                val schedule = sched.getValue(Schedule::class.java)!!
                val today = todayDate(true).replace("-", "")
                if (isFaculty && schedule.email.equals(auth.currentUser!!.email) &&
                    (schedule.notified == null || today !in schedule.notified ||
                            login!!.uppercase() !in schedule.notified)) extras.add(schedule)
                else if (schedule.faci!!.contains(auth.currentUser!!.email!!.hashSHA256()))
                    extras.add(schedule)
            }
        }
        schedules.addAll(permanent + extras)
    }

    private suspend fun fetchPermanents(isFaculty: Boolean) {
        val snapshot = dbRef.child("/Permanents").orderByChild("joined")
            .startAt(1.0).get().await()
        permanent.clear()
        schedules.clear()
        if (snapshot.exists()) {
            for (sched in snapshot.children) {
                val schedule = sched.getValue(Schedule::class.java)!!
                val today = todayDate(true).replace("-", "")
                if (schedule.suspended == null && ((isFaculty && schedule.email == auth.currentUser!!.email) ||
                            schedule.faci!!.contains(auth.currentUser!!.email!!.hashSHA256())) &&
                    (schedule.notified == null || today !in schedule.notified ||
                            login!!.uppercase() !in schedule.notified)) permanent.add(schedule)
            }
        }
        schedules.addAll(permanent + extras)
    }

    private suspend fun checkNotifications() {
        fetchNotifications()
        val updates = hashMapOf<String, Any>()
        updates.apply {
            notifications.forEach { notify ->
                showNotification(notify)
                updates["/${notify.id}/notified"] = true
            }
        }
        dbRef.child("/Data/$login/${auth.currentUser!!.email!!.hashSHA256()}/notifications").updateChildren(updates).await()
    }

    private fun showNotification(notify: Notifications) {
        index += 1
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "Notification")
            .setContentTitle(notify.title)
            .setContentText(notify.message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(if (!isAppInForeground(context)) pendingIntent else null)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                return
            notify(index, notification.build())
        }
    }

    private suspend fun fetchNotifications() {
        val snapshot = dbRef.child("/Data/$login/${auth.currentUser!!.email!!.hashSHA256()}/notifications")
            .get().await()
        notifications.clear()
        if (snapshot.exists()) {
            for (notify in snapshot.children) {
                val notification = notify.getValue(Notifications::class.java)
                if (notification != null && notification.notified == null && notification.read == null)
                    notifications.add(notification)
            }
        }
    }

    private suspend fun setupReferences(): Pair<DatabaseReference, FirebaseAuth> {
        val snapshot = Firebase.database.reference.child("/Database").get().await()
        if (snapshot.exists()) {
            for (item in snapshot.children) {
                school = item.getValue(School::class.java)
                if (school != null && school!!.name!! == userSchool) {
                    val app = FirebaseOptions.Builder()
                        .setApplicationId(school!!.appID!!)
                        .setApiKey(school!!.apiKey!!)
                        .setDatabaseUrl(school!!.dbUrl)
                        .build()
                    if (FirebaseApp.getApps(context).find { it.name == school!!.name } == null)
                        FirebaseApp.initializeApp(context, app, school!!.name!!)
                }
            }
        }
        firebase = FirebaseApp.getInstance(userSchool!!)
        return Pair(FirebaseDatabase.getInstance(firebase).reference,
            FirebaseAuth.getInstance(firebase))
    }
}