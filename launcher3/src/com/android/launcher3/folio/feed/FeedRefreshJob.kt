/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Refreshes feeds in the background through Android's job scheduler. */
class FeedRefreshJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!FeedPrefs.isEnabled(this)) return false
        job = scope.launch {
            var failed = false
            try {
                FeedRepository.get(this@FeedRefreshJob).refresh()
            } catch (t: Throwable) {
                failed = true
            }
            jobFinished(params, failed)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    companion object {
        private const val JOB_ID = 0x464f4c49 // "FOLI"

        /** Schedules or cancels the periodic refresh to match the settings. */
        fun schedule(context: Context) {
            try {
                scheduleOrThrow(context)
            } catch (e: Exception) {
                android.util.Log.w("FolioFeeds", "Couldn't schedule feed refresh", e)
            }
        }

        private fun scheduleOrThrow(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val hours = FeedPrefs.refreshHours(context)
            if (!FeedPrefs.isEnabled(context) || hours <= 0) {
                js.cancel(JOB_ID)
                return
            }
            val info = JobInfo.Builder(JOB_ID,
                    ComponentName(context, FeedRefreshJob::class.java))
                .setPeriodic(hours * 60L * 60 * 1000, 30L * 60 * 1000)
                .setRequiredNetworkType(if (FeedPrefs.wifiOnly(context))
                    JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            val existing = js.getPendingJob(JOB_ID)
            if (existing == null || existing.intervalMillis != info.intervalMillis ||
                    existing.networkType != info.networkType) {
                js.schedule(info)
            }
        }
    }
}
