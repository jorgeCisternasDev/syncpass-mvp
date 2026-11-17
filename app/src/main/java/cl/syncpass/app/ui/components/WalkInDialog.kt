package cl.syncpass.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cl.syncpass.app.ui.viewmodel.AccreditFeedback

@Composable
fun WalkInDialog(
    info: AccreditFeedback.NeedsRegistration,
    eventName: String,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by rememberSaveable(info.rawInput) { mutableStateOf("") }
    var document by rememberSaveable(info.rawInput) {
        mutableStateOf(if (info.fromQr) "" else info.rawInput)
    }
    var email by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Persona no inscrita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (info.fromQr) {
                        "El QR no corresponde a un participante inscrito en «$eventName». " +
                            "Puedes crear su ficha y acreditarlo ahora."
                    } else {
                        "«${info.rawInput}» no está inscrito en «$eventName». " +
                            "Puedes crear su ficha y acreditarlo ahora."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre completo *") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("walkin_name")
                )
                OutlinedTextField(
                    value = document,
                    onValueChange = { document = it },
                    label = { Text("RUT o documento *") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("walkin_document")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo (opcional)") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    ErrorBanner(error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, document, email) },
                enabled = !saving && name.isNotBlank() && document.isNotBlank(),
                modifier = Modifier.testTag("walkin_confirm")
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Crear y acreditar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}
