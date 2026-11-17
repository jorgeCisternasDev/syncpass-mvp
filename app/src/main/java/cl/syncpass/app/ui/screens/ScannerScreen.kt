package cl.syncpass.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.WalkInDialog
import cl.syncpass.app.ui.scanner.CameraPreview
import cl.syncpass.app.ui.viewmodel.AccreditFeedback
import cl.syncpass.app.ui.viewmodel.ScannerViewModel
import cl.syncpass.app.util.Formatters
import kotlin.math.min

private val ScannerBackground = Color(0xFF07100F)
private val ScannerPanel = Color(0xFF101B1A)

@Composable
fun ScannerScreen(
    eventId: Long,
    useLocation: Boolean,
    onBack: () -> Unit
) {
    val viewModel: ScannerViewModel = appViewModel(key = "scanner-$eventId") { container ->
        ScannerViewModel(
            eventId = eventId,
            eventRepository = container.eventRepository,
            accreditationRepository = container.accreditationRepository,
            locationProvider = container.locationProvider
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var walkInInfo by remember { mutableStateOf<AccreditFeedback.NeedsRegistration?>(null) }
    var walkInSubmitting by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LifecycleResumeEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    LaunchedEffect(state.feedback) {
        val feedback = state.feedback
        if (feedback is AccreditFeedback.NeedsRegistration && walkInInfo == null) {
            walkInInfo = feedback
        }
    }
    LaunchedEffect(state.processing, state.feedback) {
        if (walkInSubmitting && !state.processing) {
            when (state.feedback) {
                is AccreditFeedback.Success, is AccreditFeedback.Duplicate -> {
                    walkInInfo = null
                    walkInSubmitting = false
                }

                is AccreditFeedback.Error -> walkInSubmitting = false
                else -> Unit
            }
        }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }

    val feedback = state.feedback
    val scanAllowed = feedback == null && !state.processing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBackground)
    ) {
        if (hasCameraPermission && cameraError == null) {
            CameraPreview(
                torchOn = torchOn,
                onQrCode = { raw ->
                    if (scanAllowed) viewModel.onCode(raw, useLocation)
                },
                onCameraError = { message ->
                    cameraError = message
                    torchOn = false
                },
                modifier = Modifier.fillMaxSize()
            )
            ScanFrameOverlay()
        }

        if (!hasCameraPermission) {
            CameraPermissionPanel(
                onRequestPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    activity?.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                },
                onManual = { showManual = true }
            )
        } else if (cameraError != null) {
            CameraErrorPanel(
                message = cameraError!!,
                onRetry = {
                    cameraError = null
                    torchOn = false
                },
                onManual = { showManual = true }
            )
        }

        ScannerTopBar(
            eventName = state.event?.name,
            attendedCount = state.attendedCount,
            registeredCount = state.registeredCount,
            torchOn = torchOn,
            torchEnabled = hasCameraPermission && cameraError == null,
            onBack = onBack,
            onToggleTorch = { torchOn = !torchOn }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(18.dp)
        ) {
            if (feedback != null && feedback !is AccreditFeedback.NeedsRegistration) {
                ScannerResultCard(
                    feedback = feedback,
                    onScanAgain = {
                        viewModel.clearFeedback()
                        torchOn = false
                    },
                    onDone = onBack
                )
            } else if (state.processing) {
                ScannerBusyCard()
            } else if (hasCameraPermission && cameraError == null && feedback == null) {
                ScannerBottomPanel(
                    eventName = state.event?.name ?: "Evento",
                    useLocation = useLocation,
                    onManual = { showManual = true }
                )
            }
        }

        if (state.processing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ScannerPanel.copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF9CF1F2)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Validando acreditación…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (showManual) {
        ManualCodeDialog(
            onDismiss = { showManual = false },
            onConfirm = { code ->
                showManual = false
                viewModel.submitManual(code, useLocation)
            }
        )
    }

    walkInInfo?.let { info ->
        WalkInDialog(
            info = info,
            eventName = state.event?.name.orEmpty(),
            saving = walkInSubmitting,
            error = (state.feedback as? AccreditFeedback.Error)?.message,
            onDismiss = {
                if (!walkInSubmitting) {
                    walkInInfo = null
                    viewModel.clearFeedback()
                }
            },
            onConfirm = { name, document, email ->
                walkInSubmitting = true
                viewModel.registerWalkIn(name, document, email, useLocation)
            }
        )
    }
}

@Composable
private fun ScannerTopBar(
    eventName: String?,
    attendedCount: Int,
    registeredCount: Int,
    torchOn: Boolean,
    torchEnabled: Boolean,
    onBack: () -> Unit,
    onToggleTorch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Escanear QR",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!eventName.isNullOrBlank()) {
                Text(
                    text = eventName,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    text = "$attendedCount/$registeredCount acreditados",
                    color = Color(0xFF9CF1F2),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f)
        ) {
            IconButton(onClick = onToggleTorch, enabled = torchEnabled) {
                Icon(
                    imageVector = if (torchOn) Icons.Rounded.FlashOff else Icons.Rounded.FlashOn,
                    contentDescription = if (torchOn) "Apagar linterna" else "Encender linterna",
                    tint = if (torchEnabled) Color.White else Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun ScanFrameOverlay() {
    val transition = rememberInfiniteTransition(label = "scan-line")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan-progress"
    )
    val accent = Color(0xFF9CF1F2)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val frame = min(size.width, size.height) * 0.68f
        val left = (size.width - frame) / 2f
        val top = (size.height - frame) / 2f
        val shade = Color.Black.copy(alpha = 0.52f)
        drawRect(shade, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(
            shade,
            topLeft = Offset(0f, top + frame),
            size = Size(size.width, size.height - top - frame)
        )
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, frame))
        drawRect(
            shade,
            topLeft = Offset(left + frame, top),
            size = Size(size.width - left - frame, frame)
        )

        val corner = 30.dp.toPx()
        val stroke = 4.dp.toPx()
        val white = Color.White
        drawLine(white, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
        drawLine(white, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
        drawLine(white, Offset(left + frame, top), Offset(left + frame - corner, top), stroke, StrokeCap.Round)
        drawLine(white, Offset(left + frame, top), Offset(left + frame, top + corner), stroke, StrokeCap.Round)
        drawLine(white, Offset(left, top + frame), Offset(left + corner, top + frame), stroke, StrokeCap.Round)
        drawLine(white, Offset(left, top + frame), Offset(left, top + frame - corner), stroke, StrokeCap.Round)
        drawLine(
            white,
            Offset(left + frame, top + frame),
            Offset(left + frame - corner, top + frame),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            white,
            Offset(left + frame, top + frame),
            Offset(left + frame, top + frame - corner),
            stroke,
            StrokeCap.Round
        )

        val lineY = top + frame * progress
        drawLine(
            color = accent,
            start = Offset(left + 20.dp.toPx(), lineY),
            end = Offset(left + frame - 20.dp.toPx(), lineY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CameraPermissionPanel(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onManual: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF9CF1F2),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Permiso de cámara",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SyncPass usa la cámara solo para leer el QR de acreditación. " +
                    "El permiso es opcional: también puedes escribir el código.",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Permitir cámara")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Escribir código")
            }
            TextButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Abrir ajustes", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun CameraErrorPanel(message: String, onRetry: () -> Unit, onManual: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFFFB4AB),
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No se pudo abrir la cámara",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                Text("Reintentar cámara")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onManual,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Escribir código")
            }
        }
    }
}

@Composable
private fun ScannerBottomPanel(
    eventName: String,
    useLocation: Boolean,
    onManual: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ScannerPanel.copy(alpha = 0.94f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Apunta al QR del participante",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Evento: $eventName" + if (useLocation) " · ubicación activada" else "",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Escribir código")
            }
        }
    }
}

@Composable
private fun ScannerBusyCard() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ScannerPanel.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF9CF1F2)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Validando…",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ScannerResultCard(
    feedback: AccreditFeedback,
    onScanAgain: () -> Unit,
    onDone: () -> Unit
) {
    val (icon, tint, title, body) = when (feedback) {
        is AccreditFeedback.Success -> ResultContent(
            icon = Icons.Rounded.CheckCircle,
            tint = MaterialTheme.colorScheme.primary,
            title = "Acreditación registrada",
            body = "${feedback.participantName} · ${Formatters.time(feedback.attendedAt)}" +
                if (feedback.hasLocation) " · ubicación guardada" else ""
        )

        is AccreditFeedback.Duplicate -> ResultContent(
            icon = Icons.Rounded.WarningAmber,
            tint = MaterialTheme.colorScheme.tertiary,
            title = "Ya estaba acreditado",
            body = "${feedback.participantName} registró su asistencia a las " +
                Formatters.time(feedback.attendedAt) + ". No se duplicó."
        )

        is AccreditFeedback.Error -> ResultContent(
            icon = Icons.Rounded.ErrorOutline,
            tint = MaterialTheme.colorScheme.error,
            title = "No se pudo acreditar",
            body = feedback.message
        )

        is AccreditFeedback.NeedsRegistration -> ResultContent(
            icon = Icons.Rounded.QrCode2,
            tint = MaterialTheme.colorScheme.primary,
            title = "Persona no inscrita",
            body = "Completa su ficha para acreditarla."
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScanAgain,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Escanear otro")
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .weight(0.7f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Listo")
                }
            }
        }
    }
}

private data class ResultContent(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val body: String
)

@Composable
private fun ManualCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escribir código") },
        text = {
            Column {
                Text(
                    "Ingresa el código SP del participante o su RUT/documento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        error = null
                    },
                    label = { Text("Código o documento") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_manual_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isBlank()) {
                        error = "Escribe un código o documento."
                    } else {
                        onConfirm(code.trim())
                    }
                },
                modifier = Modifier.testTag("scanner_manual_confirm")
            ) {
                Text("Acreditar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

