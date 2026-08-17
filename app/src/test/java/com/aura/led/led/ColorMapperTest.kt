package com.aura.led.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorMapperTest {

    @Test
    fun `converts lowercase hex to int`() {
        assertEquals(0x0AFF10, ColorMapper.hexToInt("#0aff10"))
    }

    @Test
    fun `converts uppercase hex to int`() {
        assertEquals(0xABCDEF, ColorMapper.hexToInt("#ABCDEF"))
    }

    @Test
    fun `rejects hex without hash`() {
        assertNull(ColorMapper.hexToInt("0aff10"))
    }

    @Test
    fun `rejects wrong length`() {
        assertNull(ColorMapper.hexToInt("#0aff1"))
        assertNull(ColorMapper.hexToInt("#0aff100"))
    }

    @Test
    fun `rejects non-hex characters`() {
        assertNull(ColorMapper.hexToInt("#0aff1g"))
    }

    @Test
    fun `formats int to hex with leading zeros`() {
        assertEquals("#0aff10", ColorMapper.intToHex(0x0AFF10))
        assertEquals("#0000ff", ColorMapper.intToHex(0x0000FF))
    }

    @Test
    fun `round trips hex to int and back`() {
        val value = 0x12AB34
        assertEquals(value, ColorMapper.hexToInt(ColorMapper.intToHex(value)))
    }
}
