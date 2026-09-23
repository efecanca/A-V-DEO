package com.levidor.kehribarvideo.data

/**
 * Backend'in POST /generate cevabı.
 */
data class GenerateResponse(
    val job_id: String
)

/**
 * Backend'in GET /status/{job_id} cevabı.
 * status: "queued" | "processing" | "completed" | "failed"
 */
data class StatusResponse(
    val status: String,
    val progress: Int? = null,
    val video_url: String? = null,
    val error: String? = null
)

object DefaultPrompts {
    const val POSITIVE: String = "Animate the supplied fashion photograph with subtle natural motion. " +
        "The woman gently turns her head and blinks. Slow cinematic camera push-in. " +
        "Very slight realistic fabric movement. Preserve the scarf exactly as shown in the source image: " +
        "same pattern, colors, border, logo, folds and overall appearance. Preserve clothing, person and background. " +
        "Do not redesign or replace the scarf. No new patterns, no changing colors, no morphing, no new objects."

    const val NEGATIVE: String = "scarf redesign, different scarf, changed pattern, changed colors, changed logo, " +
        "distorted textile, morphing, face distortion, clothing change, flickering, duplicate person, extra limbs, " +
        "text, watermark"
}
