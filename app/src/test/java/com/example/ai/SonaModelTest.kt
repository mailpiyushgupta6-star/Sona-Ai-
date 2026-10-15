package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SonaModelTest {

    @Test
    fun `test fromId with generic IDs`() {
        assertEquals(SonaModel.BALANCED, SonaModel.fromId("model_balanced"))
        assertEquals(SonaModel.STRONG, SonaModel.fromId("model_strong"))
        assertEquals(SonaModel.FAST, SonaModel.fromId("model_fast"))
        assertEquals(SonaModel.LIVE, SonaModel.fromId("model_live"))
    }

    @Test
    fun `test fromId with legacy Gemini IDs`() {
        assertEquals(SonaModel.BALANCED, SonaModel.fromId("gemini-3.5-flash"))
        assertEquals(SonaModel.STRONG, SonaModel.fromId("gemini-3.1-pro-preview"))
        assertEquals(SonaModel.FAST, SonaModel.fromId("gemini-3.1-flash-lite-preview"))
        assertEquals(SonaModel.LIVE, SonaModel.fromId("gemini-3.8-live"))
    }

    @Test
    fun `test fromId with unknown ID defaults to BALANCED`() {
        assertEquals(SonaModel.BALANCED, SonaModel.fromId("unknown_model_id"))
        assertEquals(SonaModel.BALANCED, SonaModel.fromId(""))
    }

    @Test
    fun `test model capabilities`() {
        assertEquals(true, SonaModel.STRONG.supportsThinking)
        assertEquals(false, SonaModel.BALANCED.supportsThinking)
        assertEquals(true, SonaModel.LIVE.isLiveAudio)
        assertEquals(false, SonaModel.FAST.isLiveAudio)
    }
}
