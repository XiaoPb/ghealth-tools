package com.ghealth.tools.core.ui.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun ErrorEffect(
    errorMessage: String?,
    onDismiss: () -> Unit,
    useToast: Boolean = false
) {
    val context = LocalContext.current
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            if (useToast) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
            onDismiss()
        }
    }
}