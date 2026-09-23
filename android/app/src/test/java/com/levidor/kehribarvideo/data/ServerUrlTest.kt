package com.levidor.kehribarvideo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `ngrok root address keeps one trailing slash`() {
        assertEquals(
            "https://demo.ngrok-free.app/",
            ServerUrl.normalize("  https://demo.ngrok-free.app  ")
        )
    }

    @Test
    fun `copied health address is reduced to backend root`() {
        assertEquals(
            "https://demo.ngrok-free.app/",
            ServerUrl.normalize("https://demo.ngrok-free.app/health?source=browser")
        )
    }

    @Test
    fun `ngrok host without scheme defaults to https`() {
        assertEquals(
            "https://demo.ngrok-free.app/",
            ServerUrl.normalize("demo.ngrok-free.app")
        )
    }

    @Test
    fun `private host without scheme defaults to http`() {
        assertEquals(
            "http://192.168.1.20:8000/",
            ServerUrl.normalize("192.168.1.20:8000")
        )
    }

    @Test
    fun `invalid address is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrl.normalize("ftp://example.com/file")
        }
    }
}
