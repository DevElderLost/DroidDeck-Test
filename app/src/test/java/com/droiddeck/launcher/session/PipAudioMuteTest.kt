package com.droiddeck.launcher.session

import org.junit.Assert.*
import org.junit.Test

class PipAudioMuteTest {
    private class Output(var state: Boolean) : PipAudioMute.Output {
        var fail = false
        var available = true
        var writes = 0
        override fun muted(): Boolean? = state.takeIf { available }
        override fun mute(value: Boolean): Boolean {
            writes++
            if (fail) return false
            state = value
            return true
        }
    }

    @Test fun leavingWithoutManualMuteDoesNotTouchAudio() {
        val output = Output(false)
        assertTrue(PipAudioMute(listOf(output)).restore())
        assertEquals(0, output.writes)
    }

    @Test fun restoresEachOutputsOriginalStateAfterMultipleToggles() {
        val pulse = Output(true)
        val relay = Output(false)
        val control = PipAudioMute(listOf(pulse, relay))
        assertTrue(control.setMuted(true))
        assertTrue(pulse.state && relay.state)
        assertTrue(control.setMuted(false))
        assertFalse(pulse.state || relay.state)
        assertTrue(control.restore())
        assertTrue(pulse.state)
        assertFalse(relay.state)
    }

    @Test fun partiallyFailedMuteRollsBackTheOtherOutput() {
        val pulse = Output(false)
        val relay = Output(false).apply { fail = true }
        assertFalse(PipAudioMute(listOf(pulse, relay)).setMuted(true))
        assertFalse(pulse.state)
        assertFalse(relay.state)
    }

    @Test fun unavailableOutputDoesNotMuteTheOtherOutput() {
        val pulse = Output(false)
        val relay = Output(false).apply { available = false }
        assertFalse(PipAudioMute(listOf(pulse, relay)).setMuted(true))
        assertEquals(0, pulse.writes)
    }

    @Test fun failedRestorationCanBeRetried() {
        val output = Output(false)
        val control = PipAudioMute(listOf(output))
        assertTrue(control.setMuted(true))
        output.fail = true
        assertFalse(control.restore())
        output.fail = false
        assertTrue(control.restore())
        assertFalse(output.state)
    }
}
