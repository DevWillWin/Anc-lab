package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.audio.RealtimeInversionEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
    @Test
    fun testRealtimeInversionEngineInitialState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = RealtimeInversionEngine(context)
        assertFalse(engine.isAncActive)
        assertEquals(0.5f, engine.antiGain, 0.01f)
        assertTrue(engine.isLowPassEnabled)
    }

    @Test
    fun testRealtimeInversionEngineParameters() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = RealtimeInversionEngine(context)
        engine.antiGain = 0.7f
        engine.phaseTrimMs = 12.5f
        engine.isLowPassEnabled = false
        assertEquals(0.7f, engine.antiGain, 0.01f)
        assertEquals(12.5f, engine.phaseTrimMs, 0.01f)
        assertFalse(engine.isLowPassEnabled)
        engine.stop()
        assertFalse(engine.isAncActive)
    }
}
