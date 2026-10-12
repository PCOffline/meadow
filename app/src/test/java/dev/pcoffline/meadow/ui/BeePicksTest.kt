package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.enforcement.PhoneMaker
import dev.pcoffline.meadow.ui.requests.BeePicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeePicksTest {
    private val all = listOf("maya", "theo", "ana")

    @Test
    fun `everyone is asked unless some were picked`() {
        assertNull(BeePicks.toSend(null, all, emptySet()))
        assertEquals(listOf("theo"), BeePicks.toSend(listOf("theo"), all, emptySet()))
    }

    @Test
    fun `the last choice is offered again, without Bumblebees who left`() {
        assertEquals(listOf("maya"), BeePicks.effective(null, all, setOf("maya", "gone")))
        assertEquals(all, BeePicks.effective(null, all, setOf("gone")))
        assertEquals(all, BeePicks.effective(emptyList(), all, emptySet()))
    }

    @Test
    fun `phone makers are recognised by maker or brand`() {
        assertEquals(PhoneMaker.Brand.XIAOMI, PhoneMaker.brand("Xiaomi", "Redmi"))
        assertEquals(PhoneMaker.Brand.XIAOMI, PhoneMaker.brand("unknown", "POCO"))
        assertEquals(PhoneMaker.Brand.SAMSUNG, PhoneMaker.brand("samsung", "samsung"))
        assertEquals(PhoneMaker.Brand.ONEPLUS, PhoneMaker.brand("OnePlus", "OnePlus"))
        assertEquals(PhoneMaker.Brand.OTHER, PhoneMaker.brand("Google", "google"))
    }
}
