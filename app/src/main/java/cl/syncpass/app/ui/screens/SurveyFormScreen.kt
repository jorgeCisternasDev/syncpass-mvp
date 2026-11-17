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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.ErrorBanner
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.RatingStars
import cl.syncpass.app.ui.components.StatusPill
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.SurveyFormViewModel
import cl.syncpass.app.util.Formatters

@Composable
fun SurveyFormScreen(
    attendanceId: Long,
    onBack: () -> Unit,
    participantMode: Boolean = false
) {
    val viewModel: SurveyFormViewModel = appViewModel(key = "survey-$attendanceId") { container ->
        SurveyFormViewModel(
            attendanceId = attendanceId,
            surveyRepository = container.surveyRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved && !participantMode) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SyncPassTopBar(
                title = if (state.isEditing) "Editar encuesta" else "Responder encuesta",
                onBack = onBack,
                actions = {
                    if (participantMode) {
                        val participantName = state.context?.participantName.orEmpty()
                        StatusPill(
                            text = if (participantName.isBlank()) {
                                "PARTICIPANTE"
                            } else {
                                "PARTICIPANTE · $participantName"
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (participantMode && state.saved) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "¡Gracias!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tu respuesta quedó registrada. Los indicadores del monitor ya la reflejan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("Volver a mi espacio")
                }
            }
            return@Scaffold
        }
        val context = state.context
        if (context == null || state.loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ErrorBanner(state.loadError ?: "No se encontró la acreditación.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Volver") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .testTag("survey_form_scroll"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = context.eventName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = context.participantName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Acreditado ${Formatters.dateTime(context.attendedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
            item {
                RatingCard(
                    title = "Satisfacción general",
                    question = "¿Qué tan satisfecho o satisfecha estás con el evento?",
                    value = state.satisfaction,
                    onValueChange = viewModel::setSatisfaction,
                    contentDescriptionPrefix = "Satisfacción",
                    error = state.errors["satisfaction"],
                    lowLabel = "Muy insatisfecho",
                    highLabel = "Muy satisfecho"
                )
            }
            item {
                RatingCard(
                    title = "Utilidad",
                    question = "¿Qué tan útil te resultó lo aprendido o vivido?",
                    value = state.usefulness,
                    onValueChange = viewModel::setUsefulness,
                    contentDescriptionPrefix = "Utilidad",
                    error = state.errors["usefulness"],
                    lowLabel = "Nada útil",
                    highLabel = "Muy útil"
                )
            }
            item {
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = viewModel::setComment,
                    label = { Text("Comentario (opcional)") },
                    placeholder = { Text("¿Qué destacarías o qué mejorarías?") },
                    minLines = 4,
                    maxLines = 7,
                    isError = state.errors.containsKey("comment"),
                    supportingText = {
                        Text(state.errors["comment"] ?: "${state.comment.length}/600")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("survey_comment")
                )
            }
            if (state.message != null) {
                item { ErrorBanner(state.message!!) }
            }
            item {
                InfoBanner(
                    text = "Se guarda una sola respuesta por acreditación. Puedes editarla más tarde desde la pestaña Encuestas.",
                    icon = Icons.Rounded.Info
                )
            }
            item {
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("survey_submit"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Guardando…")
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isEditing) "Guardar cambios" else "Enviar encuesta")
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingCard(
    title: String,
    question: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    contentDescriptionPrefix: String,
    error: String?,
    lowLabel: String,
    highLabel: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            RatingStars(
                value = value,
                onValueChange = onValueChange,
                contentDescriptionPrefix = contentDescriptionPrefix
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lowLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = highLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
