package com.ghealth.tools.core.datastore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionPasswordAlgorithmTest {

    @Test
    fun `API 24 and 25 use the supported SHA1 PBKDF2 provider`() {
        assertEquals("PBKDF2WithHmacSHA1", sessionPasswordAlgorithm(24))
        assertEquals("PBKDF2WithHmacSHA1", sessionPasswordAlgorithm(25))
    }

    @Test
    fun `API 26 and newer use SHA256 PBKDF2`() {
        assertEquals("PBKDF2WithHmacSHA256", sessionPasswordAlgorithm(26))
        assertEquals("PBKDF2WithHmacSHA256", sessionPasswordAlgorithm(35))
    }
}
