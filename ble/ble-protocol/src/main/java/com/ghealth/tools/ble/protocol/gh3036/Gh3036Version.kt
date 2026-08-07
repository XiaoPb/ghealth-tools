package com.ghealth.tools.ble.protocol.gh3036

/**
 * 解析 GH3X_GetVersion 命令响应（格式 `<u8*>`，即 RET_GH3X_GET_VERSION）为可读版本字符串。
 *
 * 响应数据布局：[len_lo, len_hi, ...UTF-8 字节]，len 为小端 16 位长度。
 * - 数据不足 2 字节、长度为 0、长度越界或字符串去除 NUL/空白后为空时返回 "no_ver"。
 * - 自动去除首尾 NUL 与空白（设备可能返回全 0 的空版本缓冲区）。
 */
fun parseGh3036VersionString(data: ByteArray): String {
    if (data.size < 2) return "no_ver"
    val len = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
    if (len == 0 || data.size < 2 + len) return "no_ver"
    val str = String(data.sliceArray(2 until 2 + len), Charsets.UTF_8)
        .trim { it == '\u0000' || it.isWhitespace() }
    return str.ifEmpty { "no_ver" }
}
