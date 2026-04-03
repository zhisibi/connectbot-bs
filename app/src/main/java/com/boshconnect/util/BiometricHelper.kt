package com.boshconnect.util

import androidx.biometric.BiometricPrompt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.boshconnect.R

object BiometricHelper {

    fun showBiometricPrompt(
        activity: AppCompatActivity,
        title: String? = null,
        subtitle: String? = null,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (Int, String) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess(result.cryptoObject)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                onFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title ?: activity.getString(R.string.biometric_auth_title))
            .setSubtitle(subtitle ?: activity.getString(R.string.biometric_auth_subtitle))
            .setNegativeButtonText(activity.getString(R.string.use_password))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun showBiometricPromptWithCrypto(
        activity: AppCompatActivity,
        cryptoObject: BiometricPrompt.CryptoObject,
        title: String? = null,
        subtitle: String? = null,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onError: (Int, String) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess(result.cryptoObject)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                onFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title ?: activity.getString(R.string.biometric_auth_title))
            .setSubtitle(subtitle ?: activity.getString(R.string.biometric_auth_subtitle))
            .setNegativeButtonText(activity.getString(R.string.use_password))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }

    fun isBiometricAvailable(activity: AppCompatActivity): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(activity)
        // Try STRONG first, fall back to WEAK
        return biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS ||
        biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
}
