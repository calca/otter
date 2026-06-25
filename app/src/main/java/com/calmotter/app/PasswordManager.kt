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

    fun verify(password: String): Boolean {
        val saltStr = prefs.getString(KEY_SALT, null) ?: return false
        val hashStr = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val expected = Base64.decode(hashStr, Base64.NO_WRAP)
        val actual = hash(password, salt)
        return actual.contentEquals(expected)
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
    }
}
