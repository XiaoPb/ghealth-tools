package com.ghealth.tools.ble.gh3220

/**
 * GH3220 功能位定义（协议层权威映射）。
 *
 * 位定义来源于 C 端 `.claude/gh3220_protocol/c_to_mcu/demo_kernel_code/driver/inc/gh_drv.h`
 * 的 `GH3X2X_FUNC_OFFSET_*` / `GH3X2X_FUNCTION_*` 宏；
 * 位序与 0x0C/0x10/0x1C 的 function 字段（u32le 位掩码）同源；
 * 0x0B 包头的 FunctionID 字节即该位域的位偏移序号（HR=1）。
 */
enum class Gh3220Function(val bit: Int, val displayName: String) {
    ADT(0, "ADT"),
    HR(1, "HR"),
    HRV(2, "HRV"),
    HSM(3, "HSM"),
    FPBP(4, "FPBP"),
    PWA(5, "PWA"),
    SPO2(6, "SPO2"),
    ECG(7, "ECG"),
    PWTT(8, "PWTT"),
    SOFT_ADT_GREEN(9, "SOFT ADT GREEN"),
    BT(10, "BT"),
    RESP(11, "RESP"),
    AF(12, "AF"),
    TEST1(13, "TEST1"),
    TEST2(14, "TEST2"),
    SOFT_ADT_IR(15, "SOFT ADT IR"),
    RS0(16, "RS0"),
    RS1(17, "RS1"),
    RS2(18, "RS2"),
    LEAD_DET(19, "LEAD DET"),
    ;

    /** 功能位掩码（与 C `GH3X2X_FUNCTION_*` 宏同值）。 */
    val mask: Long get() = 1L shl bit

    companion object {
        /** 全部 20 个功能位取或（0x000FFFFF）。 */
        val allMask: Long = Gh3220Function.entries.fold(0L) { acc, func -> acc or func.mask }

        /** 按位还原功能集合；mask=0 返回空集，未知高位忽略。 */
        fun ofMask(mask: Long): Set<Gh3220Function> =
            Gh3220Function.entries.filter { (mask and it.mask) != 0L }.toSet()
    }
}
