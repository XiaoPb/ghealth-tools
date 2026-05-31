package com.ghealth.tools.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun UpdateDialog(
    versionName: String,
    changelog: String,
    isForceUpdate: Boolean,
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
            Text(
                text = buildString {
                    append("最新版本: $versionName")
                    if (changelog.isNotEmpty()) {
                        append("\n\n更新内容:\n$changelog")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("前往下载")
            }
        },
        dismissButton = {
            if (!isForceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text("稍后再说")
                }
            }
        }
    )
}