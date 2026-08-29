package com.deckwatch.feature.vessel

import com.deckwatch.feature.vessel.common.ImoStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The three states the IMO field can be in, and the rule that an invalid one still saves.
 * `9074729` is the check-digit-valid fixture used across the codebase (`TestData.vessel`).
 */
class ImoStatusTest {

    @Test
    fun `blank is not entered`() {
        assertThat(ImoStatus.of(null)).isEqualTo(ImoStatus.NOT_ENTERED)
        assertThat(ImoStatus.of("")).isEqualTo(ImoStatus.NOT_ENTERED)
        assertThat(ImoStatus.of("   ")).isEqualTo(ImoStatus.NOT_ENTERED)
    }

    @Test
    fun `seven digits with a matching check digit are valid`() {
        assertThat(ImoStatus.of("9074729")).isEqualTo(ImoStatus.VALID)
        assertThat(ImoStatus.of("IMO 9074729")).isEqualTo(ImoStatus.VALID)
    }

    @Test
    fun `a wrong check digit is invalid`() {
        assertThat(ImoStatus.of("9074728")).isEqualTo(ImoStatus.INVALID)
        assertThat(ImoStatus.of("1234567")).isEqualTo(ImoStatus.INVALID)
    }

    @Test
    fun `wrong length or non-digits are invalid`() {
        assertThat(ImoStatus.of("907472")).isEqualTo(ImoStatus.INVALID)
        assertThat(ImoStatus.of("90747299")).isEqualTo(ImoStatus.INVALID)
        assertThat(ImoStatus.of("90747A9")).isEqualTo(ImoStatus.INVALID)
    }

    @Test
    fun `only the invalid state raises the unverified badge`() {
        assertThat(ImoStatus.INVALID.needsWarning).isTrue()
        assertThat(ImoStatus.VALID.needsWarning).isFalse()
        assertThat(ImoStatus.NOT_ENTERED.needsWarning).isFalse()
    }
}
