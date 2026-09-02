package com.deckwatch.feature.deckview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher so `viewModelScope` runs inside the test.
 *
 * Under Robolectric `Dispatchers.Main` posts to a looper the test thread never idles, so without
 * this a `stateIn` view model never emits and every assertion waits for a minute and then fails.
 *
 * The default is an [UnconfinedTestDispatcher] because that is what the screen tests need: the
 * state has to be there by the time the composable is measured. A test that cares about *ordering*
 * — the deck sweep writes a `Round` and then a `RoundItem`, and collapsing the two would hide a
 * regression — passes a [StandardTestDispatcher] instead and advances it by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
