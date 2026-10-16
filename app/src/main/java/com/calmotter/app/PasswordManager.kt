package com.calmotter.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Gestisce la password che SOLO un'altra persona dovrebbe conoscere.
 * La password non viene mai salvata in chiaro: si salvano solo salt + hash
 * (PBKDF2-HMAC-SHA256, 120k iterazioni), e ogni valore del file è cifrato
 * da [KeystoreCipher] con una chiave AES-256-GCM dell'Android Keystore.
 *
 * Fino al 2026-10-16 il file era un `EncryptedSharedPreferences`
 * (androidx.security:security-crypto, deprecata): al primo avvio quei dati
 * passano qui (vedi [migrateLegacy]) e il file vecchio si cancella.
 *
 * Il rate-limiting sui tentativi (quanti falliti, per quanto tempo in
 * lockout) è delegato a [LockoutPolicy], logica pura testabile senza
 * Keystore.
 */
class PasswordManager internal constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(KEY_ALIAS)

    init {
        migrateLegacy(context)
        // Un file che non si decifra più (tornato da un backup su un'altra
        // installazione: la chiave Keystore non segue il backup) si azzera.
        // Non c'è nulla da recuperare, e l'alternativa era un crash-loop
        // permanente (segnalato, AEADBadTagException su un Galaxy S22): chi
        // ha il telefono può reimpostarla dall'onboarding.
        if (prefs.contains(KEY_HASH) && (readString(KEY_HASH) == null || readString(KEY_SALT) == null)) {
            prefs.edit { clear() }
        }
    }

    private fun readString(key: String): String? = prefs.getString(key, null)?.let(cipher::decrypt)
    private fun readInt(key: String): Int = readString(key)?.toIntOrNull() ?: 0
    private fun readLong(key: String): Long = readString(key)?.toLongOrNull() ?: 0L
    private fun SharedPreferences.Editor.putSecret(key: String, value: Any) {
        putString(key, cipher.encrypt(value.toString()))
    }

    /**
     * Sposta salt, hash, nome e contatori dal vecchio `EncryptedSharedPreferences`.
     * Solo se qui non c'è ancora una password: una seconda migrazione non
     * deve mai sovrascrivere quella nuova. Il file vecchio si cancella in
     * ogni caso, anche se non si decifra (stesso ragionamento del reset in
     * `init`). Da togliere, insieme alla dipendenza security-crypto, quando
     * nessuna installazione avrà più il file vecchio.
     */
    private fun migrateLegacy(context: Context) {
        val legacyRaw = context.getSharedPreferences(LEGACY_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        if (legacyRaw.all.isEmpty()) return
        if (!prefs.contains(KEY_HASH)) {
            runCatching {
                val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                val legacy = EncryptedSharedPreferences.create(
                    context,
                    LEGACY_PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                val salt = legacy.getString(KEY_SALT, null)
                val hash = legacy.getString(KEY_HASH, null)
                if (salt != null && hash != null) {
                    prefs.edit(commit = true) {
                        putSecret(KEY_SALT, salt)
                        putSecret(KEY_HASH, hash)
                        legacy.getString(KEY_PARTNER_NAME, null)?.let { putSecret(KEY_PARTNER_NAME, it) }
                        putSecret(KEY_FAILED_ATTEMPTS, legacy.getInt(KEY_FAILED_ATTEMPTS, 0))
                        putSecret(KEY_LOCKOUT_UNTIL, legacy.getLong(KEY_LOCKOUT_UNTIL, 0L))
                    }
                }
            }
        }
        legacyRaw.edit(commit = true) { clear() }
        context.deleteSharedPreferences(LEGACY_PREFS_FILE_NAME)
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
        val trimmedName = partnerName?.trim()
        prefs.edit {
            putSecret(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putSecret(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            if (!trimmedName.isNullOrEmpty()) {
                putSecret(KEY_PARTNER_NAME, trimmedName)
            }
        }
    }

    /** Nome di chi ha impostato la password, o null se non fornito. */
    fun getPartnerName(): String? = readString(KEY_PARTNER_NAME)

    /**
     * Verifica la password. Se è attivo un lockout (troppi tentativi errati
     * consecutivi), ritorna false immediatamente senza toccare hash/contatori,
     * così una chiamata durante il lockout non resetta nulla.
     */
    fun verify(password: String): Boolean {
        val now = System.currentTimeMillis()
        val current = LockoutPolicy.State(
            readInt(KEY_FAILED_ATTEMPTS),
            readLong(KEY_LOCKOUT_UNTIL)
        )
        if (LockoutPolicy.isLockedOut(current, now)) return false

        val saltStr = readString(KEY_SALT) ?: return false
        val hashStr = readString(KEY_HASH) ?: return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val expected = Base64.decode(hashStr, Base64.NO_WRAP)
        val actual = hash(password, salt)
        // MessageDigest.isEqual(), non contentEquals(): confronto a tempo
        // costante, non uno che esce al primo byte diverso. Il canale
        // temporale è quasi teorico qui (chi tenta l'unlock ha comunque il
        // telefono in mano, e PBKDF2 domina qualunque misurazione), ma
        // eliminare la domanda costa una riga (TODO.md "7").
        val matches = MessageDigest.isEqual(actual, expected)

        val next = LockoutPolicy.afterAttempt(current, matches, now)
        prefs.edit {
            putSecret(KEY_FAILED_ATTEMPTS, next.failedAttempts)
            putSecret(KEY_LOCKOUT_UNTIL, next.lockoutUntilMs)
        }

        return matches
    }

    /** True se è attivo un lockout per troppi tentativi errati consecutivi. */
    fun isLockedOut(): Boolean {
        val current = LockoutPolicy.State(
            readInt(KEY_FAILED_ATTEMPTS),
            readLong(KEY_LOCKOUT_UNTIL)
        )
        return LockoutPolicy.isLockedOut(current, System.currentTimeMillis())
    }

    /** Secondi rimanenti al lockout, arrotondati per eccesso; 0 se non in lockout. */
    fun lockoutRemainingSeconds(): Int {
        val current = LockoutPolicy.State(
            readInt(KEY_FAILED_ATTEMPTS),
            readLong(KEY_LOCKOUT_UNTIL)
        )
        return LockoutPolicy.lockoutRemainingSeconds(current, System.currentTimeMillis())
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val PREFS_FILE_NAME = "calm_otter_password"
        private const val KEY_ALIAS = "calm_otter_password_key"
        private const val LEGACY_PREFS_FILE_NAME = "calm_otter_secure_prefs"
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val KEY_PARTNER_NAME = "partner_name"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256

        /** L'istanza dell'app: vive in [AppGraph], una per Application (vedi CalmOtterApplication). */
        fun getInstance(context: Context): PasswordManager = context.appGraph.passwordManager
    }
}
