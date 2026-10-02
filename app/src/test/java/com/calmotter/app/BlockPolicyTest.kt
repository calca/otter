package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La decisione di blocco è l'unica parte del servizio di accessibilità che si
 * può provare senza un dispositivo, ed è quella che, sbagliando, copre lo
 * schermo mentre si usa qualcosa di permesso (o lascia scoperta un'app che
 * andava bloccata). Ogni caso qui sotto corrisponde a un bug già visto o a
 * una regola dichiarata nel servizio.
 */
class BlockPolicyTest {

    private val own = "com.calmotter.app"
    private val allowed = BlockPolicy.allowedPackages(
        defaultDialer = "com.google.android.dialer",
        ownPackage = own,
        keyboards = setOf("com.google.android.inputmethod.latin"),
        userAllowed = setOf("com.example.maps"),
    )

    private fun block(pkg: String, session: Boolean = true, fullScreen: Boolean = true) =
        BlockPolicy.shouldBlock(session, fullScreen, pkg, allowed)

    @Test
    fun nonAllowedAppIsBlockedDuringASession() {
        assertTrue(block("com.instagram.android"))
    }

    @Test
    fun nothingIsBlockedWithoutASession() {
        assertFalse(block("com.instagram.android", session = false))
    }

    @Test
    fun popupWindowsAreNeverBlocked() {
        // Il suggeritore di sistema che si apriva sul campo password e
        // faceva comparire il blocco sopra al dialog di sblocco.
        assertFalse(block("com.google.android.ext.services", fullScreen = false))
        assertFalse(block("com.instagram.android", fullScreen = false))
    }

    @Test
    fun dialerSystemUiSystemDialogsAndThisAppAreAllowed() {
        assertFalse(block("com.google.android.dialer"))
        assertFalse(block(BlockPolicy.SYSTEM_UI_PACKAGE))
        assertFalse(block(BlockPolicy.ANDROID_PACKAGE))
        assertFalse(block(own))
    }

    /** Segnalato: su Samsung una telefonata durante la pausa finiva sotto il blocco. */
    @Test
    fun callScreensAreNeverBlocked() {
        assertFalse(block("com.samsung.android.incallui"))
        assertFalse(block("com.android.incallui"))
        assertFalse(block("com.android.server.telecom"))
        // Un produttore non in elenco, con lo stesso nome di pacchetto.
        assertFalse(block("com.oneplus.incallui"))
        // Il nome deve finire così: non basta contenerlo.
        assertTrue(block("com.example.incalluifake"))
    }

    @Test
    fun keyboardsAreAllowed() {
        // Senza, aprire la tastiera nel dialog di sblocco faceva scattare il blocco.
        assertFalse(block("com.google.android.inputmethod.latin"))
    }

    @Test
    fun userAllowlistIsHonoured() {
        assertFalse(block("com.example.maps"))
        assertTrue(block("com.example.other"))
    }

    @Test
    fun missingDefaultDialerDoesNotAllowAnythingElse() {
        val set = BlockPolicy.allowedPackages(null, own, emptySet(), emptySet())
        assertEquals(
            // Le schermate di chiamata note restano consentite anche senza
            // dialer predefinito: le chiamate passano sempre.
            setOf(BlockPolicy.SYSTEM_UI_PACKAGE, BlockPolicy.ANDROID_PACKAGE, own) + BlockPolicy.IN_CALL_PACKAGES,
            set,
        )
    }

    @Test
    fun theUnlockPathIsNotAccidentallyBlocked() {
        // Regressione: ogni pacchetto che serve a sbloccare (questa app, le
        // tastiere, systemUI) deve restare consentito anche con whitelist vuota.
        val minimal = BlockPolicy.allowedPackages(null, own, setOf("ime"), emptySet())
        for (pkg in listOf(own, "ime", BlockPolicy.SYSTEM_UI_PACKAGE)) {
            assertFalse(BlockPolicy.shouldBlock(true, true, pkg, minimal))
        }
    }
}
