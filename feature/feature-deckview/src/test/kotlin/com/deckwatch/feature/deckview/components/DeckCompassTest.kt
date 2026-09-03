package com.deckwatch.feature.deckview.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The two pieces of the compass strip that are text rather than pixels. */
class DeckCompassTest {

    @Test
    fun `bearings are written in the three digits they are spoken in`() {
        assertThat(formatBearing(0)).isEqualTo("000")
        assertThat(formatBearing(5)).isEqualTo("005")
        assertThat(formatBearing(90)).isEqualTo("090")
        assertThat(formatBearing(355)).isEqualTo("355")
    }

    @Test
    fun `a bearing past a full turn is written as the bearing it is`() {
        assertThat(formatBearing(360)).isEqualTo("000")
        assertThat(formatBearing(370)).isEqualTo("010")
        assertThat(formatBearing(-10)).isEqualTo("350")
    }

    @Test
    fun `the spoken bearing names the quarter it falls in`() {
        assertThat(bearingSpeech(0f, CARDINALS)).isEqualTo("000 · BOW")
        assertThat(bearingSpeech(90f, CARDINALS)).isEqualTo("090 · STBD")
        assertThat(bearingSpeech(180f, CARDINALS)).isEqualTo("180 · STERN")
        assertThat(bearingSpeech(270f, CARDINALS)).isEqualTo("270 · PORT")
    }

    @Test
    fun `a bearing between marks is spoken against the nearer one`() {
        assertThat(bearingSpeech(44f, CARDINALS)).isEqualTo("044 · BOW")
        assertThat(bearingSpeech(46f, CARDINALS)).isEqualTo("046 · STBD")
        // Just short of a full turn is the bow again, not the port beam.
        assertThat(bearingSpeech(350f, CARDINALS)).isEqualTo("350 · BOW")
    }

    private companion object {
        val CARDINALS = listOf("BOW", "STBD", "STERN", "PORT")
    }
}
