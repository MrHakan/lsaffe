package com.deckwatch.feature.vessel

import app.cash.turbine.test
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.VesselType
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.common.ImoStatus
import com.deckwatch.feature.vessel.edit.VesselEditViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VesselEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val vessels = FakeVesselRepository(clock = { TestData.referenceMillis })

    private fun viewModel() = VesselEditViewModel(vessels)

    // ------------------------------------------------------------------ IMO validation

    @Test
    fun `imo status tracks the field live`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            assertThat(awaitItem().imoStatus).isEqualTo(ImoStatus.NOT_ENTERED)

            viewModel.onImoChange("907472")
            assertThat(awaitItem().imoStatus).isEqualTo(ImoStatus.INVALID)

            viewModel.onImoChange("9074729")
            assertThat(awaitItem().imoStatus).isEqualTo(ImoStatus.VALID)

            viewModel.onImoChange("9074728")
            assertThat(awaitItem().imoStatus).isEqualTo(ImoStatus.INVALID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `non-digits and overlong input never reach the field`() = runTest {
        val viewModel = viewModel()
        viewModel.onImoChange("IMO 9074729xx")
        assertThat(viewModel.state.value.imoNumber).isEqualTo("9074729")
    }

    // ------------------------------------------------------------------ save rules

    @Test
    fun `a blank name blocks the save and raises the inline error`() = runTest {
        val viewModel = viewModel()
        viewModel.onImoChange("9074729")

        assertThat(viewModel.state.value.canSave).isFalse()
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.showNameError).isTrue()
        assertThat(vessels.vessels.value).isEmpty()
    }

    @Test
    fun `an invalid imo number still saves and keeps the digits typed`() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange("MV Example")
        viewModel.onImoChange("9074720")

        assertThat(viewModel.state.value.imoStatus).isEqualTo(ImoStatus.INVALID)
        assertThat(viewModel.state.value.canSave).isTrue()

        viewModel.save()
        advanceUntilIdle()

        val saved = vessels.vessels.value.values.single()
        assertThat(saved.name).isEqualTo("MV Example")
        assertThat(saved.imoNumber).isEqualTo("9074720")
        assertThat(ImoStatus.of(saved.imoNumber).needsWarning).isTrue()
    }

    @Test
    fun `an empty imo number is stored as null rather than an empty string`() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange("MV Example")
        viewModel.save()
        advanceUntilIdle()

        assertThat(vessels.vessels.value.values.single().imoNumber).isNull()
    }

    @Test
    fun `the first vessel created becomes the active one`() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange("MV Example")
        viewModel.save()
        advanceUntilIdle()

        assertThat(vessels.observeActiveVessel().first()?.name).isEqualTo("MV Example")
    }

    @Test
    fun `a second vessel does not steal the active flag`() = runTest {
        vessels.upsertVessel(TestData.vessel(id = "vessel-active", name = "MV First", isActive = true))

        val viewModel = viewModel()
        viewModel.onNameChange("MV Second")
        viewModel.save()
        advanceUntilIdle()

        assertThat(vessels.observeActiveVessel().first()?.name).isEqualTo("MV First")
    }

    // ------------------------------------------------------------------ load and edit

    @Test
    fun `loading an existing vessel fills every field`() = runTest {
        val existing = TestData.vessel(
            id = "vessel-1",
            name = "MT Karadeniz",
            imoNumber = "9074729",
            flag = FlagState.LIBERIA,
            vesselType = VesselType.TANKER_OIL,
            grossTonnage = 45_000,
        )
        vessels.upsertVessel(existing)

        val viewModel = viewModel()
        viewModel.load("vessel-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.name).isEqualTo("MT Karadeniz")
        assertThat(state.imoStatus).isEqualTo(ImoStatus.VALID)
        assertThat(state.flag).isEqualTo(FlagState.LIBERIA)
        assertThat(state.vesselType).isEqualTo(VesselType.TANKER_OIL)
        assertThat(state.grossTonnage).isEqualTo("45000")
        assertThat(state.isNew).isFalse()
    }

    @Test
    fun `editing preserves the id, the created timestamp and the active flag`() = runTest {
        val existing = TestData.vessel(id = "vessel-1", name = "MV Example", isActive = true)
        vessels.upsertVessel(existing)

        val viewModel = viewModel()
        viewModel.load("vessel-1")
        advanceUntilIdle()
        viewModel.onNameChange("MV Example II")
        viewModel.save()
        advanceUntilIdle()

        val saved = vessels.vessels.value.values.single()
        assertThat(saved.id).isEqualTo("vessel-1")
        assertThat(saved.name).isEqualTo("MV Example II")
        assertThat(saved.createdAt).isEqualTo(existing.createdAt)
        assertThat(saved.isActive).isTrue()
    }

    @Test
    fun `flag other name is cleared when the flag stops being OTHER`() = runTest {
        val viewModel = viewModel()
        viewModel.onFlagChange(FlagState.OTHER)
        viewModel.onFlagOtherNameChange("Türkiye")
        assertThat(viewModel.state.value.flagOtherName).isEqualTo("Türkiye")

        viewModel.onFlagChange(FlagState.PANAMA)
        assertThat(viewModel.state.value.flagOtherName).isEmpty()
    }

    @Test
    fun `dates are stored as epoch-days exactly as given`() = runTest {
        val expiry = TestData.day(2027, 3, 15)
        val viewModel = viewModel()
        viewModel.onNameChange("MV Example")
        viewModel.onSecExpiryChange(expiry)
        viewModel.save()
        advanceUntilIdle()

        assertThat(vessels.vessels.value.values.single().safetyEquipmentCertExpiry).isEqualTo(expiry)
    }
}
