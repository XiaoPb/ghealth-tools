package com.ghealth.tools.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownParserTest {

    @Test
    fun `标题按级别解析`() {
        val blocks = parseMarkdown("# 一级\n## 二级\n### 三级")

        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "一级"),
                MarkdownBlock.Heading(2, "二级"),
                MarkdownBlock.Heading(3, "三级"),
            ),
            blocks,
        )
    }

    @Test
    fun `无序列表解析标记与缩进`() {
        val blocks = parseMarkdown("- 第一项\n  - 子项\n* 第二项")

        assertEquals(
            listOf(
                MarkdownBlock.ListItem("•", indent = 0, text = "第一项"),
                MarkdownBlock.ListItem("•", indent = 1, text = "子项"),
                MarkdownBlock.ListItem("•", indent = 0, text = "第二项"),
            ),
            blocks,
        )
    }

    @Test
    fun `有序列表保留序号`() {
        val blocks = parseMarkdown("1. 第一步\n2) 第二步")

        assertEquals(
            listOf(
                MarkdownBlock.ListItem("1.", indent = 0, text = "第一步"),
                MarkdownBlock.ListItem("2.", indent = 0, text = "第二步"),
            ),
            blocks,
        )
    }

    @Test
    fun `代码块保留原始内容且不解析内部语法`() {
        val markdown = "```kotlin\nval a = 1\n**不是粗体**\n```"

        assertEquals(
            listOf(MarkdownBlock.CodeBlock("val a = 1\n**不是粗体**")),
            parseMarkdown(markdown),
        )
    }

    @Test
    fun `连续普通行合并为段落`() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("第一行\n第二行")),
            parseMarkdown("第一行\n第二行"),
        )
    }

    @Test
    fun `引用与分割线`() {
        val blocks = parseMarkdown("> 提示\n\n---")

        assertEquals(
            listOf(
                MarkdownBlock.Quote("提示"),
                MarkdownBlock.Divider,
            ),
            blocks,
        )
    }

    @Test
    fun `空文本返回空列表`() {
        assertTrue(parseMarkdown("  \n \n").isEmpty())
    }
}
