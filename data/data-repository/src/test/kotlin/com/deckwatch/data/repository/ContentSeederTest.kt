package com.deckwatch.data.repository

import androidx.test.core.app.ApplicationProvider
import com.deckwatch.core.testing.TestData
import com.deckwatch.data.seed.SeedDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Runs the real importer over the real bundled assets, so a seed document that stops parsing —
 * or an import that would wipe the user's own rows — fails here rather than on a phone.
 */
class ContentSeederTest : RepositoryTest() {

    private fun seeder() = ContentSeeder(
        seed = SeedDataSource(ApplicationProvider.getApplicationContext()),
        equipmentTypeDao = database.equipmentTypeDao(),
        taskDefinitionDao = database.taskDefinitionDao(),
        regulationCardDao = database.regulationCardDao(),
        roundTemplateDao = database.roundTemplateDao(),
        preferences = preferences,
        transaction = transaction,
    )

    @Test
    fun `the bundled content lands in the tables once and is not re-imported`() = runTest {
        val seeder = seeder()

        assertThat(seeder.seedIfNeeded()).isTrue()

        assertThat(database.regulationCardDao().observeAll().first()).isNotEmpty()
        assertThat(database.equipmentTypeDao().observeAll().first()).isNotEmpty()
        assertThat(database.taskDefinitionDao().observeAll().first()).isNotEmpty()
        assertThat(database.roundTemplateDao().observeAll().first()).isNotEmpty()
        assertThat(preferences.get().seededContentVersion).isGreaterThan(0)

        // Second start, same content version: nothing to do.
        assertThat(seeder.seedIfNeeded()).isFalse()
    }

    @Test
    fun `re-importing keeps the officer's own catalogue entries`() = runTest {
        val seeder = seeder()
        seeder.seedIfNeeded()
        val reference = SeededReferenceRepository(
            regulationCardDao = database.regulationCardDao(),
            equipmentTypeDao = database.equipmentTypeDao(),
            roundTemplateDao = database.roundTemplateDao(),
            userNoteDao = database.userNoteDao(),
            seed = SeedDataSource(ApplicationProvider.getApplicationContext()),
        )
        reference.upsertUserDefinedType(
            TestData.equipmentType(typeKey = "SHIPS_OWN_GADGET"),
        )

        // Pretend an older bundle was imported, so the next start re-imports.
        preferences.setSeededContentVersion(0)
        assertThat(seeder.seedIfNeeded()).isTrue()

        val stored = reference.getEquipmentType("SHIPS_OWN_GADGET")
        assertThat(stored).isNotNull()
        assertThat(stored?.isUserDefined).isTrue()
    }
}
