package dev.qarasky.dotisland.util

import org.junit.Assert.*
import org.junit.Test

class LiveActivityParserTest {
    @Test fun englishAndRussianTrackingAreRecognized() {
        assertTrue(LiveActivityParser.isOrderTracking("Your order", "Out for delivery", false, 0))
        assertTrue(LiveActivityParser.isOrderTracking("Заказ принят", "Курьер в пути", false, 0))
        assertFalse(LiveActivityParser.isOrderTracking("Dodo", "Something new", false, 0))
    }
    @Test fun promotionsAreRejectedEvenWithOrderWords() {
        assertFalse(LiveActivityParser.isOrderTracking("Your order", "Discount coupon", true, 100))
        assertFalse(LiveActivityParser.isOrderTracking("Ваш заказ", "Промокод на скидку", true, 100))
    }
    @Test fun etaAndProgressAreNeverFabricated() {
        assertNull(LiveActivityParser.eta("Your order", "Preparing"))
        assertNull(LiveActivityParser.progress(0, 0))
        assertEquals("15–20 мин", LiveActivityParser.eta("Доставка", "Через 15–20 мин"))
        assertEquals(0.5f, LiveActivityParser.progress(50, 100)!!, 0f)
    }
}
