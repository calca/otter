package com.calmotter.app.bluetooth

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPauseBluetoothProtocolTest {

    @Test
    fun helloRoundTrips() {
        val line = formatHello("Pixel di Gianluigi")
        val parsed = parseGroupPauseBtMessage(line)
        assertEquals(GroupPauseBtMessage.Hello("Pixel di Gianluigi"), parsed)
    }

    @Test
    fun recipeRoundTrips() {
        val line = formatRecipe("aqbxxx5kPr4")
        val parsed = parseGroupPauseBtMessage(line)
        assertEquals(GroupPauseBtMessage.Recipe("aqbxxx5kPr4"), parsed)
    }

    @Test
    fun unknownLineParsesToNull() {
        assertNull(parseGroupPauseBtMessage("qualcosa d'altro"))
    }

    @Test
    fun displayNameNewlinesAreSanitized() {
        val line = formatHello("Nome\ncon newline")
        val parsed = parseGroupPauseBtMessage(line) as GroupPauseBtMessage.Hello
        assertTrue(!parsed.displayName.contains('\n'))
    }

    @Test
    fun writeThenReadLineRoundTrips() {
        val out = ByteArrayOutputStream()
        writeLine(out, "HELLO:test")
        val input = ByteArrayInputStream(out.toByteArray())
        assertEquals("HELLO:test", readLine(input))
    }

    @Test
    fun readLineReturnsNullOnEmptyStream() {
        val input = ByteArrayInputStream(ByteArray(0))
        assertNull(readLine(input))
    }

    @Test
    fun readLineHandlesMultipleLines() {
        val out = ByteArrayOutputStream()
        writeLine(out, "HELLO:a")
        writeLine(out, "RECIPE:b")
        val input = ByteArrayInputStream(out.toByteArray())
        assertEquals("HELLO:a", readLine(input))
        assertEquals("RECIPE:b", readLine(input))
    }

    @Test
    fun lobbyNameMarkerIsStableForSameTag() {
        assertEquals(groupPauseLobbyNameMarker(1234), groupPauseLobbyNameMarker(1234))
        assertTrue(groupPauseLobbyNameMarker(1234).startsWith("CalmOtter-"))
    }
}
