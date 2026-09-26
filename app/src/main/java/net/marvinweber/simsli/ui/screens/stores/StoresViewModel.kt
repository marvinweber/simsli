package net.marvinweber.simsli.ui.screens.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.domain.model.Store
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class StoresUiState(
    val household: Household? = null,
    val stores: List<Store> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class StoresUiEvent {
    data object NavigateBack : StoresUiEvent()
    data class ShowError(val message: String) : StoresUiEvent()
    data class ConfirmDeleteStore(val storeId: String, val storeName: String) : StoresUiEvent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoresViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val storeRepository: StoreRepository
) : ViewModel() {

    val uiState: StateFlow<StoresUiState> = householdRepository.getHousehold()
        .flatMapLatest { household ->
            if (household == null) {
                flowOf(StoresUiState(isLoading = false))
            } else {
                storeRepository.getStoresByHousehold(household.id).map { stores ->
                    StoresUiState(
                        household = household,
                        stores = stores,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StoresUiState(isLoading = true)
        )

    private val _events = MutableStateFlow<StoresUiEvent?>(null)
    val events: StateFlow<StoresUiEvent?> = _events.asStateFlow()

    fun createStore(name: String) {
        viewModelScope.launch {
            val household = uiState.value.household
            if (household == null) {
                _events.value = StoresUiEvent.ShowError("No household found")
                return@launch
            }

            if (name.isBlank()) {
                _events.value = StoresUiEvent.ShowError("Store name cannot be empty")
                return@launch
            }

            val now = Instant.now()
            val store = Store(
                id = UUID.randomUUID().toString(),
                householdId = household.id,
                name = name.trim(),
                sortOrder = storeRepository.getMaxStoreSortOrder(household.id) + 1f,
                createdAt = now,
                updatedAt = now
            )

            storeRepository.createStore(store)
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to create store: ${error.message}")
                }
        }
    }

    fun onDeleteStore(storeId: String) {
        viewModelScope.launch {
            val store = uiState.value.stores.find { it.id == storeId }
            if (store == null) {
                _events.value = StoresUiEvent.ShowError("Store not found")
                return@launch
            }

            _events.value = StoresUiEvent.ConfirmDeleteStore(storeId, store.name)
        }
    }

    fun confirmDeleteStore(storeId: String) {
        viewModelScope.launch {
            storeRepository.deleteStore(storeId)
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to delete store: ${error.message}")
                }
        }
    }

    fun onBack() {
        _events.value = StoresUiEvent.NavigateBack
    }

    fun onEventConsumed() {
        _events.value = null
    }
}
