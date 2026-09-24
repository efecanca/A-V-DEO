package com.levidor.kehribarvideo.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionTest {

    @Test
    fun `ngrok offline response is explained instead of generic 404`() {
        val message = ServerConnection.failureMessage(
            statusCode = 404,
            ngrokErrorCode = "ERR_NGROK_3200",
            errorBody = null
        )

        assertTrue(message.contains("tüneli çevrimdışı"))
    }

    @Test
    fun `backend 404 points to health route`() {
        val message = ServerConnection.failureMessage(404, null, "Not Found")

        assertTrue(message.contains("/health"))
    }

    @Test
    fun `ngrok upstream failure is not described as gpu option rejection`() {
        val message = ServerConnection.generationFailureMessage(
            statusCode = 502,
            ngrokErrorCode = "ERR_NGROK_8012",
            errorBody = null
        )

        assertTrue(message.contains("FastAPI"))
        assertTrue(message.contains("ERR_NGROK_8012"))
        assertTrue(!message.contains("kalite/süre"))
    }

    @Test
    fun `generate 404 points to current root url instead of gpu`() {
        val message = ServerConnection.generationFailureMessage(404, null, "Not Found")

        assertTrue(message.contains("/generate"))
        assertTrue(message.contains("güncel kök adresi"))
        assertTrue(!message.contains("GPU"))
    }

    @Test
    fun `backend feasibility detail and suggestion are shown for 400`() {
        val body = """{
            "detail": {
                "message": "Bu seçim T4 için güvenli değil.",
                "suggestion": {"quality": "fast", "target_duration_seconds": 5}
            }
        }""".trimIndent()

        val message = ServerConnection.generationFailureMessage(400, null, body)

        assertTrue(message.contains("Bu seçim T4"))
        assertTrue(message.contains("kalite=fast"))
        assertTrue(message.contains("süre=5 sn"))
    }

    @Test
    fun `backend job 404 is distinguishable from stale ngrok url`() {
        assertTrue(
            ServerConnection.isMissingJob(
                404,
                """{"detail":"job_id bulunamadı."}"""
            )
        )
    }
}
