package online.draran.billing.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillColorsTest {

    @Test fun presetsAreReadableAsIs() {
        BillColors.PRESETS.forEach { (name, c) ->
            assertTrue("$name contrast ${BillColors.contrastWithWhite(c)}", BillColors.contrastWithWhite(c) >= 4.5)
            assertEquals(name, c, BillColors.readable(c))
        }
        assertEquals(BillColors.DEFAULT, Business().accent())
    }

    @Test fun lightColoursAreDarkenedKeepingHue() {
        val gold = 0xFFFFD700.toInt()
        val out = BillColors.readable(gold)
        assertTrue(BillColors.contrastWithWhite(out) >= 4.5)
        // Still a warm colour: red > green > blue
        assertTrue(BillColors.red(out) > BillColors.green(out) && BillColors.green(out) > BillColors.blue(out))
        assertTrue(BillColors.contrastWithWhite(Business(billColor = 0xFF7DD3FC.toInt()).accent()) >= 4.5)
    }

    @Test fun tintIsNearlyWhite() {
        val t = BillColors.tint(BillColors.DEFAULT)
        assertTrue(BillColors.luminance(t) > 0.85)
        assertEquals("#4F46E5", BillColors.hex(BillColors.DEFAULT))
    }

    @Test fun logoColoursAreRankedByArea() {
        val white = BillColors.rgb(255, 255, 255)
        val red = BillColors.rgb(220, 30, 40)
        val blue = BillColors.rgb(30, 80, 200)
        val pixels = IntArray(1000) { i -> if (i < 600) white else if (i < 850) red else blue }
        val colours = BillColors.fromLogo(pixels)
        assertEquals(2, colours.size)
        assertTrue("first should be red", BillColors.red(colours[0]) > BillColors.blue(colours[0]))
        assertTrue("second should be blue", BillColors.blue(colours[1]) > BillColors.red(colours[1]))
        colours.forEach { assertTrue(BillColors.contrastWithWhite(it) >= 4.5) }
    }

    @Test fun greyLogoGivesOneDarkColour() {
        val pixels = IntArray(400) { i -> if (i < 300) 0 /* transparent */ else BillColors.rgb(40, 40, 45) }
        val colours = BillColors.fromLogo(pixels)
        assertEquals(1, colours.size)
        assertTrue(BillColors.contrastWithWhite(colours[0]) >= 4.5)
        assertEquals(emptyList<Int>(), BillColors.fromLogo(IntArray(100) { BillColors.rgb(255, 255, 255) }))
    }
}
