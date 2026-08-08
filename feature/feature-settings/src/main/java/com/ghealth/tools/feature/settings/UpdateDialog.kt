package com.ghealth.tools.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    AlertDialog(
        onDismissRequest = {
            if (!isForceUpdate) onDismiss()
        },
        title = {
            Text(text = if (isForceUpdate) "必须更新" else "发现新版本")
        },
        text = {
            Column {
                Text(
                    text = buildString {
                        append("最新版本: $versionName")
                        if (changelog.isNotEmpty()) {
                            append("\n\n更新内容:\n$changelog")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
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
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("前往下载")
            }
        },
        dismissButton = {
            if (!isForceUpdate) {
                Row {
                    TextButton(onClick = onIgnoreUpdate) {
                        Text("忽略更新")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("稍后再说")
                    }
                }
            }
        }
    )
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
