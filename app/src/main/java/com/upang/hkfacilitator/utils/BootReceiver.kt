package com.upang.hkfacilitator.utils

import android.content.*
import com.upang.hkfacilitator.models.Global.isChannelAvailable
import com.upang.hkfacilitator.models.Global.startNotificationWorker
import com.upang.hkfacilitator.models.Global.startSyncWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            startSyncWorker(context)
            if (isChannelAvailable(context)) startNotificationWorker(context)
        }
    }
}