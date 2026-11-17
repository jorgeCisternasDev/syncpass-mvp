package cl.syncpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.syncpass.app.auth.AuthUser
import cl.syncpass.app.data.repository.AuthRepository
import cl.syncpass.app.data.repository.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<AuthUser?>(authRepository.restoreSession())
    val user: StateFlow<AuthUser?> = _user

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun login(identifier: String, password: String) {
        if (_loading.value) return
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            when (val result = authRepository.login(identifier, password)) {
                is LoginResult.Success -> {
                    _user.value = result.user
                    _error.value = null
                }

                LoginResult.InvalidCredentials -> {
                    _error.value = "Usuario o clave incorrectos."
                }

                is LoginResult.Failure -> {
                    _error.value = result.message
                }
            }
            _loading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun logout() {
        authRepository.logout()
        _user.value = null
        _error.value = null
    }
}
