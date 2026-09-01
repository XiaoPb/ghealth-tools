package com.ghealth.tools.core.datastore

enum class SessionMode {
    NONE,
    OFFLINE,
    ONLINE,
}

internal fun activeChipFor(
    mode: SessionMode,
    projectChip: String,
    offlineChip: String,
): String = when (mode) {
    SessionMode.ONLINE -> projectChip
    SessionMode.OFFLINE -> offlineChip
    SessionMode.NONE -> ""
}
