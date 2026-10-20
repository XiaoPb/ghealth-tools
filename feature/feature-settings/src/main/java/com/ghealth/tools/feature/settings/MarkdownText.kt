package com.ghealth.tools.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val INLINE_REGEX = Regex(
    pattern = listOf(
        """!\[([^\]]*)]\(([^)\s]+)\)""",
        """\[([^\]]+)]\(([^)\s]+)\)""",
        """\*\*(.+?)\*\*""",
        """__(.+?)__""",
        """`([^`]+)`""",
        """~~(.+?)~~""",
        """\*(.+?)\*""",
        """_(.+?)_""",
    ).joinToString("|"),
    options = setOf(RegexOption.DOT_MATCHES_ALL),
)

/**
 * 将单行（或段落）内的 Markdown 语法转换为带样式的文本。
 *
 * 支持：链接、图片（仅显示替代文字）、粗体、行内代码、删除线、斜体。
 */
internal fun buildInlineMarkdown(
    text: String,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    appendInlineMarkdown(text, linkColor, codeBackground)
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    linkColor: Color,
    codeBackground: Color,
) {
    var index = 0
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }

    while (index < text.length) {
        val match = INLINE_REGEX.find(text, index)
        if (match == null) {
            plain.append(text, index, text.length)
            break
        }
        if (match.range.first > index) {
            plain.append(text, index, match.range.first)
        }
        flushPlain()
        val values = match.groupValues
        when {
            // 图片：无法内联渲染位图，退化为替代文字
            values[2].isNotEmpty() -> append(values[1])

            values[4].isNotEmpty() -> withLink(LinkAnnotation.Url(values[4])) {
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    appendInlineMarkdown(values[3], linkColor, codeBackground)
                }
            }

            values[5].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlineMarkdown(values[5], linkColor, codeBackground)
            }

            values[6].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlineMarkdown(values[6], linkColor, codeBackground)
            }

            values[7].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
            ) {
                append(values[7])
            }

            values[8].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlineMarkdown(values[8], linkColor, codeBackground)
            }

            values[9].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlineMarkdown(values[9], linkColor, codeBackground)
            }

            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlineMarkdown(values[10], linkColor, codeBackground)
            }
        }
        index = match.range.last + 1
    }
    flushPlain()
}

/**
 * 渲染轻量 Markdown 文本。
 *
 * 调用方负责外层滚动与最大高度限制（例如 [androidx.compose.foundation.verticalScroll]）。
 */
@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = buildInlineMarkdown(block.text, linkColor, codeBackground),
                        style = style,
                    )
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = buildInlineMarkdown(block.text, linkColor, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                )

                is MarkdownBlock.ListItem -> Row(
                    modifier = Modifier.padding(start = (block.indent * 12).dp),
                ) {
                    Text(
                        text = block.marker,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .widthIn(min = 18.dp)
                            .padding(end = 4.dp),
                    )
                    Text(
                        text = buildInlineMarkdown(block.text, linkColor, codeBackground),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                is MarkdownBlock.Quote -> Text(
                    text = buildInlineMarkdown(block.text, linkColor, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )

                is MarkdownBlock.CodeBlock -> Text(
                    text = block.code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(codeBackground, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                )

                is MarkdownBlock.Table -> MarkdownTableView(block, linkColor, codeBackground)

                MarkdownBlock.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun MarkdownTableView(
    table: MarkdownBlock.Table,
    linkColor: Color,
    codeBackground: Color,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
    ) {
        MarkdownTableRow(
            cells = table.header,
            isHeader = true,
            headerBackground = headerBackground,
            linkColor = linkColor,
            codeBackground = codeBackground,
        )
        table.rows.forEach { row ->
            HorizontalDivider(color = borderColor)
            MarkdownTableRow(
                cells = row,
                isHeader = false,
                headerBackground = headerBackground,
                linkColor = linkColor,
                codeBackground = codeBackground,
            )
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    isHeader: Boolean,
    headerBackground: Color,
    linkColor: Color,
    codeBackground: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHeader) headerBackground else Color.Transparent),
    ) {
        cells.forEachIndexed { column, cell ->
            Text(
                text = buildInlineMarkdown(cell, linkColor, codeBackground),
                style = if (isHeader) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.bodySmall
                },
                modifier = Modifier
                    .weight(if (column == 0) 0.45f else 1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
