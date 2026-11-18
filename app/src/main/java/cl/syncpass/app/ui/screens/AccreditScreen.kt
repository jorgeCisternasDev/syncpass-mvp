package cl.syncpass.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.AttendanceMethod
import cl.syncpass.app.data.local.AttendanceRow
import cl.syncpass.app.data.local.EventWithStats
import cl.syncpass.app.domain.AccreditationRules
import cl.syncpass.app.domain.AccreditationWindow
import cl.syncpass.app.sync.SyncScheduler
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.EmptyState
import cl.syncpass.app.ui.components.ErrorBanner
import cl.syncpass.app.ui.components.InfoBanner
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.StatusPill
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.components.WalkInDialog
import cl.syncpass.app.ui.viewmodel.AccreditFeedback
import cl.syncpass.app.ui.viewmodel.AccreditViewModel
import cl.syncpass.app.util.CsvBuilder
import cl.syncpass.app.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccreditScreen(
    initialEventId: Long?,
    onBack: (() -> Unit)?,
    onCreateEvent: () -> Unit,
    onScan: (Long, Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: AccreditViewModel = appViewModel(key = "accredit-${initialEventId ?: "auto"}") { container ->
        AccreditViewModel(
            initialEventId = initialEventId,
            eventRepository = container.eventRepository,
            accreditationRepository = container.accreditationRepository,
            locationProvider = container.locationProvider,
            statsDao = container.statsDao,
            connectivityMonitor = container.connectivityMonitor,
            onLocalSave = { SyncScheduler.enqueueOneTime(container.appContext) }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedCode by rememberSaveable { mutableStateOf("") }
    var locationEnabled by rememberSaveable { mutableStateOf(false) }
    var eventMenuOpen by remember { mutableStateOf(false) }
    var showWalkIn by remember { mutableStateOf(false) }
    var walkInInfo by remember { mutableStateOf<AccreditFeedback.NeedsRegistration?>(null) }
    var walkInSubmitting by remember { mutableStateOf(false) }
    var attendanceToUndo by remember { mutableStateOf<AttendanceRow?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.feedback) {
        when (val feedback = state.feedback) {
            is AccreditFeedback.NeedsRegistration -> {
                if (!showWalkIn) {
                    walkInInfo = feedback
                    showWalkIn = true
                }
            }

            is AccreditFeedback.Success -> selectedCode = ""
            else -> Unit
        }
    }
    LaunchedEffect(state.busy, state.feedback) {
        if (walkInSubmitting && !state.busy) {
            when (state.feedback) {
                is AccreditFeedback.Success, is AccreditFeedback.Duplicate -> {
                    showWalkIn = false
                    walkInInfo = null
                    walkInSubmitting = false
                }

                is AccreditFeedback.Error -> walkInSubmitting = false
                else -> Unit
            }
        }
    }
    LaunchedEffect(state.feedback) {
        val message = when (val feedback = state.feedback) {
            is AccreditFeedback.Success ->
                "Acreditación registrada: ${feedback.participantName}"

            is AccreditFeedback.Duplicate ->
                "${feedback.participantName} ya estaba acreditado."

            else -> null
        }
        if (message != null) snackbar.showSnackbar(message)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationEnabled = granted
        if (!granted) {
            scope.launch {
                snackbar.showSnackbar("Permiso de ubicación denegado. Puedes acreditar sin ubicación.")
            }
        }
    }

    fun toggleLocation(enabled: Boolean) {
        if (!enabled) {
            locationEnabled = false
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            locationEnabled = true
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SyncPassTopBar(
                title = "Acreditar asistencia",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Rounded.Logout, contentDescription = "Cerrar sesión")
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
        if (state.events.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Event,
                title = "Primero crea un evento",
                message = "Necesitas un evento para acreditar asistencias y registrar encuestas.",
                actionLabel = "Crear evento",
                onAction = onCreateEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        val event = state.selectedEvent
        val summary = state.events.firstOrNull { it.event.id == event?.id }
        val window = event?.let { AccreditationRules.window(it, now) }
        val canAccredit = window is AccreditationWindow.Open && !state.busy

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("accredit_scroll"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.busy) {
                item {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Guardando en el dispositivo…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                            enabled = !state.busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .testTag("accredit_event_selector"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Event, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event?.name ?: "Selecciona un evento",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (event != null) {
                                    Text(
                                        text = Formatters.schedule(event.startAt, event.endAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "Cambiar evento")
                        }
                        DropdownMenu(
                            expanded = eventMenuOpen,
                            onDismissRequest = { eventMenuOpen = false }
                        ) {
                            state.events.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(item.event.name)
                                            Text(
                                                text = Formatters.schedule(
                                                    item.event.startAt,
                                                    item.event.endAt
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectEvent(item.event.id)
                                        eventMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                val records = if (state.savedRecords == 1) {
                    "1 registro"
                } else {
                    "${state.savedRecords} registros"
                }
                InfoBanner(
                    text = if (state.isOnline) {
                        "En línea"
                    } else {
                        "Sin conexión · $records guardados en el teléfono"
                    },
                    icon = if (state.isOnline) Icons.Rounded.Wifi else Icons.Rounded.CloudOff,
                    containerColor = if (state.isOnline) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    contentColor = if (state.isOnline) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    modifier = Modifier.testTag("accredit_connection_status")
                )
            }
            if (event != null && summary != null) {
                item {
                    SelectedEventSummary(summary = summary)
                }
                item {
                    WindowBanner(window = window, eventName = event.name)
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Escanea el QR del participante",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "La cámara valida que el QR pertenezca a este evento y evita acreditaciones duplicadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { event?.let { onScan(it.id, locationEnabled) } },
                            enabled = canAccredit && event != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("accredit_scan_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Abrir cámara")
                        }
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Código o documento",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Escribe el código SP del participante o su RUT/documento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = selectedCode,
                            onValueChange = { selectedCode = it },
                            label = { Text("Código o documento") },
                            placeholder = { Text("Ej. SPM3K7Q2") },
                            singleLine = true,
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (canAccredit && selectedCode.isNotBlank()) {
                                        viewModel.accredit(selectedCode, locationEnabled)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("accredit_code")
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.accredit(selectedCode, locationEnabled) },
                            enabled = canAccredit && selectedCode.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("accredit_manual_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Acreditar")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (event?.allowWalkIn == true) {
                                "Este evento permite crear una ficha si la persona no está inscrita."
                            } else {
                                "Solo se puede acreditar a personas ya inscritas en este evento."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                LocationCard(
                    enabled = locationEnabled,
                    onToggle = ::toggleLocation,
                    disabled = state.busy
                )
            }
            val feedback = state.feedback
            if (feedback != null && feedback !is AccreditFeedback.NeedsRegistration) {
                item {
                    AccreditFeedbackBanner(
                        feedback = feedback,
                        onClear = { viewModel.clearFeedback() }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Últimas acreditaciones",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.attendance.isNotEmpty()) {
                        StatusPill(
                            text = state.attendance.size.toString(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.attendance.isEmpty()) {
                item {
                    Text(
                        text = "Aún no hay acreditaciones en este evento.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.attendance.take(20), key = { it.attendance.id }) { row ->
                    AccreditAttendanceRow(row = row, onUndo = { attendanceToUndo = row })
                }
            }
        }

        if (showWalkIn && walkInInfo != null) {
            WalkInDialog(
                info = walkInInfo!!,
                eventName = event?.name.orEmpty(),
                saving = walkInSubmitting,
                error = (state.feedback as? AccreditFeedback.Error)?.message,
                onDismiss = {
                    if (!walkInSubmitting) {
                        showWalkIn = false
                        walkInInfo = null
                        viewModel.clearFeedback()
                    }
                },
                onConfirm = { name, document, email ->
                    walkInSubmitting = true
                    viewModel.registerWalkIn(name, document, email, locationEnabled)
                }
            )
        }
    }

    attendanceToUndo?.let { row ->
        AlertDialog(
            onDismissRequest = { attendanceToUndo = null },
            title = { Text("Anular acreditación") },
            text = {
                Text(
                    "Se anulará la asistencia de ${row.participantName} registrada a las " +
                        "${Formatters.time(row.attendance.attendedAt)}."
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
private fun SelectedEventSummary(summary: EventWithStats) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.event.venue,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatters.schedule(summary.event.startAt, summary.event.endAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${summary.attendedCount}/${summary.registeredCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "acreditados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WindowBanner(window: AccreditationWindow?, eventName: String) {
    when (window) {
        is AccreditationWindow.Open -> InfoBanner(
            text = "Acreditación abierta hasta el ${Formatters.dateTime(window.closesAt)}. " +
                "Los datos se guardan localmente.",
            icon = Icons.Rounded.Schedule
        )

        is AccreditationWindow.NotOpen -> InfoBanner(
            text = "La acreditación para «$eventName» abre el " +
                "${Formatters.dateTime(window.opensAt)}, dos horas antes del inicio.",
            icon = Icons.Rounded.AccessTime,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )

        is AccreditationWindow.Closed -> ErrorBanner(
            text = "El evento finalizó el ${Formatters.dateTime(window.closedAt)}. " +
                "Ya no se pueden registrar asistencias, pero puedes revisar los datos."
        )

        null -> Unit
    }
}

@Composable
private fun LocationCard(enabled: Boolean, onToggle: (Boolean) -> Unit, disabled: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Adjuntar ubicación", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Guarda coordenadas de referencia en cada acreditación. Es opcional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = !disabled
            )
        }
    }
}

@Composable
private fun AccreditFeedbackBanner(
    feedback: AccreditFeedback,
    onClear: () -> Unit
) {
    when (feedback) {
        is AccreditFeedback.Success -> {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accredit_success")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Acreditación registrada",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${feedback.participantName} · ${Formatters.time(feedback.attendedAt)}" +
                            if (feedback.hasLocation) " · ubicación guardada" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    TextButton(onClick = onClear) { Text("Acreditar otra persona") }
                }
            }
        }

        is AccreditFeedback.Duplicate -> {
            InfoBanner(
                text = "${feedback.participantName} ya estaba acreditado a las " +
                    "${Formatters.time(feedback.attendedAt)}. No se creó un duplicado.",
                icon = Icons.Rounded.WarningAmber,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.testTag("accredit_duplicate")
            )
        }

        is AccreditFeedback.Error -> ErrorBanner(text = feedback.message)
        is AccreditFeedback.NeedsRegistration -> Unit
    }
}

@Composable
private fun AccreditAttendanceRow(row: AttendanceRow, onUndo: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
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
            }
            IconButton(onClick = onUndo) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Anular acreditación de ${row.participantName}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

