package online.draran.billing.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {
    @Test fun picksGreetingByHour() {
        assertEquals(Greeting.MORNING, Greeting.at(LocalTime.of(6, 0)))
        assertEquals(Greeting.MORNING, Greeting.at(LocalTime.of(11, 59)))
        assertEquals(Greeting.AFTERNOON, Greeting.at(LocalTime.of(12, 0)))
        assertEquals(Greeting.AFTERNOON, Greeting.at(LocalTime.of(16, 59)))
        assertEquals(Greeting.EVENING, Greeting.at(LocalTime.of(17, 0)))
        assertEquals(Greeting.EVENING, Greeting.at(LocalTime.of(23, 30)))
    }
}
