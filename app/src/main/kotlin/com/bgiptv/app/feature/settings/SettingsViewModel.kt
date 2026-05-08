package com.bgiptv.app.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgiptv.app.core.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore("settings")

object SettingsKeys {
    val AUTO_HIDE_DELAY = intPreferencesKey("auto_hide_delay_s")
    val PRE_BUFFER_ENABLED = booleanPreferencesKey("pre_buffer_enabled")
    val HDR_MODE = stringPreferencesKey("hdr_mode")
    val AUDIO_PASSTHROUGH = booleanPreferencesKey("audio_passthrough")
    val HARDWARE_DECODE = booleanPreferencesKey("hardware_decode")
    val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    val PIN_CODE = stringPreferencesKey("pin_code")
    val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
    val FRAME_RATE_MATCH = booleanPreferencesKey("frame_rate_match")
    val TUNNELING_ENABLED = booleanPreferencesKey("tunneling_enabled")
}

data class SettingsUiState(
    val autoHideDelaySec: Int = 8,
    val preBufferEnabled: Boolean = true,
    val hdrMode: String = "auto",
    val audioPassthrough: Boolean = true,
    val hardwareDecode: Boolean = true,
    val pinEnabled: Boolean = false,
    val cacheSizeMb: Int = 500,
    val frameRateMatch: Boolean = true,
    val tunnelingEnabled: Boolean = true,
    val updateAvailable: String? = null,
    val updateUrl: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val _updateAvailable = kotlinx.coroutines.flow.MutableStateFlow<Pair<String?, String?>>(null to null)

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        context.dataStore.data,
        _updateAvailable,
    ) { prefs, (updateName, updateUrl) ->
        SettingsUiState(
            autoHideDelaySec = prefs[SettingsKeys.AUTO_HIDE_DELAY] ?: 8,
            preBufferEnabled = prefs[SettingsKeys.PRE_BUFFER_ENABLED] ?: true,
            hdrMode = prefs[SettingsKeys.HDR_MODE] ?: "auto",
            audioPassthrough = prefs[SettingsKeys.AUDIO_PASSTHROUGH] ?: true,
            hardwareDecode = prefs[SettingsKeys.HARDWARE_DECODE] ?: true,
            pinEnabled = prefs[SettingsKeys.PIN_ENABLED] ?: false,
            cacheSizeMb = prefs[SettingsKeys.CACHE_SIZE_MB] ?: 500,
            frameRateMatch = prefs[SettingsKeys.FRAME_RATE_MATCH] ?: true,
            tunnelingEnabled = prefs[SettingsKeys.TUNNELING_ENABLED] ?: true,
            updateAvailable = updateName,
            updateUrl = updateUrl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            val result = updateChecker.check()
            if (result.hasUpdate) {
                _updateAvailable.value = result.versionName to result.apkUrl
            }
        }
    }

    fun setAutoHideDelay(seconds: Int) = save { prefs ->
        prefs[SettingsKeys.AUTO_HIDE_DELAY] = seconds.coerceIn(3, 30)
    }

    fun setPreBufferEnabled(enabled: Boolean) = save { prefs ->
        prefs[SettingsKeys.PRE_BUFFER_ENABLED] = enabled
    }

    fun setHdrMode(mode: String) = save { prefs ->
        prefs[SettingsKeys.HDR_MODE] = mode
    }

    fun setAudioPassthrough(enabled: Boolean) = save { prefs ->
        prefs[SettingsKeys.AUDIO_PASSTHROUGH] = enabled
    }

    fun setHardwareDecode(enabled: Boolean) = save { prefs ->
        prefs[SettingsKeys.HARDWARE_DECODE] = enabled
    }

    fun setPinEnabled(enabled: Boolean) = save { prefs ->
        prefs[SettingsKeys.PIN_ENABLED] = enabled
    }

    fun setCacheSizeMb(mb: Int) = save { prefs ->
        prefs[SettingsKeys.CACHE_SIZE_MB] = mb.coerceIn(100, 2000)
    }

    fun setFrameRateMatch(enabled: Boolean) = save { prefs ->
        prefs[SettingsKeys.FRAME_RATE_MATCH] = enabled
    }

    fun setTunnelingEnabled(enabled: Boolean) = save { prefs ->
        prefs[SettingsKeys.TUNNELING_ENABLED] = enabled
    }

    fun installUpdate(apkUrl: String) {
        updateChecker.openDownloadPage(apkUrl)
    }

    private fun save(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch {
            context.dataStore.edit(block)
        }
    }
}
