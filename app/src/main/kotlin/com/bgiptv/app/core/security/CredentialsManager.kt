package com.bgiptv.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    val baseApiUrl: String get() = "$serverUrl/player_api.php"
    val streamBaseUrl: String get() = serverUrl
}

@Singleton
class CredentialsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "bgiptv_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasCredentials(): Boolean =
        prefs.getString(KEY_SERVER_URL, null) != null

    fun getCredentials(): XtreamCredentials? {
        val url = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val user = prefs.getString(KEY_USERNAME, null) ?: return null
        val pass = prefs.getString(KEY_PASSWORD, null) ?: return null
        return XtreamCredentials(url, user, pass)
    }

    fun saveCredentials(credentials: XtreamCredentials) {
        prefs.edit()
            .putString(KEY_SERVER_URL, credentials.serverUrl.trimEnd('/'))
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    // Parses full Xtream URL like:
    // http://host:8080/get.php?username=foo&password=bar&type=m3u_plus
    fun parseFullXtreamUrl(fullUrl: String): XtreamCredentials? {
        return try {
            val uri = android.net.Uri.parse(fullUrl)
            val serverUrl = "${uri.scheme}://${uri.host}:${uri.port}"
            val username = uri.getQueryParameter("username") ?: return null
            val password = uri.getQueryParameter("password") ?: return null
            XtreamCredentials(serverUrl, username, password)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
