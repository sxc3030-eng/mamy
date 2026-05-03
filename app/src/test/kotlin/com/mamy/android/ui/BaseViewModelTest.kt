package com.mamy.android.ui

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    @BeforeEach fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `asStateFlow emits initial then updates`() = runTest {
        val vm = object : BaseViewModel() {
            val state = flowOf(1, 2, 3).asStateFlow(0)
        }
        vm.state.test {
            assertEquals(0, awaitItem())     // initial
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            assertEquals(3, awaitItem())
        }
    }
}
