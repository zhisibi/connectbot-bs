package com.sbssh.data.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class FieldCryptoManager {

    fun encrypt(plainText: String?, keyBytes: ByteArray): String? {
        if (plainText.isNullOrEmpty()) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val key = SecretKeySpec(normalizeKey(keyBytes), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String?, keyBytes: ByteArray): String? {
        if (cipherText.isNullOrEmpty()) return null
        val payload = Base64.decode(cipherText, Base64.NO_WRAP)
        if (payload.size < 13) return null
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(normalizeKey(keyBytes), "AES")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun normalizeKey(keyBytes: ByteArray): ByteArray {
        return when {
            keyBytes.size == 16 || keyBytes.size == 24 || keyBytes.size == 32 -> keyBytes
            keyBytes.size > 32 -> keyBytes.copyOf(32)
            else -> keyBytes.copyOf(32)
        }
    }
}
