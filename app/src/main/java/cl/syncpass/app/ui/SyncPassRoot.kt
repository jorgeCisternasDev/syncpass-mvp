package cl.syncpass.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.syncpass.app.AppContainer
import cl.syncpass.app.auth.AppRole
import cl.syncpass.app.ui.navigation.SyncPassNavHost
import cl.syncpass.app.ui.screens.CoordinatorHomeScreen
import cl.syncpass.app.ui.screens.LoginScreen
import cl.syncpass.app.ui.screens.ParticipantHomeScreen
import cl.syncpass.app.ui.viewmodel.AuthViewModel

@Composable
fun SyncPassRoot(container: AppContainer) {
    val authViewModel: AuthViewModel = appViewModel { app ->
        AuthViewModel(app.authRepository)
    }
    val user by authViewModel.user.collectAsStateWithLifecycle()
    val error by authViewModel.error.collectAsStateWithLifecycle()
    val loading by authViewModel.loading.collectAsStateWithLifecycle()

    val current = user
    if (current == null) {
        LoginScreen(
            error = error,
            loading = loading,
            onLogin = authViewModel::login,
            onClearError = authViewModel::clearError
        )
    } else {
        when (current.role) {
            AppRole.MONITOR -> SyncPassNavHost(
                container = container,
                onLogout = authViewModel::logout
            )

            AppRole.COORDINATOR -> CoordinatorHomeScreen(
                container = container,
                onLogout = authViewModel::logout
            )

            AppRole.PARTICIPANT -> ParticipantHomeScreen(
                container = container,
                user = current,
                onLogout = authViewModel::logout
            )
        }
    }
}
