package com.levidor.kehribarvideo.data

/**
 * Backend'in POST /generate cevabı.
 * job_ids yalnızca "toplu mod"da (ad_mode kapalı + birden fazla ürün) dolu gelir;
 * bu durumda job_id ilk işin id'sine eşittir (basit istemciler için kolaylık).
 */
data class GenerateResponse(
    val job_id: String,
    val job_ids: List<String>? = null
)

/**
 * Backend'in GET /status/{job_id} cevabı.
 * status: "queued" | "processing" | "completed" | "failed"
 * Yeni alanların hepsi opsiyoneldir (eski sunucularla geriye uyumluluk için).
 */
data class StatusResponse(
    val status: String,
    val progress: Int? = null,
    val video_url: String? = null,
    val error: String? = null,
    val scenes_completed: Int? = null,
    val scenes_total: Int? = null,
    val actual_duration_seconds: Double? = null,
    val mode: String? = null,
    val quality: String? = null,
    val aspect_ratio: String? = null,
    val product_count: Int? = null
)

/**
 * Backend'in GET /capabilities cevabı. Sunucu hangi seçenekleri desteklediğini
 * bildirir; böylece yeni bir kalite/oran/preset eklendiğinde APK'yı yeniden
 * derlemeye gerek kalmaz. Alanlardan biri gelmezse UI, [LocalCapabilityDefaults]
 * içindeki sabit listeye düşer.
 */
data class CapabilitiesResponse(
    val provider: String? = null,
    val modes: List<String>? = null,
    val target_durations_seconds: List<Int>? = null,
    val aspect_ratios: List<String>? = null,
    val quality_presets: List<String>? = null,
    val motion_levels: List<String>? = null,
    val camera_moves: List<String>? = null,
    val style_presets: List<String>? = null,
    val product_protection_default: Boolean? = null,
    val gpu_tier: String? = null
)

/**
 * Backend her zaman erişilemediğinde (ör. Colab henüz başlamadan) UI'ın
 * boş kalmaması için kullanılan sabit varsayılan seçenek listesi.
 * Sunucudan gelen gerçek [CapabilitiesResponse] her zaman önceliklidir.
 */
object LocalCapabilityDefaults {
    val MODES = listOf("image_to_video", "text_to_video")
    val DURATIONS = listOf(5, 10, 15)
    val ASPECT_RATIOS = listOf("16:9", "9:16", "1:1")
    val QUALITIES = listOf("fast", "standard", "high")
    val MOTIONS = listOf("subtle", "natural", "dynamic")
    val CAMERAS = listOf("static", "push_in", "pull_out", "pan_left", "pan_right", "orbit")
    val STYLE_PRESETS = listOf(
        "luxury_fashion", "studio", "modern_architecture", "natural_daylight",
        "urban", "desert_stone", "minimal", "product_closeup"
    )
}

/** Seçilen bir üretim seçeneğini insan-okur Türkçe etikete çevirir (UI için). */
fun optionLabel(key: String): String = when (key) {
    "image_to_video" -> "Fotoğraftan Video"
    "text_to_video" -> "Metinden Video"
    "fast" -> "Hızlı Test"
    "standard" -> "Standart"
    "high" -> "Yüksek"
    "subtle" -> "Çok Hafif"
    "natural" -> "Doğal"
    "dynamic" -> "Dinamik"
    "static" -> "Sabit"
    "push_in" -> "Yavaş Yaklaş"
    "pull_out" -> "Yavaş Uzaklaş"
    "pan_left" -> "Sağdan Sola"
    "pan_right" -> "Soldan Sağa"
    "orbit" -> "Orbit"
    "luxury_fashion" -> "Lüks Moda"
    "studio" -> "Stüdyo"
    "modern_architecture" -> "Modern Mimari"
    "natural_daylight" -> "Doğal Gün Işığı"
    "urban" -> "Şehir"
    "desert_stone" -> "Taş / Çöl"
    "minimal" -> "Minimal"
    "product_closeup" -> "Ürün Yakın Plan"
    else -> key
}
