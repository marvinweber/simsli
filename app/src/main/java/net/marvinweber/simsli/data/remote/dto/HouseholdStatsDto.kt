package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HouseholdStatsDto(
    val months: List<MonthlyStatsDto> = emptyList()
)

@Serializable
data class MonthlyStatsDto(
    val year: Int,
    val month: Int,
    @SerialName("items_checked") val itemsChecked: Int = 0,
    @SerialName("items_added") val itemsAdded: Int = 0
)
