package com.calmotter.app

import android.content.Context
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Gestisce la password che SOLO un'altra persona dovrebbe conoscere.
 * La password non viene mai salvata in chiaro: si salvano solo salt + hash
 * (PBKDF2-HMAC-SHA256, 120k iterazioni) dentro EncryptedSharedPreferences,
 * a sua volta cifrato con una chiave gestita dall'Android Keystore.
 *
 * Il rate-limiting sui tentativi (quanti falliti, per quanto tempo in
 * lockout) è delegato a [LockoutPolicy], logica pura testabile senza
 * Keystore.
 */
class PasswordManager private constructor(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "calm_otter_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPasswordSet(): Boolean = prefs.contains(KEY_HASH)

    /**
     * @param partnerName nome facoltativo di chi sta impostando la password
     * (raccolto durante l'onboarding, vedi OnboardingScreen.kt), mostrato poi
     * in Settings ("Impostata da …"). Salvato in chiaro nello stesso
     * EncryptedSharedPreferences della password — non è un segreto, è solo
     * un'etichetta — ma non viene mai sovrascritto con un valore vuoto: un
     * cambio password successivo senza fornirlo (es. da
     * ChangePasswordScreen, che non ha questo campo) lascia il nome
     * esistente invariato invece di cancellarlo.
     */
    fun setPassword(password: String, partnerName: String? = null) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(password, salt)
        val editor = prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
        val trimmedName = partnerName?.trim()
        if (!trimmedName.isNullOrEmpty()) {
            editor.putString(KEY_PARTNER_NAME, trimmedName)
        }
        editor.apply()
    }

    /** Nome di chi ha impostato la password, o null se non fornito. */
    fun getPartnerName(): String? = prefs.getString(KEY_PARTNER_NAME, null)

    /**
     * Verifica la password. Se è attivo un lockout (troppi tentativi errati
     * consecutivi), ritorna false immediatamente senza toccare hash/contatori,
     * così una chiamata durante il lockout non resetta nulla.
     */
    fun verify(password: String): Boolean {
        val now = System.currentTimeMillis()
        val current = LockoutPolicy.State(
            prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        )
        if (LockoutPolicy.isLockedOut(current, now)) return false

        val saltStr = prefs.getString(KEY_SALT, null) ?: return false
        val hashStr = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val expected = Base64.decode(hashStr, Base64.NO_WRAP)
        val actual = hash(password, salt)
        val matches = actual.contentEquals(expected)

        val next = LockoutPolicy.afterAttempt(current, matches, now)
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, next.failedAttempts)
            .putLong(KEY_LOCKOUT_UNTIL, next.lockoutUntilMs)
            .apply()

        return matches
    }

    /** True se è attivo un lockout per troppi tentativi errati consecutivi. */
    fun isLockedOut(): Boolean {
        val current = LockoutPolicy.State(
            prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        )
        return LockoutPolicy.isLockedOut(current, System.currentTimeMillis())
    }

    /** Secondi rimanenti al lockout, arrotondati per eccesso; 0 se non in lockout. */
    fun lockoutRemainingSeconds(): Int {
        val current = LockoutPolicy.State(
            prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        )
        return LockoutPolicy.lockoutRemainingSeconds(current, System.currentTimeMillis())
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val KEY_PARTNER_NAME = "partner_name"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256

        @Volatile private var instance: PasswordManager? = null

        fun getInstance(context: Context): PasswordManager =
            instance ?: synchronized(this) {
                instance ?: PasswordManager(context.applicationContext).also { instance = it }
            }

        @VisibleForTesting
        internal fun resetInstanceForTests() {
            instance = null
        }
    }
}
