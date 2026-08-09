package com.ghealth.tools.ble.itlvc.state

import com.ghealth.tools.ble.itlvc.codec.FrameLayout
import com.ghealth.tools.ble.itlvc.codec.ItlvcFrame

/** 丢弃原因（与 core 层错误类型解耦）。 */
enum class DropReason { LENGTH_OVERFLOW, CRC_MISMATCH, TRUNCATED }

/**
 * 字节级接收状态机。按 [FrameLayout] 参数化，支持粘包/分片/坏帧重同步/帧内超时。
 *
 * 状态转移与设备端 `GH3X2X_UprotocolParseBuffer` 对齐：
 * WAIT_HEADER → WAIT_TYPE → WAIT_LEN → WAIT_VALUE →（可选）WAIT_CHECKSUM → 产出帧。
 */
class ReceiveStateMachine(private val layout: FrameLayout) {

    enum class State { WAIT_HEADER, WAIT_TYPE, WAIT_LEN, WAIT_VALUE, WAIT_CHECKSUM }

    var state: State = State.WAIT_HEADER
        private set

    private var idPos = 0
    private val typeBuf = ArrayList<Byte>()
    private val lenBuf = ArrayList<Byte>()
    private val valueBuf = ArrayList<Byte>()
    private val checksumBuf = ArrayList<Byte>()
    private var expectedValueLen = 0
    private var lastByteAt = 0L

    var crcErrorCount: Int = 0
        private set
    var lengthErrorCount: Int = 0
        private set
    var truncatedCount: Int = 0
        private set

    private val drops = ArrayList<DropReason>()

    fun reset() {
        state = State.WAIT_HEADER
        idPos = 0
        typeBuf.clear(); lenBuf.clear(); valueBuf.clear(); checksumBuf.clear()
        expectedValueLen = 0
    }

    /** 帧内字节间隔超时检查；若丢弃半帧返回 true。 */
    fun checkTimeout(nowMs: Long, timeoutMs: Long): Boolean {
        if (state == State.WAIT_HEADER) return false
        if (nowMs - lastByteAt > timeoutMs) {
            drops.add(DropReason.TRUNCATED)
            truncatedCount++
            reset()
            return true
        }
        return false
    }

    /** 消费一批字节，返回完整帧。调用后可用 drainDropReasons() 取本次丢弃原因。 */
    fun feed(data: ByteArray, now: Long): List<ItlvcFrame> {
        val frames = ArrayList<ItlvcFrame>()
        for (b in data) {
            lastByteAt = now
            processByte(b)?.let { frames.add(it) }
        }
        return frames
    }

    /** 取出自上次调用以来的丢弃原因（清空）。 */
    fun drainDropReasons(): List<DropReason> {
        val result = drops.toList()
        drops.clear()
        return result
    }

    private fun processByte(b: Byte): ItlvcFrame? = when (state) {
        State.WAIT_HEADER -> {
            if (b == layout.idBytes[idPos]) {
                idPos++
                if (idPos == layout.idBytes.size) {
                    idPos = 0
                    state = State.WAIT_TYPE
                }
            } else {
                idPos = if (b == layout.idBytes[0]) 1 else 0
            }
            null
        }
        State.WAIT_TYPE -> {
            typeBuf.add(b)
            if (typeBuf.size == layout.typeBytes) state = State.WAIT_LEN
            null
        }
        State.WAIT_LEN -> {
            lenBuf.add(b)
            if (lenBuf.size == layout.lenBytes) {
                val len = lenBuf.fold(0) { acc, x -> (acc shl 8) or (x.toInt() and 0xFF) }
                lenBuf.clear()
                when {
                    len > layout.maxValueLen -> {
                        lengthErrorCount++
                        drops.add(DropReason.LENGTH_OVERFLOW)
                        reset()
                    }
                    len == 0 && layout.checksumLen == 0 -> return emitFrame()
                    len == 0 -> state = State.WAIT_CHECKSUM
                    else -> {
                        expectedValueLen = len
                        state = State.WAIT_VALUE
                    }
                }
            }
            null
        }
        State.WAIT_VALUE -> {
            valueBuf.add(b)
            if (valueBuf.size == expectedValueLen) {
                if (layout.checksumLen == 0) return emitFrame() else state = State.WAIT_CHECKSUM
            }
            null
        }
        State.WAIT_CHECKSUM -> {
            checksumBuf.add(b)
            if (checksumBuf.size == layout.checksumLen) {
                val body = layout.idBytes + typeBuf.toByteArray() +
                    layout.encodeLen(expectedValueLen) + valueBuf.toByteArray()
                val expected = layout.checksum!!.compute(body)
                val received = checksumBuf.toByteArray()
                checksumBuf.clear()
                if (expected.contentEquals(received)) {
                    emitFrame()
                } else {
                    crcErrorCount++
                    drops.add(DropReason.CRC_MISMATCH)
                    reset()
                    null
                }
            } else null
        }
    }

    private fun emitFrame(): ItlvcFrame {
        val frame = ItlvcFrame(typeBuf.toByteArray(), valueBuf.toByteArray())
        reset()
        return frame
    }
}
