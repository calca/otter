package com.calmotter.app.nfc

import com.calmotter.app.nfc.QuickTogetherProtocol.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class QuickTogetherProtocolTest {

    @Test
    fun helloRoundTrips() {
        val hello = QuickTogetherProtocol.decodeHello(QuickTogetherProtocol.encodeHello(State.READY, "Pixel di Marta"))
        assertEquals(QuickTogetherProtocol.Hello(QuickTogetherProtocol.VERSION, State.READY, "Pixel di Marta"), hello)
    }

    @Test
    fun theBluetoothLobbyMarkerIsNotAHello() {
        // Se B è nella lobby Bluetooth risponde con il suo marker: A non deve scambiarlo per un HELLO.
        val marker = "CalmOtter-1234".toByteArray(StandardCharsets.UTF_8) + QuickTogetherProtocol.STATUS_OK
        assertNull(QuickTogetherProtocol.decodeHello(marker))
    }

    @Test
    fun proposeRoundTrips() {
        val p = QuickTogetherProtocol.Proposal(
            version = QuickTogetherProtocol.VERSION,
            durationMinutes = 240,
            activityId = 7,
            groupTag = -1234,
            startDelayMillis = QuickTogetherProtocol.START_DELAY_MILLIS,
            name = "Luca",
        )
        val command = QuickTogetherProtocol.encodePropose(p)
        assertTrue(QuickTogetherProtocol.isPropose(command))
        assertEquals(p, QuickTogetherProtocol.decodePropose(command))
    }

    @Test
    fun longNamesAreCutWithoutBreakingACharacter() {
        val long = "Telefono di Mariaè".repeat(5)
        val hello = QuickTogetherProtocol.decodeHello(QuickTogetherProtocol.encodeHello(State.READY, long))!!
        assertTrue(hello.name.toByteArray(StandardCharsets.UTF_8).size <= 40)
        assertTrue(long.startsWith(hello.name))
    }

    @Test
    fun resultRoundTripsAndRejectsGarbage() {
        for (s in State.entries) {
            assertEquals(s, QuickTogetherProtocol.decodeResult(QuickTogetherProtocol.encodeResult(s)))
        }
        assertNull(QuickTogetherProtocol.decodeResult(byteArrayOf(0x6A, 0x82.toByte())))
        assertNull(QuickTogetherProtocol.decodePropose(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)))
    }
}
