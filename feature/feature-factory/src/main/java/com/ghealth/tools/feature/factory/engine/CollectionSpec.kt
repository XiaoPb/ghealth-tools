package com.ghealth.tools.feature.factory.engine

import com.ghealth.tools.feature.factory.model.AppComputeConfig
import com.ghealth.tools.feature.factory.model.TestType

/**
 * App 端计算采集参数：总帧数 = [skipNumber] + [minNumber]，计算只使用最后 [minNumber] 帧；
 * [isContinuous] 为 true 时要求末尾帧号连续。
 */
data class CollectionSpec(
    val minNumber: Int,
    val skipNumber: Int,
    val timeoutMs: Long,
    val isContinuous: Boolean
) {
    companion object {
        const val DEFAULT_MIN_NUMBER = 100
        const val DEFAULT_SKIP_NOISE = 200
        const val DEFAULT_SKIP_CTR = 0
        const val DEFAULT_TIMEOUT_MS = 10_000L

        fun resolve(compute: AppComputeConfig?, testType: TestType): CollectionSpec {
            val isNoise = testType == TestType.BASE_NOISE || testType == TestType.PPG_NOISE
            return CollectionSpec(
                minNumber = compute?.minNumber ?: DEFAULT_MIN_NUMBER,
                skipNumber = compute?.skipNumber ?: if (isNoise) DEFAULT_SKIP_NOISE else DEFAULT_SKIP_CTR,
                timeoutMs = compute?.timeout ?: DEFAULT_TIMEOUT_MS,
                isContinuous = compute?.isContinuous?.let { it == 1 } ?: isNoise
            )
        }
    }
}
