package com.ghealth.tools.ble.protocol.gh3036

/**
 * GH3036 AGC 物理量编解码。
 *
 * 将芯片原始 AGC 信息（每通道 64 位，拆为 [GhFuncFrame.agcInfo] 低 32 位与
 * [GhFuncFrame.agcInfoHigh] 高 32 位）转换为带物理单位的字段，并按 CSV 位域重新打包。
 *
 * 原始位域（.claude/gh_protocol/c/user/gh_data_package.h 的 gh_agc_upload_t）：
 *   低 32 位 agcL = agcInfo[ch]：
 *     [3:0] gain_code        [5:4] bg_cancel_range   [7:6] dc_cancel_range
 *     [15:8] dc_cancel_code  [23:16] led_drv_fs      [31:24] led_drv0
 *   高 32 位 agcH = agcInfoHigh[ch]：
 *     [7:0] led_drv1
 *
 * CSV 打包位域（仅 GH3036，列值语义改为物理量）：
 *   AGC_INFO_CH 列（int32，低 29 位有效）：
 *     [3:0] gain             [5:4] bg_cancel_level    [7:6] dc_cancel_level
 *     [15:8] dc_cancel_code  [29:16] led_current_sum (0.1mA, drv0+drv1)
 *   LED_INFO_CH 列（int32，低 24 位有效）：
 *     [11:0] led_current_drv0 (0.1mA)   [23:12] led_current_drv1 (0.1mA)
 *
 * LED 电流公式（0.1mA，整数先乘后除保精度）：
 *   led_drv0_ma = 10 * led_drv0 * led_drv_fs / 255
 *   led_drv1_ma = 10 * led_drv1 * led_drv_fs / 255
 *   led_current_sum = led_drv0_ma + led_drv1_ma
 *
 * 位提取统一使用 [ushr]（无符号右移）：agcInfo 元素是有符号 Int，led_drv0 的 bit 7
 * 即 Int bit 31 为 1 时元素为负数，ushr 保证位域提取不被符号扩展污染。
 */
object AgcPhysicalCodec {

    /** 从原始 AGC 值解码出的物理量字段集合。 */
    data class Physical(
        val gain: Int,            // [3:0]   增益等级
        val bgCancelLevel: Int,   // [5:4]   背景抵消等级
        val dcCancelLevel: Int,   // [7:6]   DC 抵消等级
        val dcCancelCode: Int,    // [15:8]  DC 抵消校准码
        val ledCurrentSum: Int,   // [29:16] LED 总电流 (0.1mA)
        val ledCurrentDrv0: Int,  // [11:0]  DRV0 通道电流 (0.1mA)
        val ledCurrentDrv1: Int   // [23:12] DRV1 通道电流 (0.1mA)
    )

    /**
     * 从芯片原始 AGC 值解码并计算物理量。
     *
     * @param agcL agcInfo[ch]，原始低 32 位
     * @param agcH agcInfoHigh[ch]，原始高 32 位
     */
    fun decode(agcL: Int, agcH: Int): Physical {
        val gain = agcL and 0x0F
        val bgCancelLevel = (agcL ushr 4) and 0x03
        val dcCancelLevel = (agcL ushr 6) and 0x03
        val dcCancelCode = (agcL ushr 8) and 0xFF
        val ledDrvFs = (agcL ushr 16) and 0xFF
        val ledDrv0 = (agcL ushr 24) and 0xFF
        val ledDrv1 = agcH and 0xFF
        val ledCurrentDrv0 = ledCurrentMa(ledDrv0, ledDrvFs)
        val ledCurrentDrv1 = ledCurrentMa(ledDrv1, ledDrvFs)
        val ledCurrentSum = ledCurrentDrv0 + ledCurrentDrv1
        return Physical(
            gain, bgCancelLevel, dcCancelLevel, dcCancelCode,
            ledCurrentSum, ledCurrentDrv0, ledCurrentDrv1
        )
    }

    /** led_drv_ma = 10 * led_drv * led_drv_fs / 255（0.1mA，先乘后除保精度）。 */
    private fun ledCurrentMa(ledDrv: Int, ledDrvFs: Int): Int =
        10 * ledDrv * ledDrvFs / 255

    /** 打包为 CSV AGC_INFO_CH 列值（低 29 位有效）。 */
    fun encodeAgcInfoColumn(p: Physical): Int =
        (p.gain and 0x0F) or
            ((p.bgCancelLevel and 0x03) shl 4) or
            ((p.dcCancelLevel and 0x03) shl 6) or
            ((p.dcCancelCode and 0xFF) shl 8) or
            ((p.ledCurrentSum and 0x3FFF) shl 16)

    /** 打包为 CSV LED_INFO_CH 列值（低 24 位有效）。 */
    fun encodeLedInfoColumn(p: Physical): Int =
        (p.ledCurrentDrv0 and 0x0FFF) or
            ((p.ledCurrentDrv1 and 0x0FFF) shl 12)

    /**
     * 批量转换整帧 AGC 通道数据为 CSV 列值。
     *
     * @param agcInfo frame.agcInfo（低 32 位数组）
     * @param agcInfoHigh frame.agcInfoHigh（高 32 位数组，长度应与 agcInfo 相同）
     * @return (packedAgcInfo, packedLedInfo)，分别用于 AGC_INFO_CH / LED_INFO_CH 列
     */
    fun encodeColumns(
        agcInfo: IntArray,
        agcInfoHigh: IntArray
    ): Pair<IntArray, IntArray> {
        val n = maxOf(agcInfo.size, agcInfoHigh.size)
        val packedAgc = IntArray(n)
        val packedLed = IntArray(n)
        for (i in 0 until n) {
            val agcL = agcInfo.getOrElse(i) { 0 }
            val agcH = agcInfoHigh.getOrElse(i) { 0 }
            val physical = decode(agcL, agcH)
            packedAgc[i] = encodeAgcInfoColumn(physical)
            packedLed[i] = encodeLedInfoColumn(physical)
        }
        return Pair(packedAgc, packedLed)
    }
}