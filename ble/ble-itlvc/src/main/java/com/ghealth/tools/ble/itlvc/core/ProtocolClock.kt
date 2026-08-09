package com.ghealth.tools.ble.itlvc.core

/** 时钟抽象，便于注入虚拟时钟做确定性测试。 */
fun interface ProtocolClock {
    fun now(): Long
}

object SystemClock : ProtocolClock {
    override fun now(): Long = System.currentTimeMillis()
}
