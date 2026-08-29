package com.deckwatch.feature.notes

import app.cash.turbine.ReceiveTurbine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Replaces `Dispatchers.Main` so `viewModelScope` runs on the test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Every ViewModel here publishes through `stateIn`, so the first emission is the placeholder
 * initial value and the real state follows. Skip forward to the first state the test cares about
 * rather than hard-coding how many emissions the combine happens to produce.
 */
suspend fun <T> ReceiveTurbine<T>.awaitState(predicate: (T) -> Boolean): T {
    var last: T? = null
    repeat(MaxEmissionsScanned) {
        val item = awaitItem()
        last = item
        if (predicate(item)) return item
    }
    throw AssertionError("No state matched the predicate; last seen: $last")
}

private const val MaxEmissionsScanned = 20
