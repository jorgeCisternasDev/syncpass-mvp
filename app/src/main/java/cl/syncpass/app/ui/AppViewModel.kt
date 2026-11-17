package cl.syncpass.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.syncpass.app.AppContainer
import cl.syncpass.app.SyncPassApp
import androidx.compose.ui.platform.LocalContext

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val app = LocalContext.current.applicationContext as SyncPassApp
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(app.container) }
        }
    )
}
