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
}
