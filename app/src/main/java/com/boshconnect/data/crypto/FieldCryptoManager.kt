package com.boshconnect.data.crypto

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
        
        // Validate Base64 before decoding - reject invalid characters
        val base64Regex = Regex("^[A-Za-z0-9+/=]+\$")
        if (!base64Regex.matches(cipherText)) {
            // Not valid Base64, return null instead of crashing
            return null
        }
        
        val payload = try {
            Base64.decode(cipherText, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            // Invalid Base64, return null instead of crashing
            return null
        }
        
        if (payload.size < 13) return null
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding")
        } catch (e: Exception) {
            return null
        }
        val key = SecretKeySpec(normalizeKey(keyBytes), "AES")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return try {
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            // Decryption failed, return null instead of crashing
            null
        }
    }

    private fun normalizeKey(keyBytes: ByteArray): ByteArray {
        return when {
            keyBytes.size == 16 || keyBytes.size == 24 || keyBytes.size == 32 -> keyBytes
            keyBytes.size > 32 -> keyBytes.copyOf(32)
            else -> keyBytes.copyOf(32)
        }
    }
}
