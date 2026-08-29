package com.deckwatch.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ImoNumberTest {

    @Test
    fun `valid IMO numbers pass`() {
        // 9074729 is the canonical example from the IMO scheme documentation.
        assertThat(ImoNumber.isValid("9074729")).isTrue()
        assertThat(ImoNumber.isValid("IMO 9074729")).isTrue()
    }

    @Test
    fun `invalid check digit fails`() {
        assertThat(ImoNumber.isValid("9074720")).isFalse()
    }

    @Test
    fun `wrong length or non-digits fail`() {
        assertThat(ImoNumber.isValid(null)).isFalse()
        assertThat(ImoNumber.isValid("")).isFalse()
        assertThat(ImoNumber.isValid("12345")).isFalse()
        assertThat(ImoNumber.isValid("ABCDEFG")).isFalse()
        assertThat(ImoNumber.isValid("12345678")).isFalse()
    }
}
