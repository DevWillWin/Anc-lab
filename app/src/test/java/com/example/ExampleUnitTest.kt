package com.example

import com.example.audio.AncEngine
import com.example.audio.AncSimulationMode
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testAncEngineInitialState() {
        val engine = AncEngine()
        assertFalse(engine.isAncActive)
        assertEquals(AncSimulationMode.ADAPTIVE_MASKING, engine.mode)
        assertEquals(0f, engine.estimatedReductionDb, 0.01f)
    }

    @Test
    fun testAncEngineToggleState() {
        val engine = AncEngine()
        engine.intensity = 0.8f
        assertEquals(0.8f, engine.intensity, 0.01f)
        engine.stopAnc()
        assertFalse(engine.isAncActive)
    }
}
