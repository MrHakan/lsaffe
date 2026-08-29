package com.deckwatch.core.common

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class DatesTest {

    @Test
    fun `plusMonths clamps to end of month`() {
        val jan31 = LocalDate.of(2026, 1, 31).toEpochDay()
        val result = Dates.plusMonths(jan31, 1)
        assertThat(LocalDate.ofEpochDay(result)).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `nextAnniversary rolls forward past dates`() {
        val anchor = LocalDate.of(2020, 6, 15).toEpochDay()
        val from = LocalDate.of(2026, 8, 1).toEpochDay()
        val next = Dates.nextAnniversary(anchor, from)
        assertThat(LocalDate.ofEpochDay(next)).isEqualTo(LocalDate.of(2027, 6, 15))
    }

    @Test
    fun `nextAnniversary keeps same-year future date`() {
        val anchor = LocalDate.of(2020, 11, 20).toEpochDay()
        val from = LocalDate.of(2026, 8, 1).toEpochDay()
        val next = Dates.nextAnniversary(anchor, from)
        assertThat(LocalDate.ofEpochDay(next)).isEqualTo(LocalDate.of(2026, 11, 20))
    }
}
