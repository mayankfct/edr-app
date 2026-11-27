package com.forensiccybertech.eagleye.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the auth token encrypted with an AES-256-GCM key held in Android Keystore (non-exportable). */
class SecureTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    fun save(token: String) {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(K_IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString(K_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun load(): String? = try {
        val iv = Base64.decode(prefs.getString(K_IV, null) ?: return null, Base64.NO_WRAP)
        val ct = Base64.decode(prefs.getString(K_CT, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        clear(); null // corrupted/invalidated key -> force re-login
    }

    @Synchronized
    fun clear() { prefs.edit().clear().apply() }

    private companion object {
        const val ALIAS = "eagleye_token_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val K_IV = "iv"
        const val K_CT = "ct"
    }
}
