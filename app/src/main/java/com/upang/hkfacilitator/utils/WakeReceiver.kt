package com.upang.hkfacilitator.utils

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import androidx.core.app.*
import com.upang.hkfacilitator.*
import com.upang.hkfacilitator.models.Global.isAppInForeground

class WakeReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra("title")
        val message = intent?.getStringExtra("message")
        if (title != null && message != null) showNotification(context, title, message)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "Notification")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(if (!isAppInForeground(context)) pendingIntent else null)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                return
            notify(101, notification.build())
        }
    }
}