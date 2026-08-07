package com.ghealth.tools.feature.factory.compute

import kotlin.math.PI
import kotlin.math.tan

/**
 * 7 阶 Butterworth 0.5Hz 高通滤波器。
 *
 * 实现：双线性变换 + 频率预畸变（prewarping），级联 1 个一阶节 + 3 个二阶节（biquad）。
 * 系数推导（归一化 Butterworth 极点，模拟截止 1 rad/s）：
 * - 一阶节：实极点 -1，H(s) = s / (s + ωa)
 * - 二阶节：极点对 Re ± jIm，H(s) = s² / (s² - 2·Re·ωa·s + ωa²)
 * - 预畸变截止频率 ωa = 2·fs·tan(π·fc/fs)
 *
 * [filter] 的 [initialX] 参数用于消除直流阶跃瞬态：仅将第一节的输入延时状态初始化为
 * 输入均值，直流分量从第一个样本起即被精确抵消（各节 b0+b1(+b2)=0）；下游各节
 * 实际输入为前级输出（直流已去除），保持零初态即可，避免约 2^23 LSB 的大直流偏置
 * 在 0.5Hz 高通上产生持续数秒的启动瞬态。
 */
class HighPassFilter(
    sampleRateHz: Int,
    private val cutoffHz: Double = 0.5,
    order: Int = 7
) {
    init {
        require(order == 7) { "仅支持 7 阶滤波器，实际 $order" }
        require(sampleRateHz > 0) { "采样率必须为正" }
        require(cutoffHz > 0 && cutoffHz < sampleRateHz / 2.0) { "截止频率必须大于 0 且小于奈奎斯特频率" }
    }

    private data class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double
    )

    private val sections: List<Biquad>

    init {
        val c = 2.0 * sampleRateHz
        val omegaA = 2.0 * sampleRateHz * tan(PI * cutoffHz / sampleRateHz)

        val firstOrder = Biquad(
            b0 = c / (c + omegaA),
            b1 = -c / (c + omegaA),
            b2 = 0.0,
            a1 = (omegaA - c) / (c + omegaA),
            a2 = 0.0
        )

        // 7 阶 Butterworth 共轭极点对的（实部, 虚部），归一化单位圆左半平面
        val polePairs = listOf(
            -0.2225209340 to 0.9749279122,
            -0.6234898019 to 0.7818314825,
            -0.9009688679 to 0.4338837391
        )

        sections = listOf(firstOrder) + polePairs.map { (re, _) ->
            val p = -2.0 * re * omegaA
            val q = omegaA * omegaA
            val d = c * c + p * c + q
            Biquad(
                b0 = c * c / d,
                b1 = -2.0 * c * c / d,
                b2 = c * c / d,
                a1 = (-2.0 * c * c + 2.0 * q) / d,
                a2 = (c * c - p * c + q) / d
            )
        }
    }

    /**
     * 高通滤波输入序列。
     *
     * @param initialX 第一节输入延时状态初始值（仅第一节；下游各节保持零初态），传输入均值可消除直流阶跃瞬态。
     * @return 与输入等长的滤波结果
     */
    fun filter(input: DoubleArray, initialX: Double = 0.0): DoubleArray {
        val output = DoubleArray(input.size)
        val n = sections.size
        val x1 = DoubleArray(n)
        val x2 = DoubleArray(n)
        x1[0] = initialX
        x2[0] = initialX
        val y1 = DoubleArray(n)
        val y2 = DoubleArray(n)

        for (i in input.indices) {
            var x = input[i]
            for (s in 0 until n) {
                val b = sections[s]
                val y = b.b0 * x + b.b1 * x1[s] + b.b2 * x2[s] - b.a1 * y1[s] - b.a2 * y2[s]
                x2[s] = x1[s]
                x1[s] = x
                y2[s] = y1[s]
                y1[s] = y
                x = y
            }
            output[i] = x
        }
        return output
    }
}
