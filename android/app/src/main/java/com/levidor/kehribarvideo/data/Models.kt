package com.levidor.kehribarvideo.data

/** Backend'in GET /health cevabı. */
data class HealthResponse(
    val status: String
)

/**
 * Backend'in POST /generate cevabı.
 * job_ids yalnızca "toplu mod"da (ad_mode kapalı + birden fazla ürün) dolu gelir;
 * bu durumda job_id ilk işin id'sine eşittir (basit istemciler için kolaylık).
 */
data class ReferenceResponse(
    val job_id: String
)

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
    val stage: String? = null,
    val stage_detail: String? = null,
    val progress: Int? = null,
    val video_url: String? = null,
    val reference_url: String? = null,
    val image_url: String? = null,
    val error: String? = null,
    val scenes_completed: Int? = null,
    val scenes_total: Int? = null,
    val actual_duration_seconds: Double? = null,
    val mode: String? = null,
    val quality: String? = null,
    val aspect_ratio: String? = null,
    val product_count: Int? = null,
    val project_id: String? = null,
    val result_id: String? = null,
    val media_type: String? = null,
    val provider: String? = null,
    val provider_attempts: List<String>? = null,
    val qc: QcReportResponse? = null
)

data class QcReportResponse(
    val overall_score: Int? = null,
    val color_match: String? = null,
    val motif_match: String? = null,
    val border_match: String? = null,
    val logo_match: String? = null,
    val detected_changes: List<String>? = null,
    val recommendation: String? = null,
    val disclaimer: String? = null
)

data class StudioGenerateResponse(
    val job_id: String,
    val project_id: String
)

data class ApprovalResponse(
    val result_id: String,
    val approved: Boolean
)

data class StudioMessageResponse(
    val id: String,
    val role: String,
    val text: String,
    val result_id: String? = null,
    val created_at: Double? = null
)

data class StudioResultResponse(
    val id: String,
    val media_type: String,
    val media_url: String? = null,
    val prompt: String? = null,
    val provider: String? = null,
    val qc: QcReportResponse? = null,
    val approved: Boolean = false,
    val parent_result_id: String? = null,
    val auto_corrections: Int = 0,
    val created_at: Double? = null
)

data class ProjectResponse(
    val id: String,
    val name: String,
    val created_at: Double,
    val updated_at: Double,
    val status: String,
    val product_url: String? = null,
    val model_url: String? = null,
    val thumbnail_url: String? = null,
    val messages: List<StudioMessageResponse> = emptyList(),
    val results: List<StudioResultResponse> = emptyList()
)

data class ProjectsResponse(val projects: List<ProjectResponse> = emptyList())

data class GalleryItemResponse(
    val id: String,
    val project_id: String,
    val project_name: String,
    val media_type: String,
    val media_url: String? = null,
    val prompt: String? = null,
    val provider: String? = null,
    val approved: Boolean = false,
    val created_at: Double? = null
)

data class GalleryResponse(val items: List<GalleryItemResponse> = emptyList())

/** Backend aşamasını kullanıcıya gösterilecek Türkçe metne dönüştürür. */
fun generationStageLabel(stage: String?, progress: Int? = null): String {
    val label = when (stage) {
        "uploading" -> "Fotoğraf yükleniyor…"
        "queued" -> "GPU sırasında bekleniyor…"
        "model_downloading" -> "Yapay zekâ modeli indiriliyor…"
        "model_loading" -> "Yapay zekâ modeli yükleniyor…"
        "quantizing" -> "Model INT8 için optimize ediliyor…"
        "reference_model_loading" -> "Mankenli görsel modeli hazırlanıyor…"
        "reference_generating" -> "Mankenli görsel oluşturuluyor…"
        "reference_completed" -> "Mankenli görsel hazır"
        "product_analyzing" -> "Ürün analiz ediliyor…"
        "scene_preparing" -> "Manken sahnesi hazırlanıyor…"
        "product_applying" -> "Eşarp uygulanıyor…"
        "image_enhancing" -> "Görsel iyileştiriliyor…"
        "quality_checking" -> "Kalite kontrolü yapılıyor…"
        "generating" -> "Video oluşturuluyor…"
        "encoding" -> "Video kodlanıyor…"
        "completed" -> "Video hazır"
        "failed" -> "Video üretimi başarısız oldu"
        else -> "Video işleniyor…"
    }
    val realProgress = progress?.coerceIn(0, 100)
    return if (realProgress != null) "$label %$realProgress" else label
}

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
    val gpu_tier: String? = null,
    val studio: StudioCapabilitiesResponse? = null
)

data class StudioCapabilitiesResponse(
    val providers: List<StudioProviderResponse> = emptyList(),
    val image_aspect_ratios: List<String> = emptyList(),
    val veo_durations_seconds: List<Int> = emptyList(),
    val stages: List<String> = emptyList(),
    val requires_image_approval_before_video: Boolean = true
)

data class StudioProviderResponse(
    val name: String,
    val display_name: String,
    val capabilities: List<String> = emptyList(),
    val available: Boolean = false,
    val quota_state: String? = null,
    val cost_tier: Int? = null,
    val remote: Boolean = false
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
        "luxury_scarf_campaign", "urban", "desert_stone", "minimal", "product_closeup"
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
    "luxury_scarf_campaign" -> "Lüks Eşarp"
    "studio" -> "Stüdyo"
    "modern_architecture" -> "Modern Mimari"
    "natural_daylight" -> "Doğal Gün Işığı"
    "urban" -> "Şehir"
    "desert_stone" -> "Taş / Çöl"
    "minimal" -> "Minimal"
    "product_closeup" -> "Ürün Yakın Plan"
    else -> key
}
