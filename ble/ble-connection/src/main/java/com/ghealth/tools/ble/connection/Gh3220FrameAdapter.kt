package com.ghealth.tools.ble.connection

import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataFrame
import com.ghealth.tools.ble.gh3220.rawdata.Gh3220RawDataPackage
import com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame
import com.ghealth.tools.ble.protocol.gh3036.GhFuncId

/**
 * 把新 ITLVC GH3220 帧映射为演示层既有的 [GhFuncFrame]，列对齐 .claude/csv_rules/gh3220.yaml：
 * rawdata→CH{0-31}、acc→ACCX/Y/Z、agc→AGC_INFO_CH{0-31}、amb→AMB_CH{0-15}、results→ALGO_RESULT{0-15}。
 */
object Gh3220FrameAdapter {

    fun toGhFuncFrame(frame: Gh3220RawDataFrame): GhFuncFrame = GhFuncFrame().apply {
        funcId = GhFuncId.from(frame.funcId)
        frameCnt = frame.frameId
        timestamp = System.currentTimeMillis()
        rawdata = frame.rawdata ?: IntArray(0)
        gsData = frame.acc ?: IntArray(0)
        agcInfo = frame.agc ?: IntArray(0)
        phyValue = frame.amb ?: IntArray(0)
        algoData = frame.results.map { it.value }.toIntArray()
    }

    fun toGhFuncFrames(pkg: Gh3220RawDataPackage): List<GhFuncFrame> =
        pkg.frames.map { toGhFuncFrame(it) }
}
