package com.bgiptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bgiptv.app.core.security.CredentialsManager
import com.bgiptv.app.feature.browser.BrowserScreen
import com.bgiptv.app.feature.player.PlayerScreen
import com.bgiptv.app.feature.search.SearchScreen
import com.bgiptv.app.feature.setup.SetupScreen
import com.bgiptv.app.ui.theme.BgIptvTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

object Routes {
    const val SETUP = "setup"
    const val BROWSER = "browser"
    const val PLAYER = "player/{streamId}"
    const val SEARCH = "search"

    fun player(streamId: Int) = "player/$streamId"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var credentialsManager: CredentialsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BgIptvTheme {
                val navController = rememberNavController()
                val startDestination = if (credentialsManager.hasCredentials()) Routes.BROWSER else Routes.SETUP

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable(Routes.SETUP) {
                        SetupScreen(
                            onSetupComplete = {
                                navController.navigate(Routes.BROWSER) {
                                    popUpTo(Routes.SETUP) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Routes.BROWSER) {
                        BrowserScreen(
                            onPlayChannel = { streamId ->
                                navController.navigate(Routes.player(streamId))
                            },
                            onSearch = {
                                navController.navigate(Routes.SEARCH)
                            },
                        )
                    }

                    composable(
                        route = Routes.PLAYER,
                        arguments = listOf(navArgument("streamId") { type = NavType.IntType }),
                    ) { backStack ->
                        val streamId = backStack.arguments?.getInt("streamId") ?: return@composable
                        PlayerScreen(
                            streamId = streamId,
                            onOpenBrowser = { navController.popBackStack() },
                        )
                    }

                    composable(Routes.SEARCH) {
                        SearchScreen(
                            onSelectChannel = { streamId ->
                                navController.navigate(Routes.player(streamId)) {
                                    popUpTo(Routes.SEARCH) { inclusive = true }
                                }
                            },
                            onSelectEvent = { _ ->
                                // TODO: navigate to event detail / play best channel
                                navController.popBackStack()
                            },
                            onDismiss = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
