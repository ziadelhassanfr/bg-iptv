package com.bgiptv.app.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bgiptv.app.core.data.XtreamRepository
import com.bgiptv.app.core.security.CredentialsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val credentialsManager: CredentialsManager,
    private val xtreamRepository: XtreamRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val creds = credentialsManager.getCredentials() ?: return Result.success()
        return try {
            xtreamRepository.refreshEpg(creds)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
