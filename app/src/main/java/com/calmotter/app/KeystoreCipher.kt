package com.calmotter.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra e decifra stringhe con una chiave AES-256-GCM che vive nell'Android
 * Keystore e non ne esce mai. Sostituisce `EncryptedSharedPreferences`
 * (androidx.security:security-crypto, deprecata dalla 1.1.0): stessa
 * protezione — senza la chiave di questo dispositivo/installazione il
 * contenuto non si legge — con una ventina di righe nostre invece di una
 * libreria che non avrà più correzioni.
 *
 * Il formato è Base64(IV ‖ testo cifrato con tag). L'IV lo sceglie il
 * Keystore a ogni cifratura (randomizzazione obbligatoria), non noi.
 */
internal class KeystoreCipher(private val alias: String) {

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /**
     * null se non si decifra: chiave diversa (file tornato da un backup su
     * un'altra installazione) o contenuto alterato. Chi chiama decide cosa
     * farne; qui non si lancia, perché un'eccezione all'avvio era un
     * crash-loop permanente (vedi [PasswordManager]).
     */
    fun decrypt(encoded: String): String? = runCatching {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
