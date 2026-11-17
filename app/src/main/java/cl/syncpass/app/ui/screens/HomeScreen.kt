package cl.syncpass.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.domain.AccreditationRules
import cl.syncpass.app.domain.EventStatus
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.EmptyState
import cl.syncpass.app.ui.components.EventCard
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.MetricTile
import cl.syncpass.app.ui.components.SectionHeader
import cl.syncpass.app.ui.components.StatusPill
import cl.syncpass.app.ui.viewmodel.HomeViewModel

private val filters = listOf("Todos", "En curso", "Próximos", "Finalizados")

@Composable
fun HomeScreen(
    onEventClick: (Long) -> Unit,
    onNewEvent: () -> Unit,
    onAccredit: () -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: HomeViewModel = appViewModel { container ->
        HomeViewModel(
            eventRepository = container.eventRepository,
            projectRepository = container.projectRepository,
            surveyRepository = container.surveyRepository,
            connectivityMonitor = container.connectivityMonitor
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedFilter by rememberSaveable { mutableStateOf("Todos") }

    val filtered = state.events
        .filter { summary ->
            val status = AccreditationRules.status(summary.event, System.currentTimeMillis())
            when (selectedFilter) {
                "En curso" -> status == EventStatus.LIVE
                "Próximos" -> status == EventStatus.SCHEDULED
                "Finalizados" -> status == EventStatus.FINISHED
                else -> true
            }
        }
        .sortedWith(
            compareBy<EventWithStats> {
                when (AccreditationRules.status(it.event, System.currentTimeMillis())) {
                    EventStatus.LIVE -> 0
                    EventStatus.SCHEDULED -> 1
                    EventStatus.FINISHED -> 2
                }
            }.thenBy { it.event.startAt }
        )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { HomeHeader(isOnline = state.isOnline, onLogout = onLogout) },
        floatingActionButton = {
            if (state.events.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onNewEvent,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("home_new_event")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Nuevo evento")
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("home_scroll"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroSummary(
                    totalAttendance = state.totalAttendance,
                    totalParticipants = state.totalParticipants,
                    eventsCount = state.events.size
                )
            }
            item {
                QuickAction(
                    title = "Acreditar",
                    subtitle = "QR o código",
                    icon = Icons.Rounded.QrCodeScanner,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onAccredit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        value = state.events.size.toString(),
                        label = "Eventos",
                        icon = Icons.Rounded.Event,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        value = state.totalParticipants.toString(),
                        label = "Inscritos",
                        icon = Icons.Rounded.Groups,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        value = state.totalAttendance.toString(),
                        label = "Acreditados",
                        icon = Icons.Rounded.HowToReg,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                SectionHeader(
                    title = "Tus eventos",
                    subtitle = if (state.events.size == 1) {
                        "1 evento en este dispositivo"
                    } else {
                        "${state.events.size} eventos en este dispositivo"
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
            if (state.events.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = "Crea tu primer evento",
                        message = "Registra un taller o evento, inscribe participantes y acredita su asistencia incluso sin conexión.",
                        actionLabel = "Crear evento",
                        actionTestTag = "home_new_event",
                        onAction = onNewEvent
                    )
                }
            } else if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Event,
                        title = "Sin eventos en este filtro",
                        message = "Cambia el filtro para ver otros eventos.",
                        actionLabel = "Ver todos",
                        onAction = { selectedFilter = "Todos" }
                    )
                }
            } else {
                items(filtered, key = { it.event.id }) { summary ->
                    EventCard(
                        summary = summary,
                        projectName = summary.event.projectId?.let { state.projects[it]?.name },
                        onClick = { onEventClick(summary.event.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(isOnline: Boolean, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SyncPass",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Acreditación offline para eventos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOnline) {
                StatusPill(
                    text = "EN LÍNEA",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Rounded.Wifi
                )
            } else {
                StatusPill(
                    text = "SIN CONEXIÓN",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = Icons.Rounded.CloudOff
                )
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.Rounded.Logout, contentDescription = "Cerrar sesión")
            }
        }
    }
}

@Composable
private fun HeroSummary(
    totalAttendance: Int,
    totalParticipants: Int,
    eventsCount: Int
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = "CONTROL DE ASISTENCIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = totalAttendance.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (totalAttendance == 1) "acreditación" else "acreditaciones",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroStat("Inscritos", totalParticipants)
                    HeroStat("Eventos", eventsCount)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: Int) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(24.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.78f)
            )
        }
    }
}
