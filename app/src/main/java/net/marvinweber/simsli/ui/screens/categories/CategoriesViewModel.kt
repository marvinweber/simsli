package net.marvinweber.simsli.ui.screens.categories

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
import net.marvinweber.simsli.data.repository.CategoryRepository
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Household
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class CategoriesUiState(
    val household: Household? = null,
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class CategoriesUiEvent {
    data class ShowError(val message: String) : CategoriesUiEvent()
    data class ConfirmDeleteCategory(val categoryId: String, val categoryName: String, val categoryEmoji: String?) : CategoriesUiEvent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = householdRepository.getHousehold()
        .flatMapLatest { household ->
            if (household == null) {
                flowOf(CategoriesUiState(isLoading = false))
            } else {
                categoryRepository.getCategoriesByHousehold(household.id).map { categories ->
                    CategoriesUiState(
                        household = household,
                        categories = categories,
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState(isLoading = true)
        )

    private val _events = MutableStateFlow<CategoriesUiEvent?>(null)
    val events: StateFlow<CategoriesUiEvent?> = _events.asStateFlow()

    fun createCategory(name: String, emoji: String?) {
        viewModelScope.launch {
            val household = uiState.value.household
            if (household == null) {
                _events.value = CategoriesUiEvent.ShowError("No household found")
                return@launch
            }

            if (name.isBlank()) {
                _events.value = CategoriesUiEvent.ShowError("Category name cannot be empty")
                return@launch
            }

            val now = Instant.now()
            val category = Category(
                id = UUID.randomUUID().toString(),
                householdId = household.id,
                name = name.trim(),
                emoji = emoji?.trim()?.ifBlank { null },
                sortOrder = categoryRepository.getMaxCategorySortOrder(household.id) + 1000f,
                createdAt = now,
                updatedAt = now
            )

            categoryRepository.createCategory(category)
                .onFailure { error ->
                    _events.value = CategoriesUiEvent.ShowError("Failed to create category: ${error.message}")
                }
        }
    }

    fun updateCategory(categoryId: String, name: String, emoji: String?) {
        viewModelScope.launch {
            val existing = uiState.value.categories.firstOrNull { it.id == categoryId }
            if (existing == null) {
                _events.value = CategoriesUiEvent.ShowError("Category not found")
                return@launch
            }

            if (name.isBlank()) {
                _events.value = CategoriesUiEvent.ShowError("Category name cannot be empty")
                return@launch
            }

            val updated = existing.copy(
                name = name.trim(),
                emoji = emoji?.trim()?.ifBlank { null },
                updatedAt = Instant.now()
            )

            categoryRepository.updateCategory(updated)
                .onFailure { error ->
                    _events.value = CategoriesUiEvent.ShowError("Failed to update category: ${error.message}")
                }
        }
    }

    fun onDeleteCategory(categoryId: String) {
        viewModelScope.launch {
            val category = uiState.value.categories.firstOrNull { it.id == categoryId }
            if (category == null) {
                _events.value = CategoriesUiEvent.ShowError("Category not found")
                return@launch
            }

            _events.value = CategoriesUiEvent.ConfirmDeleteCategory(categoryId, category.name, category.emoji)
        }
    }

    fun confirmDeleteCategory(categoryId: String) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(categoryId)
                .onFailure { error ->
                    _events.value = CategoriesUiEvent.ShowError("Failed to delete category: ${error.message}")
                }
        }
    }

    fun onReorder(reorderedCategories: List<Category>) {
        viewModelScope.launch {
            val original = uiState.value.categories
            if (original.map { it.id } == reorderedCategories.map { it.id }) return@launch

            // Check if exactly one item was moved (standard drag-and-drop scenario)
            val movedItem = findSingleMovedItem(original, reorderedCategories)
            if (movedItem != null) {
                val targetIndex = reorderedCategories.indexOfFirst { it.id == movedItem.id }
                val newSortOrder = computeGapSortOrder(reorderedCategories, targetIndex)
                if (newSortOrder != null) {
                    categoryRepository.updateCategorySortOrder(movedItem.id, newSortOrder)
                        .onFailure { error ->
                            _events.value = CategoriesUiEvent.ShowError("Failed to update order: ${error.message}")
                        }
                    return@launch
                }
            }

            // Fallback for collapsed gaps or multi-item shifts: re-space with 1000f increments
            val updates = reorderedCategories.mapIndexed { index, cat ->
                cat.id to ((index + 1) * 1000f)
            }
            categoryRepository.reorderCategories(updates)
                .onFailure { error ->
                    _events.value = CategoriesUiEvent.ShowError("Failed to update order: ${error.message}")
                }
        }
    }

    private fun findSingleMovedItem(original: List<Category>, reordered: List<Category>): Category? {
        if (original.size != reordered.size) return null
        return reordered.firstOrNull { item ->
            val origWithoutItem = original.filter { it.id != item.id }.map { it.id }
            val reorderedWithoutItem = reordered.filter { it.id != item.id }.map { it.id }
            origWithoutItem == reorderedWithoutItem
        }
    }

    private fun computeGapSortOrder(list: List<Category>, targetIndex: Int): Float? {
        if (list.size <= 1) return null
        return when (targetIndex) {
            0 -> list[1].sortOrder - 1000f
            list.lastIndex -> list[list.lastIndex - 1].sortOrder + 1000f
            else -> {
                val prev = list[targetIndex - 1].sortOrder
                val next = list[targetIndex + 1].sortOrder
                val diff = next - prev
                if (diff > 0.001f) prev + diff / 2f else null
            }
        }
    }

    fun onEventConsumed() {
        _events.value = null
    }
}
