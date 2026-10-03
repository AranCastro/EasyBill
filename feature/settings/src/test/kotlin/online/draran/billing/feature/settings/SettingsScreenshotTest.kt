package online.draran.billing.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.model.ThemeMode
import online.draran.billing.core.model.UserPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1100dp-xxhdpi")
class SettingsScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    private fun settings(name: String, mode: ThemeMode) {
        composeRule.setContent {
            BillingTheme(themeMode = mode) {
                Box(Modifier.height(1000.dp)) {
                    SettingsScreen(
                        preferences = UserPreferences(themeMode = mode),
                        versionName = "0.3.0",
                        dynamicColorSupported = true,
                        onThemeModeChange = {},
                        onDynamicColorChange = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun settingsLight() = settings("settings_light", ThemeMode.LIGHT)

    @Test fun settingsDark() = settings("settings_dark", ThemeMode.DARK)

    @Test fun moreLight() {
        composeRule.setContent {
            BillingTheme(themeMode = ThemeMode.LIGHT) {
                androidx.compose.material3.Surface {
                    Box(Modifier.height(760.dp)) { MoreScreen(onOpen = {}) }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/more_light.png")
    }
}
