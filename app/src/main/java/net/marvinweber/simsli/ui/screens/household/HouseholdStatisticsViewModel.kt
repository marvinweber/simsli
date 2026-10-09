package net.marvinweber.simsli.ui.screens.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.domain.model.MonthlyActivity

/** How many months the timeline renders initially / extends per scroll-back step. */
private const val TIMELINE_WINDOW_MONTHS = 12

@HiltViewModel
class HouseholdStatisticsViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    /**
     * One rendered timeline row. Months without server-recorded activity are
     * zero-filled so the timeline stays continuous into the past.
     */
    data class TimelineMonth(
        val year: Int,
        val month: Int,
        val itemsAdded: Int,
        val itemsChecked: Int
    )

    data class HouseholdStatisticsUiState(
        val isLoading: Boolean = true,
        val householdName: String? = null,
        val months: List<TimelineMonth> = emptyList(),
        /** True when any month in [months] has recorded activity. */
        val hasAnyActivity: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(HouseholdStatisticsUiState())
    val uiState = _uiState.asStateFlow()

    /** Months with server-recorded activity, keyed as year*12+month-1. */
    private var activityByMonth: Map<Long, MonthlyActivity> = emptyMap()
    private var renderedMonthCount = 0
    private var householdId: String? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val household = householdRepository.getHousehold().first()
            if (household == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No household found."
                )
                return@launch
            }
            householdId = household.id

            householdRepository.getMonthlyStats(household.id).fold(
                onSuccess = { months ->
                    activityByMonth = months.associateBy { monthKey(it.year, it.month) }
                    renderedMonthCount = TIMELINE_WINDOW_MONTHS
                    _uiState.value = HouseholdStatisticsUiState(
                        isLoading = false,
                        householdName = household.name,
                        months = buildTimeline(),
                        hasAnyActivity = months.isNotEmpty()
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Could not load statistics."
                    )
                }
            )
        }
    }

    /** Extends the rendered timeline further into the past (render window only — all data is already in memory). */
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.errorMessage != null) return
        if (_uiState.value.months.size < renderedMonthCount) return // already extended

        renderedMonthCount += TIMELINE_WINDOW_MONTHS
        _uiState.value = current.copy(months = buildTimeline())
    }

    private fun buildTimeline(): List<TimelineMonth> {
        val current = YearMonth.now()
        return (0 until renderedMonthCount).map { offset ->
            val ym = current.minusMonths(offset.toLong())
            val activity = activityByMonth[monthKey(ym.year, ym.monthValue)]
            TimelineMonth(
                year = ym.year,
                month = ym.monthValue,
                itemsAdded = activity?.itemsAdded ?: 0,
                itemsChecked = activity?.itemsChecked ?: 0
            )
        }
    }

    private fun monthKey(year: Int, month: Int): Long = year * 12L + (month - 1)
}
