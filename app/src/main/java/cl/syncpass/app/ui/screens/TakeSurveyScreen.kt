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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.PendingSurveyRow
import cl.syncpass.app.data.local.SurveyDefinitionWithStats
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.EmptyState
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.ReadOnlyStars
import cl.syncpass.app.ui.components.SectionHeader
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.TakeSurveyViewModel
import cl.syncpass.app.util.Formatters

@Composable
fun TakeSurveyScreen(
    onBack: () -> Unit,
    onCreateSurvey: () -> Unit,
    onAnswerSatisfaction: (Long) -> Unit,
    onAnswerCustom: (Long) -> Unit
) {
    val viewModel: TakeSurveyViewModel = appViewModel { container ->
        TakeSurveyViewModel(
            customSurveyRepository = container.customSurveyRepository,
            surveyRepository = container.surveyRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var autoSelected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.loading, state.pendingSatisfaction.size, state.availableDefinitions.size) {
        if (!autoSelected && !state.loading &&
            state.pendingSatisfaction.isEmpty() &&
            state.availableDefinitions.isNotEmpty()
        ) {
            selectedTab = 1
            autoSelected = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SyncPassTopBar(title = "Hacer una encuesta", onBack = onBack) }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("take_survey_scroll"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Elige una encuesta disponible. Las respuestas se guardan sin conexión.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("take_tab_satisfaction"),
                        text = { Text("Satisfacción (${state.pendingSatisfaction.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("take_tab_created"),
                        text = { Text("Creadas (${state.availableDefinitions.size})") }
                    )
                }
            }

            if (selectedTab == 0) {
                if (state.pendingSatisfaction.isEmpty() && state.completedSatisfaction.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.CheckCircle,
                            title = "No hay encuestas de satisfacción",
                            message = "Cuando acredites participantes en un evento con encuestas habilitadas, aparecerán aquí."
                        )
                    }
                } else {
                    if (state.pendingSatisfaction.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Pendientes (${state.pendingSatisfaction.size})",
                                subtitle = "Solo para personas ya acreditadas"
                            )
                        }
                        val grouped = state.pendingSatisfaction.groupBy { it.eventName }
                        grouped.forEach { (eventName, pending) ->
                            item(key = "header-$eventName") {
                                PendingEventHeader(eventName = eventName, count = pending.size)
                            }
                            items(pending, key = { "pending-${it.attendanceId}" }) { row ->
                                PendingSurveyCard(
                                    row = row,
                                    onAnswer = { onAnswerSatisfaction(row.attendanceId) }
                                )
                            }
                        }
                    } else {
                        item {
                            InfoBanner(
                                text = "No hay encuestas de satisfacción pendientes.",
                                icon = Icons.Rounded.Info
                            )
                        }
                    }

                    if (state.completedSatisfaction.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Respondidas (${state.completedSatisfaction.size})",
                                subtitle = "Puedes editar una respuesta existente",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(state.completedSatisfaction, key = { "completed-${it.survey.id}" }) { row ->
                            CompletedSurveyCard(
                                row = row,
                                onEdit = { onAnswerSatisfaction(row.survey.attendanceId) }
                            )
                        }
                    }
                }
            } else {
                if (state.availableDefinitions.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.FactCheck,
                            title = "No hay encuestas creadas activas",
                            message = "Crea una encuesta con tus propias preguntas para responderla desde aquí.",
                            actionLabel = "Crear encuesta",
                            actionTestTag = "take_survey_create_empty",
                            onAction = onCreateSurvey
                        )
                    }
                } else {
                    items(state.availableDefinitions, key = { it.definition.id }) { survey ->
                        AvailableSurveyCard(
                            survey = survey,
                            onAnswer = { onAnswerCustom(survey.definition.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingEventHeader(eventName: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = eventName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count pendientes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PendingSurveyCard(row: PendingSurveyRow, onAnswer: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.FactCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.participantName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = row.participantDocument,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = " Acreditado ${Formatters.shortDateTime(row.attendedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onAnswer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("survey_pending_${row.attendanceId}")
            ) {
                Text("Responder")
            }
        }
    }
}

@Composable
private fun CompletedSurveyCard(row: SurveyListRow, onEdit: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.participantName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = row.eventName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("survey_edit_${row.survey.attendanceId}")
                ) {
                    Text("Editar")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    Text(
                        text = "Satisfacción",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReadOnlyStars(row.survey.satisfaction)
                }
                Column {
                    Text(
                        text = "Utilidad",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReadOnlyStars(row.survey.usefulness)
                }
            }
            if (row.survey.comment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "“${row.survey.comment}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = Formatters.dateTime(row.survey.submittedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun AvailableSurveyCard(survey: SurveyDefinitionWithStats, onAnswer: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_survey_${survey.definition.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = survey.definition.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = survey.eventName ?: "Encuesta general",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (survey.definition.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = survey.definition.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (survey.questionCount == 1) "1 pregunta" else "${survey.questionCount} preguntas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (survey.responseCount == 1) "1 respuesta" else "${survey.responseCount} respuestas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onAnswer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("custom_survey_take_${survey.definition.id}")
                ) {
                    Text("Responder")
                }
            }
        }
    }
}
