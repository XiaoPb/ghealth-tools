package com.ghealth.tools.ble.protocol

import com.ghealth.tools.ble.protocol.gh3036.Gh3036FrameDecoder
import com.ghealth.tools.ble.protocol.rpccore.FrameParser
import com.ghealth.tools.ble.protocol.rpccore.unpackU8Array
import org.junit.jupiter.api.Test
import java.io.File

class GProtocolValidationTest {

    @Test
    fun `validate G protocol parsing against Rust reference output`() {
        val rawLogPath = "E:/Code/CPP/combridge-rust/libs/protocol_rust/test/test_data/ble_raw_F51817331068_2026-05-18.log"

        val parser = FrameParser()
        val decoder = Gh3036FrameDecoder()

        var totalLines = 0
        var totalGFrames = 0
        var totalDecodedFrames = 0
        var frameIndexGlobal = 0

        println("========================================")
        println("  Kotlin G Protocol Validation")
        println("========================================")

        File(rawLogPath).forEachLine { line ->
            totalLines++

            val hexStart = line.lastIndexOf(": ")
            if (hexStart < 0) return@forEachLine

            val hexStr = line.substring(hexStart + 2)
            val hexBytes = hexStr.split(" ")
                .filter { it.isNotBlank() }
                .mapNotNull { it.toIntOrNull(16)?.toByte() }
                .toByteArray()

            if (hexBytes.isEmpty()) return@forEachLine

            val timestamp = if (line.length >= 12) line.substring(0, 12) else "??:??:??.???"

            val results = parser.process(hexBytes)

            for (result in results) {
                if (result.isFailure) {
                    println("[$timestamp] 行$totalLines PARSE ERROR: ${result.exceptionOrNull()?.message}")
                    continue
                }

                val parsed = result.getOrNull() ?: continue

                if (parsed.key == "G" && parsed.param.isNotEmpty()) {
                    totalGFrames++

                    val unpacked = unpackU8Array(parsed.param)
                    if (unpacked.isEmpty()) {
                        println("[$timestamp] 行$totalLines [WARN] unpackU8Array returned empty")
                        continue
                    }

                    val frames = decoder.decode(unpacked.toByteArray())
                    println("[$timestamp] 行$totalLines key=G param_len=${parsed.param.size} secure=${parsed.isSecure} fin=${parsed.isFin} invoke_idx=${parsed.invokeIdx} frame_idx=${parsed.frameIdx}")
                    println("[$timestamp] 解码到 ${frames.size} 个G帧")

                    totalDecodedFrames += frames.size
                    for ((_, frame) in frames.withIndex()) {
                        frameIndexGlobal++
                        println("")
                        println("========== G回调 (行$totalLines, G帧#${frameIndexGlobal}) ==========")
                        println("帧计数: ${frame.frameCnt}, 时间戳: ${frame.timestamp}, ID: ${frame.funcId}")
                        println("通道数: ${chNum(frame)}, 最大通道: 32")

                        // G传感器数据
                        val gsData = frame.gsData
                        if (gsData.size >= 3) {
                            println("G传感器: acc=[${gsData[0]}, ${gsData[1]}, ${gsData[2]}]")
                        } else {
                            println("G传感器: acc=[0, 0, 0] (insufficient data)")
                        }

                        // 通道数据
                        val frameChNum = chNum(frame)
                        for (ch in 0 until frameChNum) {
                            val ipdPa = frame.phyValue.getOrElse(ch) { 0 }
                            val rawdata = frame.rawdata.getOrElse(ch) { 0 }
                            val flag = frame.flags.getOrElse(ch) { 0 }

                            val ledAdj = (flag and 0x01) != 0
                            val sa = ((flag shr 1) and 0x01) != 0
                            val paramChange = ((flag shr 2) and 0x01) != 0
                            val dreUpdate = ((flag shr 3) and 0x01) != 0
                            val skipOk = ((flag shr 4) and 0x01) != 0

                            println("  通道[$ch]: ipd_pa=$ipdPa, rawdata=$rawdata, flag={led_adj:$ledAdj, sa:$sa, param_change:$paramChange, dre_update:$dreUpdate, skip_ok:$skipOk}")

                            // AGC info
                            val agcL = frame.agcInfo.getOrElse(ch) { 0 }
                            val agcH = frame.agcInfoHigh.getOrElse(ch) { 0 }
                            val gainCode = agcL and 0x0F
                            val bgCancelRange = (agcL shr 4) and 0x03
                            val dcCancelRange = (agcL shr 6) and 0x03
                            val dcCancelCode = (agcL shr 8) and 0xFF
                            val ledDrvFs = (agcL shr 16) and 0xFF
                            val ledDrv0 = (agcL shr 24) and 0xFF
                            val ledDrv1 = agcH and 0xFF
                            println("  AGC: gain_code=$gainCode, bg_cancel_range=$bgCancelRange, dc_cancel_range=$dcCancelRange, dc_cancel_code=$dcCancelCode, led_drv0=$ledDrv0, led_drv1=$ledDrv1, bg_cancel_code=0, tia_gain=0")
                        }

                        // 帧级别 led_drv_fs
                        val ledDrvFs0 = if (frame.agcInfo.isNotEmpty()) {
                            (frame.agcInfo[0] shr 16) and 0xFF
                        } else 0
                        println("帧级别: led_drv_fs=[$ledDrvFs0, $ledDrvFs0]")

                        // algo data
                        if (frame.algoData.isNotEmpty()) {
                            println("algo_data: ${frame.algoData.toList()} (${frame.algoData.size} values)")
                        }
                        println("===========================")
                    }
                }
            }
        }

        println("")
        println("========================================")
        println("  解析统计")
        println("========================================")
        println("  总行数:           $totalLines")
        println("  G帧总数:          $totalGFrames")
        println("  G帧解码总数:      $totalDecodedFrames")
        println("========================================")
    }
}

private fun chNum(frame: com.ghealth.tools.ble.protocol.gh3036.GhFuncFrame): Int {
    return maxOf(frame.rawdata.size, frame.phyValue.size)
}
