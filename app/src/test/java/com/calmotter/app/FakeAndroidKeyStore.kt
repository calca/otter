package com.calmotter.app

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

/**
 * Registra un provider JCA "AndroidKeyStore" minimale, così
 * `EncryptedSharedPreferences`/`MasterKey` (usati da [PasswordManager])
 * possono girare sotto Robolectric — che non fornisce un vero Android
 * Keystore (è un servizio di sistema/hardware-backed, non una classe
 * Android pura, quindi fuori dallo scope di ciò che Robolectric simula).
 * Senza questo, `KeyStore.getInstance("AndroidKeyStore")` fallisce con
 * `NoSuchAlgorithmException: AndroidKeyStore KeyStore not available` ed
 * era il motivo per cui [PasswordManager] — la classe più sensibile per la
 * sicurezza dell'app — restava l'unico state holder senza alcun test
 * (TODO.md "2.1").
 *
 * Le chiavi generate qui sono normali chiavi AES software (non
 * hardware-backed, quindi estraibili) — a differenza del vero
 * AndroidKeyStore. Non è un problema per ciò che questi test verificano:
 * l'obiettivo è la logica di [PasswordManager] (hash, lockout, nome del
 * partner, auto-guarigione da un keyset corrotto), non le garanzie
 * hardware del Keystore reale, che restano fuori da ciò che uno unit test
 * JVM può comunque verificare.
 */
private object FakeAndroidKeyStoreBackend {
    val keys = mutableMapOf<String, SecretKey>()
}

class FakeAndroidKeyStoreSpi : KeyStoreSpi() {
    override fun engineLoad(stream: InputStream?, password: CharArray?) {}
    override fun engineStore(stream: OutputStream?, password: CharArray?) {}
    override fun engineIsKeyEntry(alias: String) = FakeAndroidKeyStoreBackend.keys.containsKey(alias)
    override fun engineIsCertificateEntry(alias: String) = false
    override fun engineContainsAlias(alias: String) = FakeAndroidKeyStoreBackend.keys.containsKey(alias)
    override fun engineGetKey(alias: String, password: CharArray?): Key? = FakeAndroidKeyStoreBackend.keys[alias]
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCreationDate(alias: String): Date? = if (engineContainsAlias(alias)) Date() else null

    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) {
        FakeAndroidKeyStoreBackend.keys[alias] = key as SecretKey
    }

    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) {
        throw UnsupportedOperationException("not needed by MasterKey/EncryptedSharedPreferences")
    }

    override fun engineSetCertificateEntry(alias: String, cert: Certificate) {}
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineDeleteEntry(alias: String) { FakeAndroidKeyStoreBackend.keys.remove(alias) }
    override fun engineAliases(): Enumeration<String> = Collections.enumeration(FakeAndroidKeyStoreBackend.keys.keys)
    override fun engineSize() = FakeAndroidKeyStoreBackend.keys.size
}

/**
 * `KeyGenerator.getInstance("AES", "AndroidKeyStore")` è come `MasterKey`
 * genera davvero la chiave — questo SPI produce una normale chiave AES
 * software (via il "SunJCE"/BC di sistema) e la registra nel backend
 * condiviso sotto l'alias dichiarato dal [KeyGenParameterSpec], così un
 * successivo `KeyStore.getKey(alias, null)` la ritrova.
 */
class FakeAndroidKeyStoreKeyGeneratorSpi : KeyGeneratorSpi() {
    private var alias: String? = null
    private var keySize: Int = 256

    override fun engineInit(random: SecureRandom?) {}

    override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
        val spec = params as? KeyGenParameterSpec ?: return
        alias = spec.keystoreAlias
        if (spec.keySize > 0) keySize = spec.keySize
    }

    override fun engineInit(keysize: Int, random: SecureRandom?) {
        keySize = keysize
    }

    override fun engineGenerateKey(): SecretKey {
        val key = KeyGenerator.getInstance("AES").apply { init(keySize) }.generateKey()
        alias?.let { FakeAndroidKeyStoreBackend.keys[it] = key }
        return key
    }
}

class FakeAndroidKeyStoreProvider :
    Provider("AndroidKeyStore", 1.0, "Fake AndroidKeyStore for Robolectric tests") {
    init {
        put("KeyStore.AndroidKeyStore", FakeAndroidKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", FakeAndroidKeyStoreKeyGeneratorSpi::class.java.name)
    }
}

/** Idempotente: più `@Before` nella stessa suite possono chiamarla senza duplicare il provider. */
fun installFakeAndroidKeyStore() {
    if (Security.getProvider("AndroidKeyStore") == null) {
        Security.addProvider(FakeAndroidKeyStoreProvider())
    }
}
