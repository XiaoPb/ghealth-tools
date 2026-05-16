package com.ghealth.tools.feature.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ghealth.tools.ble.protocol.gh3036.CommandGroup
import com.ghealth.tools.ble.protocol.gh3036.CommandMeta
import com.ghealth.tools.ble.protocol.gh3036.Gh3036CommandMeta

@Composable
fun CommandListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "命令操作",
                style = MaterialTheme.typography.titleMedium
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CommandGroup.entries.forEach { group ->
                val commands = Gh3036CommandMeta.getCommandsByGroup(group)
                if (commands.isNotEmpty()) {
                    item {
                        CommandGroupHeader(
                            group = group,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(commands) { command ->
                        CommandItemCard(
                            command = command,
                            onClick = { onNavigateToDetail(command.key) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandGroupHeader(
    group: CommandGroup,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = getGroupIcon(group),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = group.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CommandItemCard(
    command: CommandMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = command.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = command.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (command.hasResponse) {
                    Text(
                        text = "有返回值",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun getGroupIcon(group: CommandGroup): ImageVector = when (group) {
    CommandGroup.DEVICE_CONTROL -> Icons.Default.Settings
    CommandGroup.REGISTER -> Icons.Default.Memory
    CommandGroup.VERSION_STATUS -> Icons.Default.Update
    CommandGroup.TIME -> Icons.Default.Timer
    CommandGroup.FACTORY -> Icons.Default.Verified
    CommandGroup.OTHER -> Icons.Default.Settings
}
