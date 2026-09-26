package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemStoreDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("created_at") val createdAt: String
)
