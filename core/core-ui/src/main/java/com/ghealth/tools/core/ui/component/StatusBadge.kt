package com.ghealth.tools.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.unit.dp
import com.ghealth.tools.core.ui.theme.StatusConnected
import com.ghealth.tools.core.ui.theme.StatusConnecting
import com.ghealth.tools.core.ui.theme.StatusDisconnected

enum class ConnectionStatus {
    Connected, Connecting, Disconnected
}

@Composable
fun StatusBadge(
    status: ConnectionStatus,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        ConnectionStatus.Connected -> StatusConnected
        ConnectionStatus.Connecting -> StatusConnecting
        ConnectionStatus.Disconnected -> StatusDisconnected
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}
