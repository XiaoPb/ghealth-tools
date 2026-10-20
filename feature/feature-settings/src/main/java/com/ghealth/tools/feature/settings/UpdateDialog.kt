package com.ghealth.tools.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** 更新说明中用于标记强制更新的内部关键字，不展示给用户。 */
private val FORCE_MARKER_REGEX = Regex("""\[force]""", RegexOption.IGNORE_CASE)

@Composable
fun UpdateDialog(
    versionName: String,
    changelog: String,
    isForceUpdate: Boolean,
    useProxyDownload: Boolean,
    onUseProxyChange: (Boolean) -> Unit,
    onIgnoreUpdate: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val renderedChangelog = remember(changelog) {
        changelog.replace(FORCE_MARKER_REGEX, "").trim()
    }

    Dialog(onDismissRequest = { if (!isForceUpdate) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                Text(
                    text = if (isForceUpdate) "必须更新" else "发现新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                // 说明区域高度受限并可滚动，保证下方下载方式与按钮始终可见
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = "最新版本: $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (renderedChangelog.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "更新内容",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        MarkdownText(
                            markdown = renderedChangelog,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    DownloadSourceOption(
                        selected = useProxyDownload,
                        label = "代理下载（默认）",
                        onClick = { onUseProxyChange(true) },
                    )
                    DownloadSourceOption(
                        selected = !useProxyDownload,
                        label = "GitHub 下载",
                        onClick = { onUseProxyChange(false) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!isForceUpdate) {
                        TextButton(onClick = onIgnoreUpdate) {
                            Text("忽略更新")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("稍后再说")
                        }
                    }
                    TextButton(onClick = onDownload) {
                        Text("前往下载")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSourceOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
