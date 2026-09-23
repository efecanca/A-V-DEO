package com.levidor.kehribarvideo.data

/** Kullanıcıya HTTP kodundan daha yararlı bir bağlantı açıklaması üretir. */
object ServerConnection {

    fun failureMessage(
        statusCode: Int,
        ngrokErrorCode: String?,
        errorBody: String?
    ): String {
        val ngrokOffline = ngrokErrorCode == "ERR_NGROK_3200" ||
            errorBody.orEmpty().contains("ERR_NGROK_3200", ignoreCase = true)

        return when {
            ngrokOffline ->
                "Ngrok tüneli çevrimdışı. Colab hücresini yeniden çalıştırın ve ekranda verilen yeni adresi kaydedin."
            statusCode == 404 ->
                "Sunucuya ulaşıldı fakat /health bulunamadı. Colab'ın verdiği kök adresi kullanın."
            else -> "Bağlantı yok (HTTP $statusCode)"
        }
    }
}
