package com.ghealth.tools.ble.gh3220

import com.ghealth.tools.ble.itlvc.codec.FrameLayout

/** GH3220 固定帧布局：直接引用通用框架的 GH3220 默认布局（I=0xAA 0x11，T=1，L=1，V≤238，C=CRC8）。 */
object Gh3220Layout {
    val layout: FrameLayout
        get() = FrameLayout.GH3220
}
