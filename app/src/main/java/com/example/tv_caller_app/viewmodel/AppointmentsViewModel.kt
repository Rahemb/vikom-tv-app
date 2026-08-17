package com.example.tv_caller_app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.AppointmentDataSource
import com.example.tv_caller_app.datasource.BackendNotConfiguredException
import com.example.tv_caller_app.datasource.ProfileNotLinkedException
import com.example.tv_caller_app.datasource.SessionExpiredException
import com.example.tv_caller_app.repository.AppointmentRepository
import com.example.tv_caller_app.ui.adapters.AppointmentListItem
import com.example.tv_caller_app.ui.adapters.AppointmentListAdapter
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * State of the appointments screen. Distinct failure states rather than one
 * generic error, because the fixes differ: NotLinked needs staff to link the
 * account, SessionExpired needs the patient to sign in again, and Error is
 * usually just the network.
 */
sealed class AppointmentsUiState {
    data object Loading : AppointmentsUiState()
    data class Content(val items: List<AppointmentListItem>) : AppointmentsUiState()
    data object Empty : AppointmentsUiState()
    data object NotLinked : AppointmentsUiState()
    data object SessionExpired : AppointmentsUiState()
    data object NotConfigured : AppointmentsUiState()
    data object Error : AppointmentsUiState()
}

class AppointmentsViewModel(
    private val appointmentDataSource: AppointmentDataSource
) : ViewModel() {

    companion object {
        private const val TAG = "AppointmentsViewModel"
    }

    private val _uiState = MutableLiveData<AppointmentsUiState>(AppointmentsUiState.Loading)
    val uiState: LiveData<AppointmentsUiState> = _uiState

    fun load(forceRefresh: Boolean = true) {
        _uiState.value = AppointmentsUiState.Loading

        viewModelScope.launch {
            try {
                val appointments = appointmentDataSource.getMyAppointments(forceRefresh)

                _uiState.value = if (appointments.isEmpty()) {
                    AppointmentsUiState.Empty
                } else {
                    AppointmentsUiState.Content(
                        AppointmentListAdapter.buildGroupedList(appointments, LocalDateTime.now())
                    )
                }
            } catch (e: ProfileNotLinkedException) {
                Log.w(TAG, "No patient linked to this Supabase profile")
                _uiState.value = AppointmentsUiState.NotLinked
            } catch (e: SessionExpiredException) {
                Log.w(TAG, "Supabase session expired")
                _uiState.value = AppointmentsUiState.SessionExpired
            } catch (e: BackendNotConfiguredException) {
                Log.e(TAG, "backend.base.url is not set in local.properties")
                _uiState.value = AppointmentsUiState.NotConfigured
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load appointments: ${e.message}", e)
                _uiState.value = AppointmentsUiState.Error
            }
        }
    }
}

class AppointmentsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val sessionManager = SessionManager.getInstance(application)
        return AppointmentsViewModel(
            AppointmentRepository.getInstance(sessionManager)
        ) as T
    }
}
