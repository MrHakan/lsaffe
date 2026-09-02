package com.deckwatch.data.repository

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * First-run seeding and the content-version migration of §8.1 — against the **real** bundled
 * assets, which this module's unit tests can read (see `SeedAssetProbeTest`).
 */
@RunWith(RobolectricTestRunner::class)
class SeedInitializerTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var harness: RepositoryHarness

    @Before
    fun setUp() {
        harness = RepositoryHarness(temporaryFolder.newFolder(), TestData.referenceDay)
        seedVersionPrefs().edit { clear() }
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun `seeding is idempotent - a second run changes nothing`() = runTest {
        assertThat(harness.seedInitializer.ensureSeeded()).isTrue()

        val counts = contentCounts()
        assertThat(counts.values.all { it > 0 }).isTrue()
        assertThat(harness.seedInitializer.installedContentVersion)
            .isEqualTo(SeedInitializer.CONTENT_VERSION)

        // Same version, content present: nothing to do.
        assertThat(harness.seedInitializer.ensureSeeded()).isFalse()
        assertThat(contentCounts()).isEqualTo(counts)
    }

    @Test
    fun `a content-version bump re-seeds without touching user notes or user-defined rows`() =
        runTest {
            harness.seedInitializer.ensureSeeded()
            val counts = contentCounts()

            val note = TestData.userNote(
                title = "Chief's standing order",
                body = "Weekly round every Saturday 0900.",
            )
            harness.referenceRepository.upsertUserNote(note)
            val customType = TestData.equipmentType(
                typeKey = "USER_CUSTOM_MOORING_LIGHT",
                nameEn = "Mooring-station lantern",
            )
            harness.referenceRepository.upsertUserDefinedType(customType)

            // Simulate the next content release: the stored version no longer matches.
            seedVersionPrefs().edit {
                putInt(SeedInitializer.KEY_CONTENT_VERSION, SeedInitializer.CONTENT_VERSION - 1)
            }

            assertThat(harness.seedInitializer.ensureSeeded()).isTrue()

            // §8.1: the officer's own material survives the migration untouched.
            val notes = harness.referenceRepository.observeUserNotes().first()
            assertThat(notes.map { it.id }).containsExactly(note.id)
            assertThat(notes.single().body).isEqualTo(note.body)

            val stored = harness.referenceRepository.getEquipmentType(customType.typeKey)
            assertThat(stored?.nameEn).isEqualTo("Mooring-station lantern")
            assertThat(stored?.isUserDefined).isTrue()

            // Bundled content is upserted by key, so re-seeding does not multiply rows.
            assertThat(contentCounts()["task_definitions"]).isEqualTo(counts["task_definitions"])
            assertThat(contentCounts()["regulation_cards"]).isEqualTo(counts["regulation_cards"])
            assertThat(contentCounts()["round_templates"]).isEqualTo(counts["round_templates"])
            // …one extra equipment type: the one the user created.
            assertThat(contentCounts()["equipment_types"])
                .isEqualTo((counts["equipment_types"] ?: 0) + 1)
        }

    @Test
    fun `a bundled key never overwrites the row the user owns`() = runTest {
        val bundledKey = "FFE_PORTABLE_EXTINGUISHER"
        harness.referenceRepository.upsertUserDefinedType(
            TestData.equipmentType(typeKey = bundledKey, nameEn = "My own extinguisher entry"),
        )

        harness.seedInitializer.ensureSeeded()

        val stored = harness.referenceRepository.getEquipmentType(bundledKey)
        assertThat(stored?.nameEn).isEqualTo("My own extinguisher entry")
        assertThat(stored?.isUserDefined).isTrue()
    }

    @Test
    fun `an empty database is re-seeded even when the version says otherwise`() = runTest {
        seedVersionPrefs().edit {
            putInt(SeedInitializer.KEY_CONTENT_VERSION, SeedInitializer.CONTENT_VERSION)
        }

        assertThat(harness.seedInitializer.ensureSeeded()).isTrue()
        assertThat(harness.countOf("task_definitions")).isGreaterThan(0)
    }

    private fun seedVersionPrefs() = ApplicationProvider
        .getApplicationContext<Application>()
        .getSharedPreferences(SeedInitializer.PREFS_NAME, Context.MODE_PRIVATE)

    private fun contentCounts(): Map<String, Int> = listOf(
        "equipment_types",
        "task_definitions",
        "regulation_cards",
        "round_templates",
    ).associateWith { harness.countOf(it) }
}
