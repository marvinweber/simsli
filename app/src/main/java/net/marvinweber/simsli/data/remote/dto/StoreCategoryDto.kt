package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StoreCategoryDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("sort_order") val sortOrder: Double,
    @SerialName("created_at") val createdAt: String? = null
)
