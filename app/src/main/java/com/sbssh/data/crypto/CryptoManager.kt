package com.sbssh.data.crypto

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import com.sbssh.util.AppLogger

class CryptoManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("sbssh_prefs", Context.MODE_PRIVATE)
    }

    fun isFirstLaunch(): Boolean {
        return !prefs.contains("salt")
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun saveSalt(salt: ByteArray) {
        prefs.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).apply()
    }

    fun clearMasterPasswordState() {
        prefs.edit()
            .remove("salt")
            .remove("password_hash")
            .remove("bio_encrypted_key")
            .remove("bio_key_iv")
            .apply()
    }

    fun getSalt(): ByteArray {
        val saltStr = prefs.getString("salt", null)
            ?: throw IllegalStateException("Salt not found. Call generateSalt() first.")
        return Base64.decode(saltStr, Base64.NO_WRAP)
    }

    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /**
     * Derive key bytes AND return a SQLCipher-compatible passphrase string.
     * The passphrase is a hex representation of the derived key, with unsigned byte handling.
     */
    fun deriveKeyForDb(password: String, salt: ByteArray): Pair<ByteArray, String> {
        val keyBytes = deriveKey(password, salt)
        val hexKey = keyBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return Pair(keyBytes, hexKey)
    }

    fun enableBiometric(keyBytes: ByteArray) {
        // Simply store the key in SharedPreferences (Base64)
        // Biometric is used as a UI gate, not a cryptographic gate
        AppLogger.log("CRYPTO", "enableBiometric: storing key, len=${keyBytes.size}")
        prefs.edit()
            .putString("bio_key", Base64.encodeToString(keyBytes, Base64.NO_WRAP))
            .apply()
        AppLogger.log("CRYPTO", "enableBiometric: success")
    }

    fun getBiometricKey(): ByteArray {
        val encoded = prefs.getString("bio_key", null)
            ?: throw IllegalStateException("No biometric key stored")
        AppLogger.log("CRYPTO", "getBiometricKey: found key")
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.contains("bio_key")
    }

    fun disableBiometric() {
        AppLogger.log("CRYPTO", "disableBiometric")
        prefs.edit()
            .remove("bio_key")
            .apply()
    }

    fun isMasterPasswordSet(): Boolean {
        return prefs.contains("salt") && prefs.contains("password_hash")
    }

    fun setPasswordVerification(password: String, salt: ByteArray) {
        val verifySpec = PBEKeySpec((password + "verify").toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = Base64.encodeToString(factory.generateSecret(verifySpec).encoded, Base64.NO_WRAP)
        AppLogger.log("CRYPTO", "setPasswordVerification: pwdLen=${password.length}, saltLen=${salt.size}, hashLen=${hash.length}")
        prefs.edit().putString("password_hash", hash).apply()
    }

    fun verifyMasterPassword(password: String): Boolean {
        val salt = getSalt()
        val verifySpec = PBEKeySpec((password + "verify").toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = Base64.encodeToString(factory.generateSecret(verifySpec).encoded, Base64.NO_WRAP)
        val storedHash = prefs.getString("password_hash", null)
        AppLogger.log("CRYPTO", "verifyMasterPassword: pwdLen=${password.length}, saltLen=${salt.size}, hashLen=${hash.length}, storedHashLen=${storedHash?.length}, match=${hash == storedHash}")
        return storedHash != null && hash == storedHash
    }

    /**
     * Change master password.
     * Returns the new session key bytes for the caller to update SessionKeyHolder and re-encrypt data.
     */
    fun changeMasterPassword(oldPassword: String, newPassword: String): ByteArray {
        if (!verifyMasterPassword(oldPassword)) {
            throw IllegalArgumentException("Old password is incorrect")
        }
        if (newPassword.length < 6) {
            throw IllegalArgumentException("New password must be at least 6 characters")
        }

        // Generate new salt
        val newSalt = generateSalt()
        val newKeyBytes = deriveKey(newPassword, newSalt)

        // Store new salt and verification hash
        saveSalt(newSalt)
        setPasswordVerification(newPassword, newSalt)

        // Update biometric if enabled
        if (isBiometricEnabled()) {
            enableBiometric(newKeyBytes)
        }

        return newKeyBytes
    }
}
