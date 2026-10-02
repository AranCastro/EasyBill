package online.draran.billing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the adaptive launcher icon the way launchers mask it (circle,
 * rounded square, squircle) to app/build/outputs/roborazzi/launcher_icon.png.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h330dp-xxhdpi")
class LauncherIconScreenshotTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun launcherIcon() {
        composeRule.setContent {
            Column(
                Modifier
                    .background(Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Large preview of the full icon
                MaskedIcon(CircleShape, 150.dp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    listOf(CircleShape, RoundedCornerShape(22), RoundedCornerShape(35)).forEach { shape ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MaskedIcon(shape, 60.dp)
                            Spacer(Modifier.height(6.dp))
                            Text("Kallaa Petti", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/launcher_icon.png")
    }

    /** Launchers show the centre 72/108 of the adaptive icon inside the mask. */
    @androidx.compose.runtime.Composable
    private fun MaskedIcon(shape: Shape, size: Dp) {
        Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
            val full = size * (108f / 72f)
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(full))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(full))
        }
    }
}
