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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.AttendanceRow
import cl.syncpass.app.data.local.ParticipantStatusRow
import cl.syncpass.app.data.local.SurveyListRow
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.EmptyState
import cl.syncpass.app.ui.components.ErrorBanner
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.MissingEventState
import cl.syncpass.app.ui.components.ReadOnlyStars
import cl.syncpass.app.ui.components.StatusPill
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.EventDetailViewModel
import cl.syncpass.app.util.CsvBuilder
import cl.syncpass.app.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun EventDetailScreen(
    eventId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAccredit: () -> Unit,
    onOpenQr: (Long) -> Unit
) {
    val viewModel: EventDetailViewModel = appViewModel(key = "event-detail-$eventId") { container ->
        EventDetailViewModel(
            eventId = eventId,
            eventRepository = container.eventRepository,
            participantRepository = container.participantRepository,
            accreditationRepository = container.accreditationRepository,
            surveyRepository = container.surveyRepository,
            projectRepository = container.projectRepository,
            exportRepository = container.exportRepository
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var participantToDelete by remember { mutableStateOf<ParticipantStatusRow?>(null) }
    var attendanceToUndo by remember { mutableStateOf<AttendanceRow?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SyncPassTopBar(
                title = "Detalle del evento",
                onBack = onBack,
                actions = {
                    StatusPill(
                        text = "MONITOR",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar evento")
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
        val event = state.event
        val summary = state.eventWithStats
        if (event == null || summary == null) {
            MissingEventState(onBack = onBack)
            return@Scaffold
        }

        val filteredParticipants = state.participants.filter { row ->
            search.isBlank() ||
                row.participant.name.contains(search, ignoreCase = true) ||
                row.participant.document.contains(search, ignoreCase = true) ||
                row.participant.code.contains(search, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("event_detail_scroll"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                EventHeader(
                    title = event.name,
                    description = event.description,
                    venue = event.venue,
                    address = event.address,
                    projectName = state.projectName,
                    startAt = event.startAt,
                    endAt = event.endAt,
                    eventForStatus = event
                )
            }
            item {
                EventStatsCard(
                    registered = summary.registeredCount,
                    attended = summary.attendedCount,
                    capacity = event.capacity,
                    walkInCount = state.walkInCount
                )
            }
            item {
                Button(
                    onClick = onAccredit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("event_detail_accredit"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Acreditar")
                }
            }
            item {
                SurveyReleaseCard(
                    surveyEnabled = event.surveyEnabled,
                    surveyReleased = event.surveyReleased,
                    onRelease = viewModel::releaseSurvey
                )
            }
            item {
                TabRow(
                    selectedTabIndex = selectedTab.coerceIn(0, 1),
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("event_tab_participants"),
                        text = { Text("Participantes (${state.participants.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("event_tab_attendance"),
                        text = { Text("Asistencias (${state.attendance.size})") }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = { Text("Buscar por nombre, documento o código") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("participant_search")
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    viewModel.resetAddState()
                                    showAddDialog = true
                                },
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("add_participant"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Agregar")
                            }
                        }
                    }
                    if (state.participants.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Groups,
                                title = "Aún no hay participantes",
                                message = "Inscribe a las personas para generar su código y su QR de acreditación.",
                                actionLabel = "Inscribir participante",
                                onAction = {
                                    viewModel.resetAddState()
                                    showAddDialog = true
                                }
                            )
                        }
                    } else if (filteredParticipants.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Search,
                                title = "Sin coincidencias",
                                message = "Prueba con otro nombre, documento o código.",
                                actionLabel = "Limpiar búsqueda",
                                onAction = { search = "" }
                            )
                        }
                    } else {
                        items(filteredParticipants, key = { it.participant.id }) { row ->
                            ParticipantCard(
                                row = row,
                                onAccredit = { viewModel.accreditParticipant(row) },
                                onOpenQr = { onOpenQr(row.participant.id) },
                                onDelete = { participantToDelete = row }
                            )
                        }
                    }
                }

                1 -> {
                    if (state.attendance.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.HowToReg,
                                title = "Sin acreditaciones",
                                message = "Usa el botón Acreditar para escanear un QR o escribir el código de un participante."
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "Cada acreditación queda guardada en el dispositivo, incluso sin conexión.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(state.attendance, key = { it.attendance.id }) { row ->
                            AttendanceCard(
                                row = row,
                                onUndo = { attendanceToUndo = row }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddParticipantDialog(
                saving = addState.saving,
                errors = addState.errors,
                message = addState.message,
                successVersion = addState.successVersion,
                onDismiss = {
                    if (!addState.saving) {
                        showAddDialog = false
                        viewModel.resetAddState()
                    }
                },
                onSave = viewModel::addParticipant,
                onSuccess = {
                    showAddDialog = false
                    viewModel.resetAddState()
                    scope.launch { snackbar.showSnackbar("Participante inscrito correctamente.") }
                }
            )
        }
    }

    participantToDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { participantToDelete = null },
            title = { Text("Eliminar participante") },
            text = {
                Text(
                    "¿Eliminar a ${row.participant.name} del evento? " +
                        "Si ya está acreditado, primero debes anular su asistencia."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteParticipant(row.participant.id)
                        participantToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { participantToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    attendanceToUndo?.let { row ->
        AlertDialog(
            onDismissRequest = { attendanceToUndo = null },
            title = { Text("Anular acreditación") },
            text = {
                Text(
                    "Se anulará la asistencia de ${row.participantName} registrada a las " +
                        "${Formatters.time(row.attendance.attendedAt)}. Si tenía encuesta, también se eliminará."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeAttendance(row.attendance.id)
                        attendanceToUndo = null
                    }
                ) {
                    Text("Anular", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { attendanceToUndo = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EventHeader(
    title: String,
    description: String,
    venue: String,
    address: String,
    projectName: String?,
    startAt: Long,
    endAt: Long,
    eventForStatus: cl.syncpass.app.data.local.EventEntity
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cl.syncpass.app.ui.components.EventStatusPill(eventForStatus)
                if (!projectName.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        text = projectName,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
            }
            Spacer(Modifier.height(16.dp))
            HeaderLine(
                icon = Icons.Rounded.Schedule,
                text = Formatters.schedule(startAt, endAt),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            HeaderLine(
                icon = Icons.Rounded.Place,
                text = if (address.isBlank()) venue else "$venue · $address",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HeaderLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = 0.88f)
        )
    }
}

@Composable
private fun EventStatsCard(
    registered: Int,
    attended: Int,
    capacity: Int,
    walkInCount: Int
) {
    val progress = if (registered == 0) 0f else attended.toFloat() / registered.toFloat()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatColumn("Inscritos", registered.toString(), Modifier.weight(1f))
                StatColumn("Acreditados", attended.toString(), Modifier.weight(1f))
                StatColumn(
                    "Aforo",
                    if (capacity > 0) capacity.toString() else "âˆž",
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("${Formatters.percent(attended, registered)} de asistencia")
                    if (walkInCount > 0) append(" · $walkInCount sin inscripción previa")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ParticipantCard(
    row: ParticipantStatusRow,
    onAccredit: () -> Unit,
    onOpenQr: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onAccredit,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials(row.participant.name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.participant.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${row.participant.document} · ${row.participant.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (row.attendanceId != null) {
                        StatusPill(
                            text = "ACREDITADO",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = Icons.Rounded.CheckCircle
                        )
                    } else {
                        StatusPill(
                            text = "INSCRITO",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Rounded.RadioButtonUnchecked
                        )
                    }
                    if (row.participant.isWalkIn) {
                        StatusPill(
                            text = "SIN INSCRIPCIÓN",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            IconButton(onClick = onOpenQr) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "Ver QR de ${row.participant.name}")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Más acciones")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Eliminar participante") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceCard(row: AttendanceRow, onUndo: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.participantName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.time(row.attendance.attendedAt)} · " +
                        CsvBuilder.methodLabel(row.attendance.method),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (row.attendance.latitude != null && row.attendance.longitude != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = String.format(
                                java.util.Locale.US,
                                "%.5f, %.5f",
                                row.attendance.latitude,
                                row.attendance.longitude
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onUndo) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Anular acreditación de ${row.participantName}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SurveyReleaseCard(
    surveyEnabled: Boolean,
    surveyReleased: Boolean,
    onRelease: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Encuesta de satisfacción",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (surveyEnabled) {
                            if (surveyReleased) {
                                "Liberada. Los participantes acreditados ya pueden responder."
                            } else {
                                "Se libera automáticamente al terminar el evento. También puedes liberarla antes."
                            }
                        } else {
                            "Las encuestas están deshabilitadas para este evento."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (surveyReleased) "LIBERADA" else "NO LIBERADA",
                    containerColor = if (surveyReleased) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    contentColor = if (surveyReleased) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
            }
            if (surveyEnabled && !surveyReleased) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRelease,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("survey_release_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Liberar encuesta")
                }
            }
        }
    }
}


@Composable
private fun AddParticipantDialog(
    saving: Boolean,
    errors: Map<String, String>,
    message: String?,
    successVersion: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onSuccess: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var document by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(successVersion) {
        if (successVersion > 0) onSuccess()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Inscribir participante") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Se generará un código único y un QR para acreditar su asistencia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre completo *") },
                    singleLine = true,
                    isError = errors.containsKey("name"),
                    supportingText = errors["name"]?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("participant_name")
                )
                OutlinedTextField(
                    value = document,
                    onValueChange = { document = it },
                    label = { Text("RUT o documento *") },
                    placeholder = { Text("Ej. 12.345.678-9") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = errors.containsKey("document"),
                    supportingText = errors["document"]?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("participant_document")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = errors.containsKey("email"),
                    supportingText = errors["email"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                if (message != null) {
                    ErrorBanner(message)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, document, email) },
                enabled = !saving,
                modifier = Modifier.testTag("participant_save")
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Inscribir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts.first().take(1) + parts[1].take(1)).uppercase()
    }
}
