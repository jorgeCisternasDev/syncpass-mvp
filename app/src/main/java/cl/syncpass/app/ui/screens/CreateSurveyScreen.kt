package cl.syncpass.app.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.SurveyQuestionType
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.ErrorBanner
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.SectionHeader
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.CreateSurveyViewModel
import cl.syncpass.app.ui.viewmodel.SurveyQuestionDraft

@Composable
fun CreateSurveyScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: CreateSurveyViewModel = appViewModel { container ->
        CreateSurveyViewModel(
            customSurveyRepository = container.customSurveyRepository,
            eventRepository = container.eventRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var eventMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val selectedEvent = state.events.firstOrNull { it.event.id == state.eventId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SyncPassTopBar(title = "Crear encuesta", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .testTag("create_survey_scroll"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Define el título y las preguntas. Puedes asociarla a un evento o dejarla como encuesta general.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Título de la encuesta *") },
                    placeholder = { Text("Ej. Evaluación del taller") },
                    singleLine = true,
                    isError = state.errors.containsKey("title"),
                    supportingText = state.errors["title"]?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("survey_title")
                )
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Descripción") },
                    placeholder = { Text("Para qué sirve esta encuesta") },
                    minLines = 2,
                    maxLines = 4,
                    isError = state.errors.containsKey("description"),
                    supportingText = {
                        Text(state.errors["description"] ?: "${state.description.length}/500")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Column {
                    Text(
                        text = "Evento",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(
                            onClick = { eventMenuOpen = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("survey_event_selector"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedEvent?.event?.name ?: "Sin evento (general)",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "Elegir evento")
                        }
                        DropdownMenu(
                            expanded = eventMenuOpen,
                            onDismissRequest = { eventMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sin evento (general)") },
                                onClick = {
                                    viewModel.setEvent(null)
                                    eventMenuOpen = false
                                }
                            )
                            state.events.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.event.name) },
                                    onClick = {
                                        viewModel.setEvent(item.event.id)
                                        eventMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    state.errors["event"]?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.events.isEmpty()) {
                        Text(
                            text = "No hay eventos creados. La encuesta quedará como general.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    title = "Preguntas (${state.questions.size})",
                    subtitle = "Máximo 20 preguntas"
                )
            }
            if (state.errors["questions"] != null) {
                item { ErrorBanner(state.errors["questions"]!!) }
            }
            state.questions.forEachIndexed { index, question ->
                item(key = question.localId) {
                    QuestionEditorCard(
                        index = index,
                        question = question,
                        error = state.errors["q_$index"],
                        canRemove = state.questions.size > 1,
                        onChangePrompt = { viewModel.setQuestionPrompt(question.localId, it) },
                        onChangeType = { viewModel.setQuestionType(question.localId, it) },
                        onChangeRequired = { viewModel.setQuestionRequired(question.localId, it) },
                        onRemove = { viewModel.removeQuestion(question.localId) }
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::addQuestion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("add_question"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar pregunta")
                }
            }
            item {
                InfoBanner(
                    text = "Las respuestas se guardan en este dispositivo y se pueden revisar desde la lista de encuestas creadas.",
                    icon = Icons.Rounded.Info
                )
            }
            item {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("create_survey_save"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Creando…")
                    } else {
                        Icon(Icons.Rounded.QuestionAnswer, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Crear encuesta")
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuestionEditorCard(
    index: Int,
    question: SurveyQuestionDraft,
    error: String?,
    canRemove: Boolean,
    onChangePrompt: (String) -> Unit,
    onChangeType: (String) -> Unit,
    onChangeRequired: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("question_card_$index")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Pregunta ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_question_$index")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar pregunta ${index + 1}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = question.prompt,
                onValueChange = onChangePrompt,
                label = { Text("Pregunta *") },
                placeholder = { Text("Ej. ¿Qué te pareció la actividad?") },
                minLines = 2,
                maxLines = 4,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("question_prompt_$index")
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = question.type == SurveyQuestionType.TEXT,
                    onClick = { onChangeType(SurveyQuestionType.TEXT) },
                    label = { Text("Texto corto") },
                    modifier = Modifier.testTag("question_type_text_$index"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                FilterChip(
                    selected = question.type == SurveyQuestionType.RATING,
                    onClick = { onChangeType(SurveyQuestionType.RATING) },
                    label = { Text("Escala 1–5") },
                    modifier = Modifier.testTag("question_type_rating_$index"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Obligatoria", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "La respuesta debe completarse antes de enviar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = question.required,
                    onCheckedChange = onChangeRequired,
                    modifier = Modifier.testTag("question_required_$index")
                )
            }
        }
    }
}
