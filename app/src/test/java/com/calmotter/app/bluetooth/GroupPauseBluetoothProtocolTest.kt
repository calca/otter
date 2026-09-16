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

    @Test
    fun lobbyInfoRoundTrips() {
        val parsed = parseGroupPauseBtMessage(formatLobbyInfo("Pixel di Marco", 45))
        assertTrue(parsed is GroupPauseBtMessage.LobbyInfo)
        parsed as GroupPauseBtMessage.LobbyInfo
        assertEquals("Pixel di Marco", parsed.hostName)
        assertEquals(45, parsed.durationMinutes)
    }

    /**
     * Il separatore dentro al nome spezzerebbe il parsing come un newline
     * spezza il framing: va neutralizzato prima di finire sul filo.
     */
    @Test
    fun lobbyInfoSurvivesASeparatorInsideTheName() {
        val parsed = parseGroupPauseBtMessage(formatLobbyInfo("Marco|Rossi", 30))
        assertTrue(parsed is GroupPauseBtMessage.LobbyInfo)
        parsed as GroupPauseBtMessage.LobbyInfo
        assertEquals(30, parsed.durationMinutes)
        assertTrue(!parsed.hostName.contains('|'))
    }

    /**
     * Una durata assente o non numerica rende il messaggio inutile: meglio
     * scartarlo che mostrare "0 minuti" a chi deve decidere se unirsi.
     */
    @Test
    fun lobbyInfoWithoutAUsableDurationIsRejected() {
        assertNull(parseGroupPauseBtMessage("LOBBY:Marco"))
        assertNull(parseGroupPauseBtMessage("LOBBY:Marco|"))
        assertNull(parseGroupPauseBtMessage("LOBBY:Marco|abc"))
        assertNull(parseGroupPauseBtMessage("LOBBY:Marco|0"))
    }

    /**
     * Il motivo per cui aggiungere un messaggio non rompe una versione
     * precedente all'altro capo: chi non lo conosce lo ignora e prosegue.
     */
    @Test
    fun unknownMessagesAreIgnoredRatherThanFatal() {
        assertNull(parseGroupPauseBtMessage("SOMETHING:new"))
        assertNull(parseGroupPauseBtMessage(""))
    }

    @Test
    fun unlockTokenRoundTrips() {
        assertEquals(41827, parseGroupPauseUnlockToken(groupPauseUnlockToken(41827)))
    }

    /**
     * Un token di un'altra pausa non deve liberare questa: il confronto sul
     * groupTag è ciò che impedisce di terminare la sessione sbagliata.
     */
    @Test
    fun unlockTokenOfAnotherPauseDoesNotMatch() {
        assertEquals(41827, parseGroupPauseUnlockToken(groupPauseUnlockToken(41827)))
        assert(parseGroupPauseUnlockToken(groupPauseUnlockToken(999)) != 41827)
    }

    /** Payload estranei (o il marker della lobby) non sono rilasci. */
    @Test
    fun nonUnlockPayloadsAreRejected() {
        assertNull(parseGroupPauseUnlockToken(groupPauseLobbyNameMarker(41827)))
        assertNull(parseGroupPauseUnlockToken("UNLOCK:"))
        assertNull(parseGroupPauseUnlockToken("UNLOCK:abc"))
        assertNull(parseGroupPauseUnlockToken(""))
    }
}
