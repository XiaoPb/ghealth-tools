package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.itlvc.codec.Crc8
import com.ghealth.tools.ble.itlvc.codec.FrameLayout

/** GH3220 固定帧布局：I=0xAA 0x11，T=1，L=1，V≤238，C=CRC8。 */
object Gh3220Layout {
    val layout: FrameLayout = FrameLayout(
        idBytes = byteArrayOf(0xAA.toByte(), 0x11.toByte()),
        typeBytes = 1,
        lenBytes = 1,
        maxValueLen = 238,
        checksum = Crc8,
    )
}
