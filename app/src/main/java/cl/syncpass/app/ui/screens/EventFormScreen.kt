package cl.syncpass.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.ErrorBanner
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.SectionHeader
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.EventFormViewModel
import cl.syncpass.app.util.Formatters
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun EventFormScreen(
    eventId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val viewModel: EventFormViewModel = appViewModel(key = "event-form-${eventId ?: "new"}") { container ->
        EventFormViewModel(
            eventId = eventId,
            eventRepository = container.eventRepository,
            projectRepository = container.projectRepository,
            customSurveyRepository = container.customSurveyRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var projectMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onDeleted() }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val selectedProject = state.projects.firstOrNull { it.id == state.projectId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SyncPassTopBar(
                title = if (state.isEditing) "Editar evento" else "Nuevo evento",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (state.loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ErrorBanner(state.loadError!!)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Volver") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("event_form_scroll"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = if (state.isEditing) {
                        "Actualiza los datos del evento. Los cambios se guardan en este dispositivo."
                    } else {
                        "Define el evento y sus reglas de acreditación. Todo funciona sin conexión."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nombre del evento *") },
                    placeholder = { Text("Ej. Taller de robótica") },
                    singleLine = true,
                    isError = state.errors.containsKey("name"),
                    supportingText = state.errors["name"]?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_name")
                )
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Descripción") },
                    placeholder = { Text("Qué se hará, quién puede participar…") },
                    minLines = 3,
                    maxLines = 6,
                    isError = state.errors.containsKey("description"),
                    supportingText = {
                        Text(
                            state.errors["description"]
                                ?: "${state.description.length}/600"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.venue,
                    onValueChange = viewModel::setVenue,
                    label = { Text("Recinto o lugar *") },
                    placeholder = { Text("Ej. Sede Alonso de Ribera") },
                    singleLine = true,
                    isError = state.errors.containsKey("venue"),
                    supportingText = state.errors["venue"]?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_venue")
                )
            }
            item {
                OutlinedTextField(
                    value = state.address,
                    onValueChange = viewModel::setAddress,
                    label = { Text("Dirección") },
                    placeholder = { Text("Calle, número, comuna") },
                    singleLine = true,
                    isError = state.errors.containsKey("address"),
                    supportingText = state.errors["address"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Column {
                    Text(
                        text = "Proyecto",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(
                            onClick = { projectMenuOpen = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedProject?.name ?: "Sin proyecto",
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "Elegir proyecto")
                        }
                        DropdownMenu(
                            expanded = projectMenuOpen,
                            onDismissRequest = { projectMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sin proyecto") },
                                onClick = {
                                    viewModel.setProject(null)
                                    projectMenuOpen = false
                                }
                            )
                            state.projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        viewModel.setProject(project.id)
                                        projectMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    if (state.projects.isEmpty()) {
                        Text(
                            text = "Puedes crear proyectos desde Ajustes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    state.errors["project"]?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                SectionHeader(title = "Fecha y hora")
            }
            item {
                DateTimeCard(
                    label = "Inicio",
                    epochMillis = state.startAt,
                    error = state.errors["start"],
                    onDateClick = {
                        val zoned = Instant.ofEpochMilli(state.startAt).atZone(ZoneId.systemDefault())
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> viewModel.setStartDate(year, month + 1, day) },
                            zoned.year,
                            zoned.monthValue - 1,
                            zoned.dayOfMonth
                        ).show()
                    },
                    onTimeClick = {
                        val zoned = Instant.ofEpochMilli(state.startAt).atZone(ZoneId.systemDefault())
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> viewModel.setStartTime(hour, minute) },
                            zoned.hour,
                            zoned.minute,
                            true
                        ).show()
                    }
                )
            }
            item {
                DateTimeCard(
                    label = "Término",
                    epochMillis = state.endAt,
                    error = state.errors["end"],
                    onDateClick = {
                        val zoned = Instant.ofEpochMilli(state.endAt).atZone(ZoneId.systemDefault())
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> viewModel.setEndDate(year, month + 1, day) },
                            zoned.year,
                            zoned.monthValue - 1,
                            zoned.dayOfMonth
                        ).show()
                    },
                    onTimeClick = {
                        val zoned = Instant.ofEpochMilli(state.endAt).atZone(ZoneId.systemDefault())
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> viewModel.setEndTime(hour, minute) },
                            zoned.hour,
                            zoned.minute,
                            true
                        ).show()
                    }
                )
            }
            item {
                InfoBanner(
                    text = "La acreditación abre 2 horas antes del inicio y cierra al finalizar el evento.",
                    icon = Icons.Rounded.Info
                )
            }
            item {
                OutlinedTextField(
                    value = state.capacityText,
                    onValueChange = viewModel::setCapacity,
                    label = { Text("Aforo") },
                    placeholder = { Text("Sin límite") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.errors.containsKey("capacity"),
                    supportingText = {
                        Text(state.errors["capacity"] ?: "Vacío significa sin límite de inscripciones.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("event_capacity")
                )
            }
            item {
                SettingsSwitch(
                    title = "Permitir ingreso sin inscripción",
                    description = "Si alguien no está inscrito, podrás crear su ficha y acreditarlo en el momento.",
                    checked = state.allowWalkIn,
                    onCheckedChange = viewModel::setAllowWalkIn
                )
            }
            item {
                SettingsSwitch(
                    title = "Habilitar encuestas",
                    description = "Solo las personas acreditadas podrán responder la encuesta de satisfacción y utilidad.",
                    checked = state.surveyEnabled,
                    onCheckedChange = viewModel::setSurveyEnabled
                )
            }
            if (!state.isEditing && state.surveyEnabled) {
                item {
                    SectionHeader(
                        title = "Encuesta del evento",
                        subtitle = "Se crea junto al evento y queda ligada a él"
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.surveyTitle,
                        onValueChange = viewModel::setSurveyTitle,
                        label = { Text("Título de la encuesta *") },
                        singleLine = true,
                        isError = state.errors.containsKey("survey_title"),
                        supportingText = state.errors["survey_title"]?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("event_survey_title")
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.surveyDescription,
                        onValueChange = viewModel::setSurveyDescription,
                        label = { Text("Descripción") },
                        minLines = 2,
                        maxLines = 4,
                        isError = state.errors.containsKey("survey_description"),
                        supportingText = state.errors["survey_description"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.surveyQuestions.forEachIndexed { index, question ->
                    item(key = "event_survey_${question.localId}") {
                        QuestionEditorCard(
                            index = index,
                            question = question,
                            error = state.errors["survey_q_$index"],
                            canRemove = state.surveyQuestions.size > 1,
                            onChangePrompt = {
                                viewModel.setSurveyQuestionPrompt(question.localId, it)
                            },
                            onChangeType = {
                                viewModel.setSurveyQuestionType(question.localId, it)
                            },
                            onChangeRequired = {
                                viewModel.setSurveyQuestionRequired(question.localId, it)
                            },
                            onRemove = { viewModel.removeSurveyQuestion(question.localId) }
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = viewModel::addSurveyQuestion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("event_survey_add_question"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar pregunta")
                    }
                }
                item {
                    InfoBanner(
                        text = "La encuesta se libera automáticamente cuando termina el evento. El botón manual queda como excepción.",
                        icon = Icons.Rounded.Info
                    )
                }
            }
            item {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving && !state.deleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("event_save"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Guardando…")
                    } else {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar evento")
                    }
                }
            }
            if (state.isEditing) {
                item {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.saving && !state.deleting,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.deleting) "Eliminando…" else "Eliminar evento")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar este evento?") },
            text = {
                Text(
                    "Se eliminarán también sus participantes, acreditaciones y encuestas. " +
                        "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteEvent()
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun DateTimeCard(
    label: String,
    epochMillis: Long,
    error: String?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDateClick,
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Formatters.date(epochMillis))
                }
                OutlinedButton(
                    onClick = onTimeClick,
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Formatters.time(epochMillis))
                }
            }
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
