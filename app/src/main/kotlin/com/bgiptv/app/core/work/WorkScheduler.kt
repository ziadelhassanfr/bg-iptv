package com.bgiptv.app.core.work

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager = WorkManager.getInstance(context)

    fun scheduleEpgRefresh() {
        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(4, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "epg_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleLiveScorePolling() {
        val request = PeriodicWorkRequestBuilder<LiveScoreWorker>(1, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "live_scores",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelLiveScorePolling() {
        workManager.cancelUniqueWork("live_scores")
    }
}
