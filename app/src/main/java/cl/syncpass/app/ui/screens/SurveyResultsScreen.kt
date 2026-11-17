package cl.syncpass.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.SurveyQuestionType
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.EmptyState
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.ReadOnlyStars
import cl.syncpass.app.ui.components.StatusPill
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.SurveyResultsViewModel
import cl.syncpass.app.util.Formatters

@Composable
fun SurveyResultsScreen(
    surveyId: Long,
    onBack: () -> Unit
) {
    val viewModel: SurveyResultsViewModel = appViewModel(key = "survey-results-$surveyId") { container ->
        SurveyResultsViewModel(
            surveyId = surveyId,
            customSurveyRepository = container.customSurveyRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SyncPassTopBar(title = "Resultados", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val survey = state.survey
        if (survey == null) {
            EmptyState(
                icon = Icons.Rounded.FactCheck,
                title = "Encuesta no encontrada",
                message = "Puede que la encuesta o su evento hayan sido eliminados.",
                actionLabel = "Volver",
                onAction = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("survey_results_scroll"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        StatusPill(
                            text = if (survey.definition.isActive) "ACTIVA" else "PAUSADA",
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = survey.definition.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (survey.definition.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = survey.definition.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = survey.eventName ?: "Encuesta general",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "${survey.questionCount} preguntas · ${survey.responseCount} respuestas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = viewModel::toggleActive,
                        enabled = !state.busy,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("survey_toggle_active"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = if (survey.definition.isActive) {
                                    Icons.Rounded.PauseCircle
                                } else {
                                    Icons.Rounded.PlayCircle
                                },
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (survey.definition.isActive) "Pausar" else "Reactivar")
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.busy,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("survey_delete"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Eliminar")
                    }
                }
            }
            item {
                Text(
                    text = "Preguntas",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(state.questions, key = { "question-${it.id}" }) { question ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${question.position + 1}.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(question.prompt, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = SurveyQuestionType.label(question.type) +
                                    if (question.required) " · Obligatoria" else " · Opcional",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Respuestas (${state.responses.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (state.responses.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.FactCheck,
                        title = "Aún no hay respuestas",
                        message = if (survey.definition.isActive) {
                            "Comparte esta encuesta desde «Hacer una encuesta» para recibir respuestas en este dispositivo."
                        } else {
                            "La encuesta está pausada. Reactívala para recibir respuestas."
                        }
                    )
                }
            } else {
                items(state.responses, key = { "response-${it.responseId}" }) { response ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("survey_response_${response.responseId}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = response.respondentName.ifBlank { "Anónimo" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = Formatters.dateTime(response.submittedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            response.answers.forEach { answer ->
                                Text(
                                    text = answer.questionPrompt,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (answer.questionType == SurveyQuestionType.RATING) {
                                    ReadOnlyStars(answer.ratingValue ?: 0)
                                } else {
                                    Text(
                                        text = answer.textValue?.takeIf { it.isNotBlank() }
                                            ?: "Sin respuesta",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            item {
                InfoBanner(
                    text = "Los resultados se calculan directamente de las respuestas guardadas en este dispositivo.",
                    icon = Icons.Rounded.Info
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar la encuesta?") },
            text = {
                Text(
                    "Se eliminarán la encuesta, sus preguntas y todas sus respuestas. " +
                        "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
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
