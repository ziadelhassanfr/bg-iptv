package com.bgiptv.app.feature.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgiptv.app.core.data.XtreamRepository
import com.bgiptv.app.core.security.CredentialsManager
import com.bgiptv.app.core.security.XtreamCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { WELCOME, QR_PAIRING, MANUAL_ENTRY, IMPORTING }

data class SetupUiState(
    val step: SetupStep = SetupStep.WELCOME,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val error: String? = null,
    val qrData: String? = null,
    val localUrl: String? = null,
    val importedChannels: Int = 0,
    val importedEpg: Int = 0,
    val importedVod: Int = 0,
    val isComplete: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsManager: CredentialsManager,
    private val xtreamRepository: XtreamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun startQrSetup() {
        viewModelScope.launch {
            val localIp = getLocalIpAddress(context) ?: "192.168.1.x"
            val localUrl = "http://$localIp:8181"
            // TODO: start NanoHTTPD server on port 8181
            // TODO: generate QR code bitmap
            _uiState.update { it.copy(
                step = SetupStep.QR_PAIRING,
                localUrl = localUrl,
            ) }
        }
    }

    fun startManualSetup() {
        _uiState.update { it.copy(step = SetupStep.MANUAL_ENTRY, error = null) }
    }

    fun cancelSetup() {
        _uiState.update { it.copy(step = SetupStep.WELCOME) }
    }

    fun onServerUrlChange(value: String) = _uiState.update { it.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun onPasteFullUrl(fullUrl: String) {
        val parsed = credentialsManager.parseFullXtreamUrl(fullUrl)
        if (parsed != null) {
            _uiState.update { it.copy(
                serverUrl = parsed.serverUrl,
                username = parsed.username,
                password = parsed.password,
                error = null,
            ) }
        } else {
            _uiState.update { it.copy(error = "URL non reconnue — vérifie le format") }
        }
    }

    fun confirmManualEntry() {
        val state = _uiState.value
        when {
            state.serverUrl.isBlank() -> _uiState.update { it.copy(error = "URL serveur manquante") }
            state.username.isBlank() -> _uiState.update { it.copy(error = "Identifiant manquant") }
            state.password.isBlank() -> _uiState.update { it.copy(error = "Mot de passe manquant") }
            else -> {
                val creds = XtreamCredentials(
                    serverUrl = state.serverUrl.trimEnd('/'),
                    username = state.username.trim(),
                    password = state.password.trim(),
                )
                credentialsManager.saveCredentials(creds)
                startImport(creds)
            }
        }
    }

    private fun startImport(credentials: XtreamCredentials) {
        _uiState.update { it.copy(step = SetupStep.IMPORTING) }
        viewModelScope.launch {
            xtreamRepository.importAll(credentials) { progress ->
                _uiState.update { it.copy(
                    importedChannels = progress.channels,
                    importedEpg = progress.epg,
                    importedVod = progress.vod,
                    isComplete = progress.isComplete,
                ) }
            }
        }
    }

    private fun getLocalIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
            )
        } catch (e: Exception) { null }
    }
}
