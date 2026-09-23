package com.levidor.kehribarvideo.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Kullanıcının Ayarlar ekranına yapıştırdığı adresi backend'in kök adresine
 * çevirir. Özellikle tarayıcıdan kopyalanan `/health` adresinin Retrofit
 * tarafından yanlışlıkla `/health/health` yapılmasını önler.
 */
object ServerUrl {

    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl
            .trim()
            .trim('"', '\'')

        require(trimmed.isNotEmpty()) { "Sunucu adresi boş bırakılamaz." }

        val withScheme = if (trimmed.contains("://")) {
            trimmed
        } else {
            "${defaultSchemeFor(trimmed)}://$trimmed"
        }

        val parsed = withScheme.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Geçerli bir HTTP/HTTPS sunucu adresi girin.")

        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "Sunucu adresi http:// veya https:// ile başlamalıdır."
        }

        // Bu projedeki FastAPI uçları daima sunucu kökündedir. Kullanıcı
        // tarayıcıdan .../health ya da .../capabilities kopyalasa bile yalnızca
        // origin'i saklayarak tüm Android çağrılarının aynı sözleşmeyi izlemesini
        // sağlarız.
        return parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    private fun defaultSchemeFor(value: String): String {
        val authority = value.substringBefore('/').substringBefore('?')
        val host = authority.substringBefore(':').lowercase()
        val isPrivateIpv4 = host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
        val isLocal = host == "localhost" || host == "127.0.0.1" ||
            host == "10.0.2.2" || isPrivateIpv4
        return if (isLocal) "http" else "https"
    }
}
