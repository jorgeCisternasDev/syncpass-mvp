package cl.syncpass.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.domain.AccreditationRules
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.EmptyState
import cl.syncpass.app.ui.components.EventStatusPill
import cl.syncpass.app.ui.components.HorizontalProgress
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.MissingEventState
import cl.syncpass.app.ui.components.ReadOnlyStars
import cl.syncpass.app.ui.components.StatusPill
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.IndicatorsViewModel
import cl.syncpass.app.util.CsvBuilder
import cl.syncpass.app.util.Formatters

@Composable
fun IndicatorsScreen(
    eventId: Long,
    onBack: () -> Unit,
    roleLabel: String = "MONITOR"
) {
    val viewModel: IndicatorsViewModel = appViewModel(key = "indicators-$eventId") { container ->
        IndicatorsViewModel(
            eventId = eventId,
            eventRepository = container.eventRepository,
            surveyRepository = container.surveyRepository,
            exportRepository = container.exportRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SyncPassTopBar(
                title = "Indicadores",
                onBack = onBack,
                actions = {
                    StatusPill(
                        text = roleLabel,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            exportLauncher.launch(CsvBuilder.fileName(state.eventWithStats?.event?.name))
                        },
                        enabled = state.eventWithStats != null && !state.exporting
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Exportar CSV")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val summary = state.eventWithStats
        if (summary == null) {
            MissingEventState(onBack = onBack)
            return@Scaffold
        }
        val event = summary.event
        val registered = summary.registeredCount
        val attended = summary.attendedCount
        val surveyCount = summary.surveyCount
        val satisfaction = state.surveyStats.avgSatisfaction
        val usefulness = state.surveyStats.avgUsefulness

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("indicators_scroll"),
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
                        EventStatusPill(event)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            event.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${event.venue} · ${Formatters.schedule(event.startAt, event.endAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IndicatorTile(
                        value = registered.toString(),
                        label = "Inscritos",
                        icon = Icons.Rounded.Groups,
                        modifier = Modifier.weight(1f)
                    )
                    IndicatorTile(
                        value = attended.toString(),
                        label = "Acreditados",
                        icon = Icons.Rounded.HowToReg,
                        modifier = Modifier.weight(1f)
                    )
                    IndicatorTile(
                        value = surveyCount.toString(),
                        label = "Encuestas",
                        icon = Icons.Rounded.FactCheck,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Cobertura",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(12.dp))
                        RateLine(
                            label = "Tasa de acreditación",
                            percent = Formatters.percent(attended, registered),
                            fraction = if (registered == 0) 0f else attended.toFloat() / registered
                        )
                        Spacer(Modifier.height(14.dp))
                        RateLine(
                            label = "Participación en encuestas",
                            percent = Formatters.percent(surveyCount, attended),
                            fraction = if (attended == 0) 0f else surveyCount.toFloat() / attended
                        )
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        RatingMetric(
                            label = "Satisfacción",
                            value = Formatters.rating(satisfaction),
                            stars = satisfaction?.toInt() ?: 0,
                            modifier = Modifier.weight(1f)
                        )
                        RatingMetric(
                            label = "Utilidad",
                            value = Formatters.rating(usefulness),
                            stars = usefulness?.toInt() ?: 0,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Distribución de satisfacción",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                DistributionCard(state.distribution, satisfaction)
            }
            if (state.surveys.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (roleLabel == "COORDINADOR") {
                                "Encuestas respondidas"
                            } else {
                                "Comentarios recientes"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                items(state.surveys.take(12), key = { it.survey.id }) { survey ->
                    SurveyCommentCard(survey)
                }
            } else {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = "Aún no hay respuestas",
                        message = "Cuando los participantes acreditados respondan la encuesta, verás aquí promedios, distribución y comentarios.",
                        actionLabel = null
                    )
                }
            }
            item {
                InfoBanner(
                    text = "Indicadores calculados en el dispositivo a partir de los datos locales. No se envía información a un servidor.",
                    icon = Icons.Rounded.Info
                )
            }
        }
    }
}

@Composable
private fun IndicatorTile(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RateLine(label: String, percent: String, fraction: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = percent,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
    )
}

@Composable
private fun RatingMetric(
    label: String,
    value: String,
    stars: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(4.dp))
        ReadOnlyStars(stars)
    }
}

@Composable
private fun DistributionCard(
    distribution: List<cl.syncpass.app.data.local.RatingCount>,
    average: Double?
) {
    val counts = (1..5).associateWith { rating ->
        distribution.firstOrNull { it.rating == rating }?.count ?: 0
    }
    val total = counts.values.sum()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (total == 0) {
                Text(
                    text = "Sin respuestas todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                (5 downTo 1).forEach { rating ->
                    val count = counts[rating] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$rating",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(16.dp)
                        )
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        HorizontalProgress(
                            fraction = if (total == 0) 0f else count.toFloat() / total,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(22.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Promedio: ${Formatters.rating(average)} / 5 · $total respuestas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SurveyCommentCard(row: SurveyListRow) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.participantName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                ReadOnlyStars(row.survey.satisfaction)
            }
            if (row.survey.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "“${row.survey.comment}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Utilidad ${row.survey.usefulness}/5 · ${Formatters.dateTime(row.survey.submittedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
