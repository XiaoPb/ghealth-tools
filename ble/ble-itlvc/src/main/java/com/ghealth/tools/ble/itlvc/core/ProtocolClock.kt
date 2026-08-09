package com.ghealth.tools.ble.itlvc.core

/** 时钟抽象，便于注入虚拟时钟做确定性测试。返回值仅用于计算差值；实现应优先使用单调时钟源（墙钟可能回拨）。 */
fun interface ProtocolClock {
    fun now(): Long
}

object SystemClock : ProtocolClock {
    override fun now(): Long = System.currentTimeMillis()
}
