package com.engfred.yvd.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.engfred.yvd.TAG_DOWNLOAD_JOB
import java.util.UUID

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "PAUSE_DOWNLOAD" && intent.action != "CANCEL_DOWNLOAD") return
        val wm = WorkManager.getInstance(context)
        val workManagerId = intent.getStringExtra("workManagerId")
        if (workManagerId != null) {
            runCatching { wm.cancelWorkById(UUID.fromString(workManagerId)) }
        } else {
            // Fallback: cancel all (should not normally happen)
            wm.cancelAllWorkByTag(TAG_DOWNLOAD_JOB)
        }
        // Room status update is handled by the worker's isStopped / finally path
    }
}