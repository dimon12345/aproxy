package com.aproxy.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aproxy.app.R
import com.aproxy.app.proxy.ProxyProtocol
import com.aproxy.app.ui.theme.AProxyPalette

@Composable
fun PortDialog(
    protocol: ProxyProtocol,
    currentPort: Int,
    usedPorts: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(currentPort.toString()) }
    var errorRes by rememberSaveable { mutableIntStateOf(0) }

    val error = errorRes.takeIf { it != 0 }
    val confirm = {
        val value = text.toIntOrNull()
        when {
            value == null || value !in 1024..65535 -> errorRes = R.string.port_error
            value in usedPorts -> errorRes = R.string.port_busy
            else -> onConfirm(value)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AProxyPalette.Surface,
        title = {
            Text(
                stringResource(R.string.port_dialog_title, protocol.name),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        text = value.filter(Char::isDigit).take(5)
                        errorRes = 0
                    },
                    label = { Text(stringResource(R.string.port)) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = error?.let { { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = confirm) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
