package com.bgiptv.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
)

data class UpdateResult(
    val hasUpdate: Boolean,
    val versionName: String = "",
    val apkUrl: String = "",
)

private const val VERSION_URL = "https://github.com/ziadelhassanfr/bg-iptv/releases/latest/download/version.json"

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VERSION_URL).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext UpdateResult(false)

            val body = response.body?.string() ?: return@withContext UpdateResult(false)
            val adapter = moshi.adapter(VersionInfo::class.java)
            val info = adapter.fromJson(body) ?: return@withContext UpdateResult(false)

            val currentCode = context.packageManager
                .getPackageInfo(context.packageName, 0).versionCode

            UpdateResult(
                hasUpdate = info.versionCode > currentCode,
                versionName = info.versionName,
                apkUrl = info.apkUrl,
            )
        } catch (e: Exception) {
            Log.d("UpdateChecker", "Check failed: ${e.message}")
            UpdateResult(false)
        }
    }

    fun openDownloadPage(apkUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
