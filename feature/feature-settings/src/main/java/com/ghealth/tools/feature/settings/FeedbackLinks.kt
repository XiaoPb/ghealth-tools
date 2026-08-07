package com.ghealth.tools.feature.settings

import java.net.URLEncoder

enum class FeedbackCategory(val label: String, val description: String) {
    BUG("问题反馈", "遇到异常、闪退或功能不工作时选择此项"),
    FEATURE("功能建议", "希望新增功能或改进现有功能时选择此项"),
    OTHER("其他", "以上类别之外的反馈内容")
}

object FeedbackLinks {
    const val GITHUB_ISSUES_BASE = "https://github.com/XiaoPb/ghealth-tools/issues/new"
    const val FEISHU_FORM_URL =
        "https://bcn8ovn2riae.feishu.cn/base/Fpu8b0Ikwa0bnbssYwjc1vvYnYD?from=from_copylink"

    fun githubIssueUrl(category: FeedbackCategory, appVersion: String): String {
        val title = "[${category.label}] 反馈"
        val body = buildString {
            appendLine("**类别**: ${category.label}")
            appendLine("**App 版本**: $appVersion")
            appendLine()
            appendLine("**内容**:")
            appendLine()
            appendLine("**复现步骤（可选）**:")
            appendLine()
            appendLine("**期望行为（可选）**:")
        }
        return "$GITHUB_ISSUES_BASE?title=${encode(title)}&body=${encode(body)}"
    }

    internal fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}