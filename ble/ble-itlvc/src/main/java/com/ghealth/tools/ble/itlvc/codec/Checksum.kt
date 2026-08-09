package com.ghealth.tools.ble.itlvc.codec

/**
 * 校验算法接口。
 *
 * [size] 为校验值固定字节数；[compute] 对给定数据计算校验值。
 * 返回空数组或不实现本接口即表示"无校验"（见 FrameLayout.checksum = null）。
 */
interface Checksum {
    val size: Int
    fun compute(data: ByteArray): ByteArray
}