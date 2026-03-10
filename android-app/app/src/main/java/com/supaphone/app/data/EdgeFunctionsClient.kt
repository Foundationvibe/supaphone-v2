package com.supaphone.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.supaphone.app.diagnostics.AppLog
import java.net.HttpURLConnection
import java.net.URL

data class BackendCallResult(
    val ok: Boolean,
    val statusCode: Int,
    val body: String,
)

data class PairingCompleteResult(
    val ok: Boolean,
    val error: String? = null,
    val pairLinkId: String? = null,
)

data class PairedDevice(
    val id: String,
    val label: String,
    val platform: String?,
    val online: Boolean,
    val lastSeenAt: String?,
)

data class RecentPush(
    val id: String,
    val type: String,
    val content: String,
    val status: String,
    val createdAt: String?,
)

object EdgeFunctionsClient {
    private fun normalizeBackendError(raw: String?, fallback: String): String {
        val compact = raw
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (compact.isBlank()) {
            return fallback
        }
        if (Regex("credentials are not registered|is not registered|re-pair and try again", RegexOption.IGNORE_CASE).containsMatchIn(compact)) {
            return "This device is no longer registered with the backend. Pair it again."
        }
        if (Regex("credentials do not match this installation", RegexOption.IGNORE_CASE).containsMatchIn(compact)) {
            return "This device identity no longer matches the backend. Pair the app again."
        }
        if (Regex("failed to connect|failed to fetch|network|timed out|timeout|unable to resolve host", RegexOption.IGNORE_CASE).containsMatchIn(compact)) {
            return "Network issue detected. Check connection and retry."
        }
        return compact
    }

    private fun ensureConfigured() {
        val missing = BackendConfig.missingConfigKeys()
        if (missing.isNotEmpty()) {
            AppLog.w("API_CONFIG_MISSING", missing.joinToString(","))
        }
        check(missing.isEmpty()) {
            "Backend configuration missing: ${missing.joinToString(", ")}. " +
                "Update android-app/local.properties."
        }
    }

    private suspend fun callFunction(functionName: String, requestBody: JSONObject): BackendCallResult =
        withContext(Dispatchers.IO) {
            ensureConfigured()
            AppLog.d("API_CALL_START", "fn=$functionName")

            val endpoint = "${BackendConfig.edgeBaseUrl}/$functionName"
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", BackendConfig.supabaseAnonKey)
                    setRequestProperty("Authorization", "Bearer ${BackendConfig.supabaseAnonKey}")
                }

                connection.outputStream.use { output ->
                    output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                val statusCode = connection.responseCode
                val responseStream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
                val body = responseStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

                if (statusCode in 200..299) {
                    AppLog.d("API_CALL_OK", "fn=$functionName code=$statusCode")
                } else {
                    AppLog.w("API_CALL_FAIL", "fn=$functionName code=$statusCode")
                }

                BackendCallResult(
                    ok = statusCode in 200..299,
                    statusCode = statusCode,
                    body = body
                )
            } finally {
                connection.disconnect()
            }
        }

    suspend fun completePairing(
        code: String,
        phoneClientId: String,
        phoneClientSecret: String,
        phoneLabel: String,
        pushToken: String? = null,
    ): PairingCompleteResult {
        return try {
            val requestBody = JSONObject()
                .put("code", code)
                .put("phoneClientId", phoneClientId)
                .put("phoneClientSecret", phoneClientSecret)
                .put("phoneLabel", phoneLabel)
            if (!pushToken.isNullOrBlank()) {
                requestBody.put("pushToken", pushToken)
            }

            val response = callFunction(
                functionName = "pairing-complete",
                requestBody = requestBody
            )

            val json = JSONObject(response.body)
            if (!response.ok || !json.optBoolean("ok", false)) {
                PairingCompleteResult(
                    ok = false,
                    error = normalizeBackendError(json.optString("error"), "Unable to complete pairing.")
                )
            } else {
                PairingCompleteResult(
                    ok = true,
                    pairLinkId = json.optString("pairLinkId").ifBlank { null }
                )
            }
        } catch (error: Exception) {
            AppLog.e("PAIR_COMPLETE_EXCEPTION", "reason=${error.message}", error)
            PairingCompleteResult(
                ok = false,
                error = normalizeBackendError(error.message, "Unable to complete pairing.")
            )
        }
    }

    suspend fun registerPushToken(
        phoneClientId: String,
        phoneClientSecret: String,
        phoneLabel: String,
        pushToken: String
    ): Result<Unit> {
        return runCatching {
            val response = callFunction(
                functionName = "register-push-token",
                requestBody = JSONObject()
                    .put("phoneClientId", phoneClientId)
                    .put("phoneClientSecret", phoneClientSecret)
                    .put("phoneLabel", phoneLabel)
                    .put("pushToken", pushToken)
            )
            val json = JSONObject(response.body)
            if (!response.ok || !json.optBoolean("ok", false)) {
                error(normalizeBackendError(json.optString("error"), "Unable to register push token."))
            }
            Unit
        }
    }

    suspend fun ackPushEvent(
        eventId: String,
        targetClientId: String,
        targetClientSecret: String,
        status: String,
        ackMessage: String? = null
    ): Result<Unit> {
        return runCatching {
            val request = JSONObject()
                .put("eventId", eventId)
                .put("targetClientId", targetClientId)
                .put("targetClientSecret", targetClientSecret)
                .put("status", status)
            if (!ackMessage.isNullOrBlank()) {
                request.put("ackMessage", ackMessage)
            }

            val response = callFunction(
                functionName = "device-ack",
                requestBody = request
            )
            val json = JSONObject(response.body)
            if (!response.ok || !json.optBoolean("ok", false)) {
                error(normalizeBackendError(json.optString("error"), "Unable to acknowledge push event."))
            }
            Unit
        }
    }

    suspend fun fetchPairedDevices(
        clientId: String,
        clientSecret: String,
        clientType: String
    ): Result<List<PairedDevice>> {
        return runCatching {
            val response = callFunction(
                functionName = "paired-devices",
                requestBody = JSONObject()
                    .put("clientId", clientId)
                    .put("clientSecret", clientSecret)
                    .put("clientType", clientType)
            )
            val json = JSONObject(response.body)
            if (!response.ok || !json.optBoolean("ok", false)) {
                error(normalizeBackendError(json.optString("error"), "Unable to load paired devices."))
            }

            val devicesArray: JSONArray = json.optJSONArray("devices") ?: JSONArray()
            buildList {
                for (index in 0 until devicesArray.length()) {
                    val item = devicesArray.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isBlank()) continue

                    add(
                        PairedDevice(
                            id = id,
                            label = item.optString("label").ifBlank { "Paired Device" },
                            platform = item.optString("platform").ifBlank { null },
                            online = item.optBoolean("online", false),
                            lastSeenAt = item.optString("lastSeenAt").ifBlank { null },
                        )
                    )
                }
            }
        }
    }

    suspend fun renamePairedDevice(
        requesterClientId: String,
        requesterClientSecret: String,
        requesterClientType: String,
        targetClientId: String,
        newLabel: String
    ): Result<Unit> {
        return runCatching {
            val response = callFunction(
                functionName = "rename-device",
                requestBody = JSONObject()
                    .put("requesterClientId", requesterClientId)
                    .put("requesterClientSecret", requesterClientSecret)
                    .put("requesterClientType", requesterClientType)
                    .put("targetClientId", targetClientId)
                    .put("newLabel", newLabel)
            )
            val json = JSONObject(response.body)
            if (!response.ok || !json.optBoolean("ok", false)) {
                error(normalizeBackendError(json.optString("error"), "Unable to rename paired device."))
            }
            Unit
        }
    }

    suspend fun removePairedDevice(
        requesterClientId: String,
        requesterClientSecret: String,
        requesterClientType: String,
        targetClientId: String
    ): Result<Unit> {
        return runCatching {
            val response = callFunction(
                functionName = "remove-paired-device",
                requestBody = JSONObject()
                    .put("requesterClientId", requesterClientId)
                    .put("requesterClientSecret", requesterClientSecret)
                    .put("requesterClientType", requesterClientType)
                    .put("targetClientId", targetClientId)
            )
            val json = JSONObject(response.body)
            if (!response.ok || !json.optBoolean("ok", false)) {
                error(normalizeBackendError(json.optString("error"), "Unable to remove paired device."))
            }
            Unit
        }
    }

    suspend fun fetchRecentPushes(
        clientId: String,
        clientSecret: String,
        limit: Int = 50
    ): Result<List<RecentPush>> {
        return runCatching {
            val response = callFunction(
                functionName = "recent-pushes",
                requestBody = JSONObject()
                    .put("clientId", clientId)
                    .put("clientSecret", clientSecret)
                    .put("limit", limit.coerceIn(1, 100))
            )
            val json = JSONObject(response.body)
            if (!response.ok || !json.optBoolean("ok", false)) {
                error(normalizeBackendError(json.optString("error"), "Unable to load recent pushes."))
            }

            val itemsArray: JSONArray = json.optJSONArray("items") ?: JSONArray()
            buildList {
                for (index in 0 until itemsArray.length()) {
                    val item = itemsArray.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isBlank()) continue

                    add(
                        RecentPush(
                            id = id,
                            type = item.optString("type").ifBlank { "link" },
                            content = item.optString("content").ifBlank { "" },
                            status = item.optString("status").ifBlank { "queued" },
                            createdAt = item.optString("createdAt").ifBlank { null },
                        )
                    )
                }
            }
        }
    }
}
