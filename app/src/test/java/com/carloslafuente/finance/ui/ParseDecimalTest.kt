package com.carloslafuente.finance.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ParseDecimalTest {
    @Test
    fun `comma and dot are both accepted as decimal separator`() {
        assertEquals(BigDecimal("1234.56"), parseDecimal("1234,56"))
        assertEquals(BigDecimal("1234.56"), parseDecimal(" 1234.56 "))
    }

    @Test
    fun `dots are thousands separators when a comma is present`() {
        assertEquals(BigDecimal("1234.56"), parseDecimal("1.234,56"))
    }

    @Test
    fun `text that is not a number is rejected`() {
        assertNull(parseDecimal("12,3,4"))
        assertNull(parseDecimal(""))
    }
}
