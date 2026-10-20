package com.ghealth.tools.feature.settings

/**
 * 更新说明弹窗使用的轻量 Markdown 块级模型。
 *
 * 仅覆盖 GitHub Release 正文中常见的语法（标题、列表、引用、代码块、分割线、段落），
 * 不引入第三方 Markdown 依赖。
 */
internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock

    /** [indent] 为缩进层级（每两个前导空格算一层），用于渲染嵌套列表。 */
    data class ListItem(val marker: String, val indent: Int, val text: String) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data class CodeBlock(val code: String) : MarkdownBlock

    data object Divider : MarkdownBlock

    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET_REGEX = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val ORDERED_REGEX = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val DIVIDER_REGEX = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
private val TABLE_SEPARATOR_REGEX =
    Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$""")
private const val INDENT_WIDTH = 2

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.length > 2 && trimmed.startsWith('|') && trimmed.endsWith('|')
}

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

/**
 * 将 Markdown 文本解析为块列表。
 *
 * 解析保持无状态、纯函数，便于单元测试；不处理 HTML 等复杂语法。
 */
internal fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.toString())
            paragraph.clear()
        }
    }

    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flushParagraph()

            isTableRow(line) &&
                index + 1 < lines.size &&
                TABLE_SEPARATOR_REGEX.matches(lines[index + 1].trim()) -> {
                flushParagraph()
                val header = splitTableRow(line)
                val rows = mutableListOf<List<String>>()
                var lastConsumed = index + 1
                index += 2
                while (index < lines.size && isTableRow(lines[index])) {
                    rows += splitTableRow(lines[index])
                    lastConsumed = index
                    index++
                }
                blocks += MarkdownBlock.Table(header, rows)
                index = lastConsumed
            }

            trimmed.startsWith("```") -> {
                flushParagraph()
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code += lines[index].trimEnd()
                    index++
                }
                blocks += MarkdownBlock.CodeBlock(code.joinToString("\n"))
            }

            DIVIDER_REGEX.matches(trimmed) -> {
                flushParagraph()
                blocks += MarkdownBlock.Divider
            }

            HEADING_REGEX.matches(trimmed) -> {
                flushParagraph()
                HEADING_REGEX.find(trimmed)?.let { match ->
                    blocks += MarkdownBlock.Heading(
                        level = match.groupValues[1].length,
                        text = match.groupValues[2].trim(),
                    )
                }
            }

            trimmed.startsWith(">") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(trimmed.removePrefix(">").trim())
            }

            BULLET_REGEX.matches(line) -> {
                flushParagraph()
                BULLET_REGEX.find(line)?.let { match ->
                    blocks += MarkdownBlock.ListItem(
                        marker = "•",
                        indent = match.groupValues[1].length / INDENT_WIDTH,
                        text = match.groupValues[2].trim(),
                    )
                }
            }

            ORDERED_REGEX.matches(line) -> {
                flushParagraph()
                ORDERED_REGEX.find(line)?.let { match ->
                    blocks += MarkdownBlock.ListItem(
                        marker = "${match.groupValues[2]}.",
                        indent = match.groupValues[1].length / INDENT_WIDTH,
                        text = match.groupValues[3].trim(),
                    )
                }
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}
