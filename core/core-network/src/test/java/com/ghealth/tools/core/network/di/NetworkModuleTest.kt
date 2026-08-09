package com.ghealth.tools.core.network.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkModuleTest {

    @Test
    fun `redactSensitive masks login password in json body`() {
        val body = """{"username":"Lierda","password":"Lierda"}"""
        assertEquals("""{"username":"Lierda","password":"***"}""", body.redactSensitive())
    }

    @Test
    fun `redactSensitive masks register password_confirm`() {
        val body = """{"username":"u","password":"p1","password_confirm":"p2"}"""
        assertEquals("""{"username":"u","password":"***","password_confirm":"***"}""", body.redactSensitive())
    }

    @Test
    fun `redactSensitive masks form encoded password`() {
        assertEquals("username=user&password=***", "username=user&password=secret123".redactSensitive())
    }

    @Test
    fun `redactSensitive masks password with spaces in json`() {
        val body = """{"username": "u", "password" : "secret"}"""
        assertEquals("""{"username": "u", "password" : "***"}""", body.redactSensitive())
    }

    @Test
    fun `redactSensitive leaves non sensitive messages unchanged`() {
        val body = """{"username":"Lierda","email":"a@b.com"}"""
        assertEquals(body, body.redactSensitive())
    }
}