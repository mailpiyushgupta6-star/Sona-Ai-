package com.example.androidapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCommandParserTest {

    @Test
    fun testHinglishCommandSuffixes() {
        val ytb = AppCommandParser.parse("YouTube kholo")
        assertNotNull(ytb)
        assertEquals("youtube", ytb?.targetApp)
        assertTrue(ytb?.language is CommandLanguage.Hinglish)

        val chrome = AppCommandParser.parse("Chrome open karo")
        assertNotNull(chrome)
        assertEquals("chrome", chrome?.targetApp)
        assertTrue(chrome?.language is CommandLanguage.Hinglish)

        val wa = AppCommandParser.parse("WhatsApp chalao")
        assertNotNull(wa)
        assertEquals("whatsapp", wa?.targetApp)
        assertTrue(wa?.language is CommandLanguage.Hinglish)

        val cam = AppCommandParser.parse("Camera kholo")
        assertNotNull(cam)
        assertEquals("camera", cam?.targetApp)
        assertTrue(cam?.language is CommandLanguage.Hinglish)

        val sett = AppCommandParser.parse("Settings khol do")
        assertNotNull(sett)
        assertEquals("settings", sett?.targetApp)
        assertTrue(sett?.language is CommandLanguage.Hinglish)
    }

    @Test
    fun testHinglishVariationsAndPrefixes() {
        val ytbZara = AppCommandParser.parse("zara YouTube khol do")
        assertNotNull(ytbZara)
        assertEquals("youtube", ytbZara?.targetApp)

        val chrmPlease = AppCommandParser.parse("ek baar Chrome open kardo please")
        assertNotNull(chrmPlease)
        assertEquals("chrome", chrmPlease?.targetApp)

        val waPrefix = AppCommandParser.parse("chalao WhatsApp")
        assertNotNull(waPrefix)
        assertEquals("whatsapp", waPrefix?.targetApp)

        val camPrefix = AppCommandParser.parse("khol do Camera")
        assertNotNull(camPrefix)
        assertEquals("camera", camPrefix?.targetApp)

        val appSuffix = AppCommandParser.parse("YouTube app kholo")
        assertNotNull(appSuffix)
        assertEquals("youtube", appSuffix?.targetApp)
    }

    @Test
    fun testEnglishCommands() {
        val sett = AppCommandParser.parse("Open Settings")
        assertNotNull(sett)
        assertEquals("settings", sett?.targetApp)
        assertTrue(sett?.language is CommandLanguage.English)

        val chrm = AppCommandParser.parse("launch Chrome")
        assertNotNull(chrm)
        assertEquals("chrome", chrm?.targetApp)
        assertTrue(chrm?.language is CommandLanguage.English)

        val wa = AppCommandParser.parse("start WhatsApp")
        assertNotNull(wa)
        assertEquals("whatsapp", wa?.targetApp)
        assertTrue(wa?.language is CommandLanguage.English)

        val polite = AppCommandParser.parse("Please open Camera")
        assertNotNull(polite)
        assertEquals("camera", polite?.targetApp)
        assertTrue(polite?.language is CommandLanguage.English)

        val canYou = AppCommandParser.parse("Can you please open YouTube?")
        assertNotNull(canYou)
        assertEquals("youtube", canYou?.targetApp)
        assertTrue(canYou?.language is CommandLanguage.English)
    }

    @Test
    fun testNonCommandsIgnored() {
        assertNull(AppCommandParser.parse("What is open source?"))
        assertNull(AppCommandParser.parse("How to open a bank account?"))
        assertNull(AppCommandParser.parse("Account open kaise kare?"))
        assertNull(AppCommandParser.parse("Why do we open windows in summer?"))
        assertNull(AppCommandParser.parse("Tell me about Chrome OS features"))
        assertNull(AppCommandParser.parse("Camera me photo kaise lete hain?"))
        assertNull(AppCommandParser.parse("What is the capital of India?"))
        assertNull(AppCommandParser.parse("   "))
    }
}
