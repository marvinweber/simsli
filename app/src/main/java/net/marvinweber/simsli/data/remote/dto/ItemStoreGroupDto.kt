package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemStoreGroupDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("created_at") val createdAt: String
)
