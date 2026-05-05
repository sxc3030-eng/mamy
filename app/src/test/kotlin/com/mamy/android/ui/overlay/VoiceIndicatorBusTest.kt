package com.mamy.android.ui.overlay

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class VoiceIndicatorBusTest {

    @Test
    fun `default state is Idle`() = runTest {
        val bus = VoiceIndicatorBus()
        assertSame(VoiceIndicatorState.Idle, bus.state.first())
    }

    @Test
    fun `emit transitions through states`() = runTest {
        val bus = VoiceIndicatorBus()
        val transitions = listOf(
            VoiceIndicatorState.Listening,
            VoiceIndicatorState.Processing,
            VoiceIndicatorState.AwaitingSmsConfirm,
            VoiceIndicatorState.Done,
            VoiceIndicatorState.Idle,
        )
        for (s in transitions) {
            bus.emit(s)
            assertSame(s, bus.state.first())
        }
    }

    @Test
    fun `awaiting sms confirm is its own state distinct from processing`() = runTest {
        val bus = VoiceIndicatorBus()
        bus.emit(VoiceIndicatorState.Processing)
        val processing = bus.state.first()
        bus.emit(VoiceIndicatorState.AwaitingSmsConfirm)
        val awaiting = bus.state.first()
        assertEquals(VoiceIndicatorState.Processing, processing)
        assertEquals(VoiceIndicatorState.AwaitingSmsConfirm, awaiting)
    }
}
