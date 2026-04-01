package com.sbssh.ui.cloud

import com.google.gson.Gson
import com.sbssh.util.AppLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple HTTP client for SbSSH Cloud Sync API.
 * Uses HttpURLConnection — no extra dependency needed.
 */
class CloudSyncApi(private var baseUrl: String) {

    private val gson = Gson()

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    // --- Models ---
    data class RegisterRequest(val username: String, val password: String, val encryptedSalt: String)
    data class LoginRequest(val username: String, val password: String)
    data class TokenResponse(val token: String, val userId: Int, val username: String)
    data class UploadRequest(val encryptedData: String, val deviceId: String? = null)
    data class DownloadResponse(val encryptedData: String?, val updatedAt: String?)

    // --- API ---
    fun register(username: String, password: String, encryptedSalt: String): TokenResponse {
        val body = gson.toJson(RegisterRequest(username, password, encryptedSalt))
        return postJson("/api/v1/register", body)
    }

    fun login(username: String, password: String): TokenResponse {
        val body = gson.toJson(LoginRequest(username, password))
        return postJson("/api/v1/login", body)
    }

    fun upload(token: String, encryptedData: String, deviceId: String?) {
        val body = gson.toJson(UploadRequest(encryptedData, deviceId))
        val url = URL("$baseUrl/api/v1/sync/upload")
        AppLogger.log("CLOUD", "POST /api/v1/sync/upload (auth)")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader(Charsets.UTF_8).readText()
            AppLogger.log("CLOUD", "Response $code: ${resp.take(200)}")
            if (code !in 200..299) {
                val msg = try { gson.fromJson(resp, Map::class.java)["detail"] as? String } catch (_: Exception) { null }
                throw CloudException(code, msg ?: resp)
            }
        } finally {
            conn.disconnect()
        }
    }

    fun download(token: String): DownloadResponse {
        return getJsonAuth("/api/v1/sync/download", token)
    }

    fun healthCheck(): Boolean {
        return try {
            val url = URL("$baseUrl/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            AppLogger.log("CLOUD", "Health check failed: ${e.message}")
            false
        }
    }

    // --- HTTP helpers ---
    private inline fun <reified T> postJson(path: String, body: String): T {
        val url = URL("$baseUrl$path")
        AppLogger.log("CLOUD", "POST $path")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader(Charsets.UTF_8).readText()
            AppLogger.log("CLOUD", "Response $code: ${response.take(200)}")

            if (code !in 200..299) {
                val msg = try { gson.fromJson(response, Map::class.java)["detail"] as? String } catch (_: Exception) { null }
                throw CloudException(code, msg ?: response)
            }
            return gson.fromJson(response, T::class.java)
        } finally {
            conn.disconnect()
        }
    }

    private inline fun <reified T> postJsonAuth(path: String, body: String, token: String): T {
        val url = URL("$baseUrl$path")
        AppLogger.log("CLOUD", "POST $path (auth)")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader(Charsets.UTF_8).readText()
            AppLogger.log("CLOUD", "Response $code: ${response.take(200)}")

            if (code !in 200..299) {
                val msg = try { gson.fromJson(response, Map::class.java)["detail"] as? String } catch (_: Exception) { null }
                throw CloudException(code, msg ?: response)
            }
            return gson.fromJson(response, T::class.java)
        } finally {
            conn.disconnect()
        }
    }

    private inline fun <reified T> getJsonAuth(path: String, token: String): T {
        val url = URL("$baseUrl$path")
        AppLogger.log("CLOUD", "GET $path (auth)")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader(Charsets.UTF_8).readText()
            AppLogger.log("CLOUD", "Response $code: ${response.take(200)}")

            if (code !in 200..299) {
                val msg = try { gson.fromJson(response, Map::class.java)["detail"] as? String } catch (_: Exception) { null }
                throw CloudException(code, msg ?: response)
            }
            return gson.fromJson(response, T::class.java)
        } finally {
            conn.disconnect()
        }
    }
}

class CloudException(val statusCode: Int, message: String) : Exception(message)
