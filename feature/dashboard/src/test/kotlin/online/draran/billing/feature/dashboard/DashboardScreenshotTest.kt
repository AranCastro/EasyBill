package online.draran.billing.feature.dashboard

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the dashboard on the JVM and saves PNGs to
 * feature/dashboard/build/outputs/roborazzi for visual review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1500dp-xxhdpi")
class DashboardScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    private fun capture(name: String, state: DashboardUiState, mode: ThemeMode, height: Int) {
        composeRule.setContent {
            BillingTheme(themeMode = mode) {
                Box(Modifier.height(height.dp)) {
                    DashboardScreen(state = state, onNavigate = {}, isDarkTheme = mode == ThemeMode.DARK)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000) // let the chart animation finish
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun dashboardLight() = capture("dashboard_light", DashboardSampleData.filled, ThemeMode.LIGHT, 1420)

    @Test fun dashboardDark() = capture("dashboard_dark", DashboardSampleData.filled, ThemeMode.DARK, 1420)

    @Test fun dashboardEmpty() = capture("dashboard_empty", DashboardSampleData.empty, ThemeMode.LIGHT, 1180)
}
