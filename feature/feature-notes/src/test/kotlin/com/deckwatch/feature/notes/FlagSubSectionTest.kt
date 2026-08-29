package com.deckwatch.feature.notes

import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The rule that splits the FLAG section into RMI / Liberia / Panama sub-lists — §8.1. */
class FlagSubSectionTest {

    @Test
    fun `a single flagNotes key assigns the card`() {
        val card = flagCard(
            refKey = "FLAG_UNKNOWN_01",
            flagNotes = mapOf("RMI" to "RO surveyor must attend the 5-yearly overload test."),
        )

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.RMI)
    }

    @Test
    fun `full country names are accepted as flagNotes keys`() {
        val card = flagCard(refKey = "FLAG_X", flagNotes = mapOf("Liberia" to "Trained crew may service."))

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.LIBERIA)
    }

    @Test
    fun `a comparison card naming all three falls through to the refKey`() {
        val card = flagCard(
            refKey = "FLAG_PAN_LIFERAFT_EXTENSION",
            flagNotes = mapOf("RMI" to "a", "LIB" to "b", "PAN" to "c"),
        )

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.PANAMA)
    }

    @Test
    fun `the refKey token assigns the card when there are no flag notes`() {
        val card = flagCard(refKey = "FLAG_LIB_SAF_005")

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.LIBERIA)
    }

    @Test
    fun `RMI marine notice numbering is recognised`() {
        val card = flagCard(
            refKey = "FLAG_MN_2_011_37",
            citation = "MN 2-011-37",
            sourceRef = "Marine Notice 2-011-37, Life-Saving Appliances and Systems",
        )

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.RMI)
    }

    @Test
    fun `Liberia marine notice numbering is recognised`() {
        val card = flagCard(
            refKey = "FLAG_LIFEJACKETS",
            citation = "SAF-006",
            sourceRef = "Marine Notice SAF-006, Lifejackets",
        )

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.LIBERIA)
    }

    @Test
    fun `Panama circular numbering is recognised`() {
        val card = flagCard(
            refKey = "FLAG_MMC_281",
            citation = "MMC-281",
            sourceRef = "Merchant Marine Circular MMC-281",
        )

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.PANAMA)
    }

    @Test
    fun `an unattributable card is left unassigned rather than guessed`() {
        val card = flagCard(
            refKey = "FLAG_GENERAL_GUIDANCE",
            citation = "Flag guidance",
            sourceRef = "Company circular",
        )

        assertThat(card.flagSubSection()).isNull()
    }

    @Test
    fun `a card that contrasts two Administrations belongs to the one its refKey names`() {
        // Shape taken from the seeded content: FLAG_LIB_CO2_90 carries both LIB and RMI notes
        // because it states how Liberia's 90% rule differs from the RMI 95% rule.
        val card = flagCard(
            refKey = "FLAG_LIB_CO2_90",
            citation = "Liberia Marine Notice FIR-001",
            sourceRef = "Liberia Marine Notice FIR-001",
            flagNotes = mapOf("LIB" to "≥90% of nominal charge.", "RMI" to "≥95% of nominal charge."),
        )

        assertThat(card.flagSubSection()).isEqualTo(FlagSubSection.LIBERIA)
    }

    @Test
    fun `a caveat card that applies to all three Administrations is left for the catch-all group`() {
        // FLAG_NOTICE_REVISION_CAVEAT in the seed: it belongs to no single registry, so it is
        // listed under "Other flag notices" rather than arbitrarily filed under one of them.
        val card = flagCard(
            refKey = "FLAG_NOTICE_REVISION_CAVEAT",
            citation = "Flag Administration notices",
            sourceRef = "Flag Administration notices",
            flagNotes = mapOf("RMI" to "a", "LIB" to "b", "PAN" to "c"),
        )

        assertThat(card.flagSubSection()).isNull()
    }

    @Test
    fun `two-letter aliases are not accepted because they collide with other codes`() {
        // "LR" is Lloyd's Register, not Liberia; "MH" and "PA" are equally ambiguous.
        assertThat(FlagSubSection.fromCode("LR")).isNull()
        assertThat(FlagSubSection.fromCode("MH")).isNull()
        assertThat(FlagSubSection.fromCode("PA")).isNull()
    }

    @Test
    fun `a card citing two registries in one string is left unassigned`() {
        val card = flagCard(
            refKey = "FLAG_COMPARISON",
            citation = "SAF-005 vs MMC-258",
            sourceRef = "Comparison of authorised service provider regimes",
        )

        assertThat(card.flagSubSection()).isNull()
    }

    private fun flagCard(
        refKey: String,
        citation: String = "Flag notice",
        sourceRef: String = "Flag notice",
        flagNotes: Map<String, String> = emptyMap(),
    ) = TestData.regulationCard(
        refKey = refKey,
        section = RegulationSection.FLAG,
        citation = citation,
        sourceRef = sourceRef,
        flagNotes = flagNotes,
    )
}
