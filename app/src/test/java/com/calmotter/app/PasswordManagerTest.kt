package com.calmotter.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
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
    private val prefsFileName = "calm_otter_password"
    private val legacyFileName = "calm_otter_secure_prefs"

    private lateinit var context: Context

    @Before
    fun setUp() {
        installFakeAndroidKeyStore()
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
        // riavvio del processo). Un'istanza nuova, non quella dell'app.
        val recreated = PasswordManager(context)

        assertTrue(recreated.isLockedOut())
        assertFalse(recreated.verify("s3cret!"))
    }

    /**
     * Lo stesso scenario del crash reale (AEADBadTagException al
     * costruttore, specs/onboarding-and-password/design.md): un file che non
     * si decifra più, come dopo un ripristino da backup senza la chiave
     * Keystore. L'istanza deve ricostruirsi da sola invece di lanciare.
     */
    @Test
    fun anUndecryptableFileSelfHealsInsteadOfCrashing() {
        val manager = PasswordManager.getInstance(context)
        manager.setPassword("s3cret!", partnerName = "Alex")
        assertTrue(manager.isPasswordSet())

        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            .edit()
            .putString("password_hash", "bm90IHJlYWwgY2lwaGVydGV4dA==")
            .putString("password_salt", "bm90IHJlYWwgZWl0aGVy")
            .commit()

        val recovered = PasswordManager(context)

        // Azzerato, non recuperato: non c'è nulla da recuperare.
        assertFalse(recovered.isPasswordSet())
        assertNull(recovered.getPartnerName())

        recovered.setPassword("new-one")
        assertTrue(recovered.verify("new-one"))
    }

    /** Nessun valore del file è leggibile in chiaro, nemmeno il nome. */
    @Test
    fun nothingIsStoredInTheClear() {
        PasswordManager.getInstance(context).setPassword("s3cret!", partnerName = "Alex")

        val raw = context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE).all.values.joinToString()

        assertFalse(raw.contains("Alex"))
        assertFalse(raw.contains("s3cret!"))
    }

    // ── Migrazione da EncryptedSharedPreferences ─────────────────────────

    private fun writeLegacy(block: SharedPreferences.Editor.() -> Unit) {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, legacyFileName, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).edit().apply(block).commit()
    }

    /** Il formato di prima: salt e hash in Base64, come li scriveva PasswordManager. */
    private fun legacyPassword(password: String): Pair<String, String> {
        val salt = ByteArray(16) { it.toByte() }
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(salt, Base64.NO_WRAP) to Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    @Test
    fun anExistingPasswordIsMigratedAndTheOldFileRemoved() {
        val (salt, hash) = legacyPassword("old-pass")
        writeLegacy {
            putString("password_salt", salt)
            putString("password_hash", hash)
            putString("partner_name", "Alex")
        }

        val manager = PasswordManager(context)

        assertTrue(manager.isPasswordSet())
        assertTrue(manager.verify("old-pass"))
        assertEquals("Alex", manager.getPartnerName())
        assertTrue(context.getSharedPreferences(legacyFileName, Context.MODE_PRIVATE).all.isEmpty())
    }

    /** Un lockout in corso non si azzera passando al formato nuovo. */
    @Test
    fun aLockoutInProgressSurvivesTheMigration() {
        val (salt, hash) = legacyPassword("old-pass")
        writeLegacy {
            putString("password_salt", salt)
            putString("password_hash", hash)
            putInt("failed_attempts", 5)
            putLong("lockout_until", System.currentTimeMillis() + 60_000L)
        }

        assertTrue(PasswordManager(context).isLockedOut())
    }

    /** Un file vecchio che non si decifra non blocca l'avvio: si scarta. */
    @Test
    fun anUndecryptableOldFileIsDroppedWithoutCrashing() {
        context.getSharedPreferences(legacyFileName, Context.MODE_PRIVATE).edit()
            .putString("__androidx_security_crypto_encrypted_prefs_key_keyset__", "not a real keyset")
            .putString("__androidx_security_crypto_encrypted_prefs_value_keyset__", "not a real keyset either")
            .commit()

        val manager = PasswordManager(context)

        assertFalse(manager.isPasswordSet())
        assertTrue(context.getSharedPreferences(legacyFileName, Context.MODE_PRIVATE).all.isEmpty())
    }
}
