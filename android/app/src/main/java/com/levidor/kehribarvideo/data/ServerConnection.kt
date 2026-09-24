package com.levidor.kehribarvideo.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Kullanıcıya HTTP kodundan daha yararlı bir bağlantı açıklaması üretir. */
object ServerConnection {

    fun failureMessage(
        statusCode: Int,
        ngrokErrorCode: String?,
        errorBody: String?,
        endpoint: String = "/health"
    ): String {
        val body = errorBody.orEmpty()
        val code = ngrokErrorCode.orEmpty()
        val ngrokOffline = code.equals("ERR_NGROK_3200", ignoreCase = true) ||
            body.contains("ERR_NGROK_3200", ignoreCase = true) ||
            body.contains("endpoint is offline", ignoreCase = true)
        val ngrokUpstreamUnavailable = code.equals("ERR_NGROK_8012", ignoreCase = true) ||
            body.contains("ERR_NGROK_8012", ignoreCase = true)

        return when {
            ngrokOffline ->
                "Ngrok tüneli çevrimdışı. Colab hücresindeki YENİ SUNUCU ADRESİ'ni Ayarlar'a kaydedin."
            ngrokUpstreamUnavailable ->
                "Ngrok açık, ancak Colab'daki FastAPI backend'ine ulaşamıyor " +
                    "(ERR_NGROK_8012). Colab günlüğünde backend'in kapanıp kapanmadığını kontrol edin."
            statusCode == 404 ->
                "Sunucuya ulaşıldı fakat $endpoint bulunamadı. " +
                    "Colab'ın verdiği güncel kök adresi kullanın."
            else -> "Bağlantı yok (HTTP $statusCode)"
        }
    }

    fun generationFailureMessage(
        statusCode: Int,
        ngrokErrorCode: String?,
        errorBody: String?
    ): String {
        if (statusCode == 400) {
            return backendDetail(errorBody)
                ?: "Seçilen kalite/süre bu GPU için desteklenmiyor."
        }
        return failureMessage(statusCode, ngrokErrorCode, errorBody, endpoint = "/generate")
    }

    fun isMissingJob(statusCode: Int, errorBody: String?): Boolean =
        statusCode == 404 && errorBody.orEmpty().contains("job_id bulunamadı", ignoreCase = true)

    private fun backendDetail(errorBody: String?): String? {
        val raw = errorBody?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val detail = JsonParser.parseString(raw).asJsonObject.get("detail")
            when {
                detail?.isJsonPrimitive == true ->
                    detail.asString.takeIf { it.isNotBlank() }
                detail?.isJsonObject == true -> {
                    val detailObject = detail.asJsonObject
                    val message = detailObject.stringOrNull("message")
                    val suggestion = detailObject.get("suggestion")
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                    val suggestedQuality = suggestion?.stringOrNull("quality")
                        ?.takeIf { it.isNotBlank() }
                    val suggestedDuration = suggestion?.get("target_duration_seconds")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asInt
                        ?.takeIf { it > 0 }
                    buildString {
                        message?.let { append(it) }
                        if (suggestedQuality != null || suggestedDuration != null) {
                            if (isNotEmpty()) append(" ")
                            append("\u00d6neri:")
                            suggestedQuality?.let { append(" kalite=$it") }
                            suggestedDuration?.let { append(" süre=${it} sn") }
                        }
                    }.takeIf { it.isNotBlank() }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
}
