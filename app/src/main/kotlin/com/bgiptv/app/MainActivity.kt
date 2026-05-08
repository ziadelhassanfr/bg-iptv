package com.bgiptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.bgiptv.app.core.security.CredentialsManager
import com.bgiptv.app.ui.theme.BgIptvTheme
import com.bgiptv.app.feature.browser.BrowserScreen
import com.bgiptv.app.feature.setup.SetupScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var credentialsManager: CredentialsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BgIptvTheme {
                if (credentialsManager.hasCredentials()) {
                    BrowserScreen()
                } else {
                    SetupScreen(
                        onSetupComplete = { recreate() }
                    )
                }
            }
        }
    }
}
