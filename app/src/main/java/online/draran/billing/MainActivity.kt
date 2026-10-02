package online.draran.billing

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.isDark
import online.draran.billing.core.model.ThemeMode
import online.draran.billing.core.model.UserPreferences
import online.draran.billing.ui.BillingApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Keep the splash until the saved theme is loaded, so the app never flashes the wrong theme.
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences = (state as? MainUiState.Ready)?.preferences ?: UserPreferences()
            val dark = preferences.themeMode.isDark()

            SystemBarsFollowTheme(dark)

            BillingTheme(themeMode = preferences.themeMode, dynamicColor = preferences.dynamicColor) {
                BillingApp(
                    isDarkTheme = dark,
                    onToggleTheme = { viewModel.setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
                    versionName = BuildConfig.VERSION_NAME,
                )
            }
        }
    }

    /** Status and navigation bar icons follow the app theme, not only the phone's setting. */
    @Composable
    private fun SystemBarsFollowTheme(dark: Boolean) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
            )
            onDispose {}
        }
    }

    private companion object {
        // Same defaults as androidx.activity for 3-button navigation bars
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
