package online.draran.billing.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {
    @Test fun picksGreetingByHour() {
        assertEquals(Greeting.NIGHT, Greeting.at(LocalTime.MIDNIGHT))
        assertEquals(Greeting.NIGHT, Greeting.at(LocalTime.of(2, 30)))
        assertEquals(Greeting.NIGHT, Greeting.at(LocalTime.of(4, 59)))
        assertEquals(Greeting.MORNING, Greeting.at(LocalTime.of(5, 0)))
        assertEquals(Greeting.MORNING, Greeting.at(LocalTime.of(11, 59)))
        assertEquals(Greeting.AFTERNOON, Greeting.at(LocalTime.NOON))
        assertEquals(Greeting.AFTERNOON, Greeting.at(LocalTime.of(16, 59)))
        assertEquals(Greeting.EVENING, Greeting.at(LocalTime.of(17, 0)))
        assertEquals(Greeting.EVENING, Greeting.at(LocalTime.of(20, 59)))
        assertEquals(Greeting.NIGHT, Greeting.at(LocalTime.of(21, 0)))
        assertEquals(Greeting.NIGHT, Greeting.at(LocalTime.of(23, 59)))
    }
}
