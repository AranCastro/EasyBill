package online.draran.billing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.ui.LockScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LockScreenScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun lockScreenAsksOnceAndOnTap() {
        var asked = 0
        composeRule.setContent { BillingTheme { LockScreen(onUnlock = { asked++ }, onLeave = {}) } }
        composeRule.onNodeWithText("Modern Kallaa Petti is locked").assertIsDisplayed()
        assertEquals(1, asked) // the prompt opens by itself
        composeRule.onNodeWithText("Unlock").performClick()
        assertEquals(2, asked)
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/lock_screen.png")
    }
}
