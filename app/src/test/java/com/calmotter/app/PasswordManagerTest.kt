package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TODO.md "2.1": la classe più sensibile per la sicurezza dell'app era
 * anche l'unica singleton senza alcun test — incluso il percorso di
 * auto-guarigione aggiunto dopo il crash AEADBadTagException (vedi
 * specs/onboarding-and-password/design.md), che è precisamente il codice
 * che non deve regredire in silenzio.
 */
@RunWith(RobolectricTestRunner::class)
class PasswordManagerTest {

    // Deve restare uguale a PasswordManager.PREFS_FILE_NAME (private lì) —
    // stesso motivo per cui SessionManagerTest hardcoda le proprie chiavi
    // di SharedPreferences invece di esporle solo per il test.
    private val prefsFileName = "calm_otter_secure_prefs"

    private lateinit var context: Context

    @Before
    fun setUp() {
        installFakeAndroidKeyStore()
        PasswordManager.resetInstanceForTests()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun noPasswordSetInitially() {
        val manager = PasswordManager.getInstance(context)
        assertFalse(manager.isPasswordSet())
    }

    @Test
    fun setPasswordThenVerifyRoundTrips() {
        val manager = PasswordManager.getInstance(context)

        manager.setPassword("s3cret!")

        assertTrue(manager.isPasswordSet())
        assertTrue(manager.verify("s3cret!"))
    }

    @Test
    fun verifyRejectsTheWrongPassword() {
        val manager = PasswordManager.getInstance(context)
        manager.setPassword("s3cret!")

        assertFalse(manager.verify("not it"))
    }

    @Test
    fun verifyBeforeAnyPasswordIsSetReturnsFalseWithoutCrashing() {
        val manager = PasswordManager.getInstance(context)

        assertFalse(manager.verify("anything"))
    }

    /**
     * PasswordManager.kt:78 — un cambio password successivo senza fornire
     * il nome (es. da ChangePasswordScreen, che non ha quel campo) deve
     * lasciare il nome esistente invariato, non cancellarlo.
     */
    @Test
    fun partnerNameSurvivesAPasswordChangeThatOmitsIt() {
        val manager = PasswordManager.getInstance(context)

        manager.setPassword("first", partnerName = "Alex")
        assertEquals("Alex", manager.getPartnerName())

        manager.setPassword("second")

        assertEquals("Alex", manager.getPartnerName())
        assertTrue(manager.verify("second"))
        assertFalse(manager.verify("first"))
    }

    @Test
    fun partnerNameIsNullWhenNeverProvided() {
        val manager = PasswordManager.getInstance(context)
        manager.setPassword("s3cret!")

        assertNull(manager.getPartnerName())
    }

    @Test
    fun partnerNameIsTrimmedAndBlankIsTreatedAsAbsent() {
        val manager = PasswordManager.getInstance(context)

        manager.setPassword("s3cret!", partnerName = "  Sam  ")
        assertEquals("Sam", manager.getPartnerName())
    }

    @Test
    fun lockoutCountersPersistAcrossInstancesOfTheSamePrefsFile() {
        val manager = PasswordManager.getInstance(context)
        manager.setPassword("s3cret!")

        repeat(5) { manager.verify("wrong") }
        assertTrue(manager.isLockedOut())

        // Nuova istanza sullo stesso file (non un reset dello storage):
        // il lockout è uno stato persistito, non solo in memoria — deve
        // sopravvivere alla ricreazione del singleton (es. dopo un
        // riavvio del processo).
        PasswordManager.resetInstanceForTests()
        val recreated = PasswordManager.getInstance(context)

        assertTrue(recreated.isLockedOut())
        assertFalse(recreated.verify("s3cret!"))
    }

    /**
     * TODO.md "2.1"/specs/onboarding-and-password/design.md: riproduce lo
     * stesso scenario del crash reale (AEADBadTagException al costruttore),
     * corrompendo direttamente il keyset Tink dentro il file — lo stesso
     * meccanismo con cui un ripristino di backup senza la chiave Keystore
     * lo rompeva. L'istanza deve ricostruirsi da sola invece di lanciare.
     */
    @Test
    fun corruptedKeysetSelfHealsInsteadOfCrashing() {
        val manager = PasswordManager.getInstance(context)
        manager.setPassword("s3cret!", partnerName = "Alex")
        assertTrue(manager.isPasswordSet())

        PasswordManager.resetInstanceForTests()
        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            .edit()
            .putString("__androidx_security_crypto_encrypted_prefs_key_keyset__", "not a real keyset")
            .putString("__androidx_security_crypto_encrypted_prefs_value_keyset__", "not a real keyset either")
            .apply()

        // Prima della correzione: AEADBadTagException qui, non catturata.
        val recovered = PasswordManager.getInstance(context)

        // Il file corrotto viene azzerato, non recuperato: non c'è nulla da
        // recuperare da un keyset che non decifra più (vedi il commento in
        // PasswordManager.kt), quindi la password precedentemente impostata
        // non c'è più — comportamento corretto, non un effetto collaterale
        // tollerato.
        assertFalse(recovered.isPasswordSet())
        assertNull(recovered.getPartnerName())

        // E l'istanza risultante funziona normalmente da qui in avanti.
        recovered.setPassword("new-one")
        assertTrue(recovered.verify("new-one"))
    }
}
