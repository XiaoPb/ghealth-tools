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

    @Test
    fun `表格解析表头与数据行`() {
        val markdown = """
            | 项目 | 值 |
            | --- | --- |
            | 版本 | `v0.8.6` |
            | 提交数量 | 4 |
        """.trimIndent()

        assertEquals(
            listOf(
                MarkdownBlock.Table(
                    header = listOf("项目", "值"),
                    rows = listOf(
                        listOf("版本", "`v0.8.6`"),
                        listOf("提交数量", "4"),
                    ),
                ),
            ),
            parseMarkdown(markdown),
        )
    }

    @Test
    fun `v086 发布说明可完整解析`() {
        val blocks = parseMarkdown(RELEASE_BODY_V086)

        // 表格不应被拆成包含竖线的普通段落
        assertTrue(blocks.none { it is MarkdownBlock.Paragraph && it.text.contains('|') })
        assertTrue(blocks.none { it is MarkdownBlock.Paragraph && it.text.contains("---") })

        val table = blocks.filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(listOf("项目", "值"), table.header)
        assertEquals(9, table.rows.size)

        assertEquals(4, blocks.count { it is MarkdownBlock.Heading && it.level == 2 })
        assertEquals(4, blocks.count { it is MarkdownBlock.Heading && it.level == 3 })
        assertEquals(4, blocks.filterIsInstance<MarkdownBlock.ListItem>().size)
        assertTrue(
            blocks.any {
                it is MarkdownBlock.Paragraph && it.text.contains("[v0.8.5...v0.8.6]")
            },
        )
    }

    private companion object {
        val RELEASE_BODY_V086 = """
            ## 版本信息

            | 项目 | 值 |
            | --- | --- |
            | 版本 | `v0.8.6` |
            | 版本号 | `0.8.6` |
            | 提交 | `7ac4dce` |
            | 提交数量 | 4 |
            | 构建时间 | 2026-10-20 15:51 UTC |
            | 运行环境 | Android 7.0+ (minSdk 24) |
            | 编译环境 | compileSdk 37 / JDK 17 |
            | 安装包大小 | 4.9M |
            | SHA-256 | `e59cae7cf6cc8923c3162dbaa8f0b563e38f36c78fca1412dbbeab37ac693cc1` |

            ## 变更内容

            ### 新功能

            - feat(feature-ota): support Nordic SMP firmware upgrade (`489fe13`)

            ### 构建与依赖

            - build: compile against API 37 and use mcumgr-ble 3.4.1 (`58c9223`)

            ### CI/CD

            - ci(release): generate release notes from commit history (`a00d47a`)

            ### 其他改动

            - chore(app): bump version to 0.8.6 (`7ac4dce`)

            ## 贡献者

            XiaoPb

            ## 对比

            [v0.8.5...v0.8.6](https://github.com/XiaoPb/ghealth-tools/compare/v0.8.5...v0.8.6)
        """.trimIndent()
    }
}
