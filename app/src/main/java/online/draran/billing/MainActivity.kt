package online.draran.billing

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.isDark
import online.draran.billing.core.model.ThemeMode
import online.draran.billing.core.model.UserPreferences
import online.draran.billing.ui.BillingApp
import online.draran.billing.ui.LockScreen
import online.draran.billing.ui.AppLock

@AndroidEntryPoint
// FragmentActivity: the fingerprint / screen-lock prompt (BiometricPrompt) needs it
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Keep the splash until the saved theme is loaded, so the app never flashes the wrong theme.
        // Also wait for the app lock setting, so a locked app never shows its data first.
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading || viewModel.unlocked == null }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? MainUiState.Ready
            val preferences = ready?.preferences ?: UserPreferences()
            val dark = preferences.themeMode.isDark()

            SystemBarsFollowTheme(dark)

            BillingTheme(themeMode = preferences.themeMode, dynamicColor = preferences.dynamicColor) {
                // The lock covers the app instead of replacing it, so a half-made bill is still there after unlocking
                val locked = preferences.appLock && viewModel.unlocked == false && AppLock.canAuthenticate(this)
                if (ready != null) Box(if (locked) Modifier.clearAndSetSemantics {} else Modifier) {
                    BillingApp(
                        onboarded = ready.onboarded,
                        businessType = ready.businessType,
                        isDarkTheme = dark,
                        onToggleTheme = { viewModel.setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
                        versionName = BuildConfig.VERSION_NAME,
                    )
                }
                if (locked) LockScreen(onUnlock = { AppLock.prompt(this, onSuccess = viewModel::unlock) }, onLeave = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onReturn(SystemClock.elapsedRealtime(), (viewModel.uiState.value as? MainUiState.Ready)?.preferences?.appLock == true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.backgroundAt = SystemClock.elapsedRealtime()
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
