package com.calmotter.app

import android.content.Context
import android.util.Base64
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
 */
class PasswordManager(context: Context) {

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

    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(password, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Verifica la password. Se è attivo un lockout (troppi tentativi errati
     * consecutivi), ritorna false immediatamente senza toccare hash/contatori,
     * così una chiamata durante il lockout non resetta nulla.
     */
    fun verify(password: String): Boolean {
        if (isLockedOut()) return false

        val saltStr = prefs.getString(KEY_SALT, null) ?: return false
        val hashStr = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val expected = Base64.decode(hashStr, Base64.NO_WRAP)
        val actual = hash(password, salt)
        val matches = actual.contentEquals(expected)

        if (matches) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
        } else {
            val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            if (failedAttempts >= MAX_ATTEMPTS) {
                // Lockout raggiunto: azzera il contatore così l'utente riparte
                // con un nuovo set di tentativi allo scadere del lockout.
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
                    .apply()
            } else {
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
                    .apply()
            }
        }

        return matches
    }

    /** True se è attivo un lockout per troppi tentativi errati consecutivi. */
    fun isLockedOut(): Boolean = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) > System.currentTimeMillis()

    /** Secondi rimanenti al lockout, arrotondati per eccesso; 0 se non in lockout. */
    fun lockoutRemainingSeconds(): Int {
        val remainingMs = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()
        if (remainingMs <= 0) return 0
        return ((remainingMs + 999) / 1000).toInt()
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L
    }
}
