package com.sbssh.data.crypto

/**
 * Holds the derived PBKDF2 key in memory during an active session.
 * Used by FieldCryptoManager to encrypt/decrypt sensitive VPS fields.
 * Cleared on app close or re-authentication.
 */
object SessionKeyHolder {
    private var keyBytes: ByteArray? = null

    fun set(key: ByteArray) {
        keyBytes = key
    }

    fun get(): ByteArray {
        return keyBytes ?: throw IllegalStateException("Session key not set. User not authenticated.")
    }

    fun isSet(): Boolean = keyBytes != null

    fun clear() {
        keyBytes = null
    }
}
