package com.bgiptv.app.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bgiptv.app.core.network.FootballDataClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LiveScoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val footballDataClient: FootballDataClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            footballDataClient.refreshLiveScores()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
