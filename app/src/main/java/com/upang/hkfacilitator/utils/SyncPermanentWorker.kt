package com.upang.hkfacilitator.utils

import android.content.*
import androidx.work.*
import com.google.firebase.*
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.upang.hkfacilitator.models.Global.getWeekRange
import com.upang.hkfacilitator.models.Global.todayDate
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.hashSHA256
import com.upang.hkfacilitator.models.Global.isAppInForeground
import com.upang.hkfacilitator.models.Global.isDayBefore
import com.upang.hkfacilitator.models.Global.syncWake
import com.upang.hkfacilitator.models.Global.todayDay
import com.upang.hkfacilitator.models.Global.todayHour
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class SyncPermanentWorker(
    val context: Context,
    workerParams: WorkerParameters
) :
    CoroutineWorker(context, workerParams)
{
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("Preferences", Context.MODE_PRIVATE)
    private var dbRef: DatabaseReference = Firebase.database.reference
    private val userSchool = sharedPreferences.getString("School", null)
    private val login = sharedPreferences.getString("Login", null)
    private val permanents = arrayListOf<Schedule>()
    private lateinit var firebase: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private var school: School? = null
    private var notify = false
    private var faculty = ""
    private var links = ""

    override suspend fun doWork(): Result {
        try {
            if (userSchool != null) {
                if (Global.offset == null) Global.offset = serverTime()
                val (fDatabase, fAuth) = setupReferences()
                dbRef = fDatabase
                auth = fAuth
                if (auth.currentUser != null) {
                    if ((login == "Faculty" || login == "Facilitator") && !isAppInForeground(context)) {
                        fetchPermanents()
                        syncPermanents()
                    }
                    if (login == "Admin" || login == "Manager") {
                        fetchExtras()
                        sendNotification()
                    }
                    syncWake(context)
                }
            }
            return Result.success()
        } catch (_: Exception) {
            return Result.failure()
        }
    }

    private suspend fun sendNotification() {
        if (notify) {
            val updates = hashMapOf<String, Any>()
            val today = todayDate(true)
            val id = today.substring(2).replace("-", "") +
                    "${todayHour().replace(":", "")}HELPFA"
            val title = "Need help for schedules"
            val message = "The following schedule owners need help with their facilitator " +
                    "requests:\n$faculty"
            val datetime = "${todayDate(false)} ${todayHour()}"
            val notify = Notifications(id, title, message, datetime, "SCHEDULES.$links")
            val adminSnapshot = dbRef.child("/Data/Admin").get().await()
            if (adminSnapshot.exists()) {
                for (user in adminSnapshot.children) {
                    val admin = user.getValue(User::class.java)
                    if (admin != null) {
                        if (!user.child("notifications").child(id).exists()) {
                            updates["/Admin/${admin.email!!.hashSHA256()}/notifications/$id"] = notify
                            updates["/Admin/${admin.email.hashSHA256()}/notified"] = true
                        }
                    }
                }
            }
            val managerSnapshot = dbRef.child("/Data/Manager").get().await()
            if (managerSnapshot.exists()) {
                for (user in managerSnapshot.children) {
                    val manager = user.getValue(User::class.java)
                    if (manager != null) {
                        if (!user.child("notifications").child(id).exists()) {
                            updates["/Manager/${manager.email!!.hashSHA256()}/notifications/$id"] = notify
                            updates["/Manager/${manager.email.hashSHA256()}/notified"] = true
                        }
                    }
                }
            }
            dbRef.child("/Data").updateChildren(updates).await()
        }
    }

    private suspend fun fetchExtras() {
        val snapshot = dbRef.child("/Extras").orderByChild("joined")
            .equalTo(0.0).get().await()
        if (snapshot.exists()) {
            for (sched in snapshot.children) {
                val schedule = sched.getValue(Schedule::class.java)!!
                if (schedule.suspended == null &&
                    isDayBefore(schedule.date!!, true, 2)) {
                    faculty += "${schedule.owner}\n"
                    links += "${schedule.id},${schedule.owner}."
                    notify = true
                }
            }
        }
    }

    private suspend fun fetchPermanents() {
        val snapshot = dbRef.child("/Permanents").orderByChild("joined")
            .startAt(1.0).get().await()
        permanents.clear()
        if (snapshot.exists()) for (sched in snapshot.children)
            permanents.add(sched.getValue(Schedule::class.java)!!)
    }

    private suspend fun syncPermanents() {
        val updates = hashMapOf<String, Any?>()
        updates.apply {
            permanents.forEach { sched ->
                val (start, end) = getWeekRange(sched.date!!)
                if (sched.completed != "$start - $end") {
                    if (sched.extended == true) put("/Permanents/${sched.id}/extended", false)
                    if (sched.date != todayDay()) {
                        put("/Permanents/${sched.id}/completed", "$start - $end")
                        sched.faci?.filter { (_, faci) -> faci.timeIn != null || faci.timeOut != null }?.forEach { (id, _) ->
                            put("/Permanents/${sched.id}/faci/$id/timeIn", null)
                            put("/Permanents/${sched.id}/faci/$id/timeOut", null)
                        }
                        if (sched.suspended == null) {
                            val schedule = sched.copy(
                                id = "${sched.id!!.substring(0, 4)}${start.substring(2)
                                    .replace("-", "")}${sched.id!!.substring(4)}",
                                date = "${start.substringAfter("-")}-${start
                                    .substringBefore("-")}",
                                completed = null,
                                suspended = null,
                                restrict = null)
                            put("/Completed/${schedule.id}", schedule)
                        }
                    }
                } else sched.faci?.filter { (_, faci) -> faci.marked == true }?.forEach { (id, _) ->
                    put("/Permanents/${sched.id}/faci/$id/marked", null)
                }
            }
        }
        dbRef.updateChildren(updates).await()
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

    private suspend fun serverTime(): Long {
        return suspendCancellableCoroutine { continuation ->
            val databaseRef = FirebaseDatabase.getInstance().reference
            databaseRef.child(".info/serverTimeOffset")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val offset = snapshot.getValue(Long::class.java) ?: 0L
                        continuation.resume(offset)
                    }
                    override fun onCancelled(error: DatabaseError) {}
            })
        }
    }
}