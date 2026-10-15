package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiProviderTest {

    private val provider = GeminiProvider()

    @Test
    fun `test mapping generic IDs to Gemini IDs`() {
        assertEquals("gemini-3.5-flash", provider.mapGenericToGeminiId("model_balanced"))
        assertEquals("gemini-3.1-pro-preview", provider.mapGenericToGeminiId("model_strong"))
        assertEquals("gemini-3.1-flash-lite-preview", provider.mapGenericToGeminiId("model_fast"))
        assertEquals("gemini-3.8-live", provider.mapGenericToGeminiId("model_live"))
    }

    @Test
    fun `test fallback for unknown generic ID`() {
        assertEquals("gemini-3.5-flash", provider.mapGenericToGeminiId("unknown_id"))
    }
}
