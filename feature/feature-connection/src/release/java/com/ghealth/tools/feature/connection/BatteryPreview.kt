package com.ghealth.tools.feature.connection

import androidx.compose.runtime.Composable

/**
 * 电池预览占位实现（release 构建编译）：直接透传真实状态，不渲染任何预览 UI。
 * 调试预览仅存在于 src/debug 下的同名实现，因此正式包中不包含任何预览逻辑。
 */
@Composable
internal fun BatteryPreviewScope(
    state: ConnectionUiState,
    content: @Composable (ConnectionUiState) -> Unit,
) {
    content(state)
}