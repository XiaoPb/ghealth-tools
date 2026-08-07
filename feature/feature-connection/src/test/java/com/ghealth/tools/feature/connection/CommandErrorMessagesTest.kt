package com.ghealth.tools.feature.connection

import com.ghealth.tools.ble.protocol.rpccore.ProtocolError
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandErrorMessagesTest {

    @Test
    fun `timeout maps to timeout hint mentioning unsupported command possibility`() {
        val message = userFriendlyCommandError(ProtocolError.Timeout, "F_GetMode")

        assertTrue(message.contains("F_GetMode"))
        assertTrue(message.contains("超时"))
        assertTrue(message.contains("不支持"))
    }

    @Test
    fun `command not found maps to unsupported command hint`() {
        val message = userFriendlyCommandError(ProtocolError.CommandNotFound, "F_GetMode")

        assertTrue(message.contains("不支持该命令"))
    }

    @Test
    fun `channel closed maps to connection hint`() {
        val message = userFriendlyCommandError(ProtocolError.ChannelClosed, "GH3X_GetVersion")

        assertTrue(message.contains("连接"))
    }

    @Test
    fun `unknown error falls back to exception message`() {
        val message = userFriendlyCommandError(RuntimeException("boom"), "F_GetMode")

        assertTrue(message.contains("boom"))
        assertTrue(message.contains("F_GetMode"))
    }

    @Test
    fun `null message falls back to generic failure text`() {
        val message = userFriendlyCommandError(IllegalStateException(), "F_GetMode")

        assertTrue(message.contains("执行失败"))
    }
}
