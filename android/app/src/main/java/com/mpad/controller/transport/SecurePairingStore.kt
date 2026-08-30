package com.mpad.controller.transport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePairingStore(context: Context) {
    private val prefs = context.getSharedPreferences("mpad_secure_pairings", Context.MODE_PRIVATE)
    private val alias = "mpad_pairing_key_v1"

    fun put(peerId: String, token: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token)
        val value = cipher.iv + encrypted
        prefs.edit().putString(prefKey(peerId), Base64.encodeToString(value, Base64.NO_WRAP)).apply()
    }

    fun get(peerId: String): ByteArray? = try {
        val encoded = prefs.getString(prefKey(peerId), null) ?: return null
        val value = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = value.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.doFinal(value.copyOfRange(12, value.size))
    } catch (_: Exception) { null }

    fun remove(peerId: String) = prefs.edit().remove(prefKey(peerId)).apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun prefKey(peerId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(peerId.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

