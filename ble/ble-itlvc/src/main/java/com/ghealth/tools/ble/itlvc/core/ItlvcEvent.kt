package com.ghealth.tools.ble.itlvc.core

import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame
import com.ghealth.tools.ble.itlvc.state.SessionState

/** ITLVC 会话事件。 */
sealed interface ItlvcEvent {
    data class FrameReceived(val frame: ItlvcFrame) : ItlvcEvent
    data class FrameDropped(val error: ItlvcError.FrameError) : ItlvcEvent
    data class CommandCompleted(val type: ByteArray, val result: Result<ByteArray>) : ItlvcEvent
    data class SessionChanged(val state: SessionState) : ItlvcEvent
}
