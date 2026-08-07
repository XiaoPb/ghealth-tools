package com.ghealth.tools.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackLinksTest {

    @Test
    fun `github issue url 以新建地址开头并携带 title 参数`() {
        val url = FeedbackLinks.githubIssueUrl(FeedbackCategory.BUG, "1.0.0")
        assertTrue(url.startsWith("${FeedbackLinks.GITHUB_ISSUES_BASE}?"))
        assertTrue(url.contains("title="))
    }

    @Test
    fun `github issue url 的 title 包含编码后的类别标签`() {
        val bugUrl = FeedbackLinks.githubIssueUrl(FeedbackCategory.BUG, "1.0.0")
        val otherUrl = FeedbackLinks.githubIssueUrl(FeedbackCategory.OTHER, "1.0.0")
        assertTrue(bugUrl.contains(FeedbackLinks.encode("[问题反馈]")))
        assertTrue(otherUrl.contains(FeedbackLinks.encode("[其他]")))
    }

    @Test
    fun `github issue url 的 body 包含 App 版本`() {
        val url = FeedbackLinks.githubIssueUrl(FeedbackCategory.FEATURE, "2.3.4")
        assertTrue(url.contains(FeedbackLinks.encode("**App 版本**: 2.3.4")))
    }

    @Test
    fun `飞书表单地址保持不变`() {
        assertEquals(
            "https://bcn8ovn2riae.feishu.cn/share/base/form/shrcng4BNicQJWoDADDf4x3j39w",
            FeedbackLinks.FEISHU_FORM_URL
        )
    }
}