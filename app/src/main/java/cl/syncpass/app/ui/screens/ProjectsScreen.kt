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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.data.local.ProjectWithCount
import cl.syncpass.app.ui.appViewModel
import cl.syncpass.app.ui.components.EmptyState
import cl.syncpass.app.ui.components.LoadingState
import cl.syncpass.app.ui.components.SyncPassTopBar
import cl.syncpass.app.ui.viewmodel.ProjectsViewModel
import kotlinx.coroutines.launch

@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    val viewModel: ProjectsViewModel = appViewModel { container ->
        ProjectsViewModel(container.projectRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ProjectWithCount?>(null) }
    var projectToDelete by remember { mutableStateOf<ProjectWithCount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.saved.collect {
            showDialog = false
            editingProject = null
            snackbar.showSnackbar("Proyecto guardado.")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SyncPassTopBar(title = "Proyectos", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingProject = null
                    showDialog = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Nuevo proyecto") },
                modifier = Modifier.testTag("add_project")
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Los proyectos agrupan eventos relacionados. Un evento puede quedar sin proyecto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.projects.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Workspaces,
                        title = "Aún no hay proyectos",
                        message = "Crea un proyecto para organizar tus talleres y eventos por línea de trabajo.",
                        actionLabel = "Crear proyecto",
                        onAction = {
                            editingProject = null
                            showDialog = true
                        }
                    )
                }
            } else {
                items(state.projects, key = { it.project.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.project.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.project.description.isNotBlank()) {
                                    Text(
                                        text = item.project.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (item.eventCount == 1) "1 evento" else "${item.eventCount} eventos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingProject = item
                                    showDialog = true
                                }
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Editar ${item.project.name}")
                            }
                            IconButton(
                                onClick = { projectToDelete = item },
                                modifier = Modifier.testTag("project_delete_${item.project.id}")
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Eliminar ${item.project.name}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ProjectDialog(
            project = editingProject?.project,
            onDismiss = {
                showDialog = false
                editingProject = null
            },
            onSave = { name, description ->
                viewModel.save(editingProject?.project?.id, name, description)
            }
        )
    }

    projectToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Eliminar proyecto") },
            text = {
                Text(
                    "¿Eliminar «${item.project.name}»? Sus ${item.eventCount} eventos no se eliminarán, " +
                        "pero quedarán sin proyecto asignado."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(item.project.id)
                        projectToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ProjectDialog(
    project: cl.syncpass.app.data.local.ProjectEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var description by rememberSaveable(project?.id) { mutableStateOf(project?.description.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) "Nuevo proyecto" else "Editar proyecto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Nombre *") },
                    placeholder = { Text("Ej. Vinculación con el medio") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = { Text("Descripción") },
                    minLines = 2,
                    maxLines = 4,
                    supportingText = { Text("${description.length}/300") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        name.trim().length < 2 -> error = "El nombre debe tener al menos 2 caracteres."
                        name.trim().length > 80 -> error = "El nombre no puede superar 80 caracteres."
                        else -> onSave(name, description)
                    }
                },
                modifier = Modifier.testTag("project_save")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
