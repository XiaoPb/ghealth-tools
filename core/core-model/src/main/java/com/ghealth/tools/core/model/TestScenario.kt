package com.ghealth.tools.core.model

enum class TestScenario(val displayName: String) {
    RESTING("静息状态"),
    EXERCISE("运动状态"),
    SLEEP("睡眠状态"),
    DAILY("日常活动"),
    CLINICAL("临床测试"),
    LABORATORY("实验室测试"),
    OTHER("其他")
}
