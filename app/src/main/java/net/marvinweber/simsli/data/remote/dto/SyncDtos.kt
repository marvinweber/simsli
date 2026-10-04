package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeltaResponseDto(
    val household: HouseholdDto? = null,
    val stores: List<StoreDto>? = null,
    val categories: List<CategoryDto>? = null,
    @SerialName("store_categories") val storeCategories: List<StoreCategoryDto>? = null,
    val items: List<ItemDto>? = null,
    @SerialName("item_stores") val itemStores: List<ItemStoreDto>? = null,
    @SerialName("list_entries") val listEntries: List<ListEntryDto>? = null,
    @SerialName("server_time") val serverTime: String
)

@Serializable
data class FlushRequestDto(
    @SerialName("household_id") val householdId: String,
    val household: HouseholdDto? = null,
    val stores: List<StoreDto>? = null,
    val categories: List<CategoryDto>? = null,
    @SerialName("store_categories") val storeCategories: List<StoreCategoryDto>? = null,
    val items: List<ItemDto>? = null,
    @SerialName("item_stores") val itemStores: List<ItemStoreDto>? = null,
    @SerialName("list_entries") val listEntries: List<ListEntryDto>? = null,
    @SerialName("deleted_stores") val deletedStores: List<String>? = null,
    @SerialName("deleted_categories") val deletedCategories: List<String>? = null,
    @SerialName("deleted_items") val deletedItems: List<String>? = null,
    @SerialName("deleted_entries") val deletedEntries: List<String>? = null
)

@Serializable
data class CreateHouseholdRequestDto(
    val id: String,
    val name: String
)

@Serializable
data class InviteTokenDto(
    val token: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("used_by") val usedBy: String? = null
)

@Serializable
data class AcceptInviteRequestDto(
    val token: String
)

@Serializable
data class AcceptInviteResponseDto(
    @SerialName("household_id") val householdId: String,
    val message: String
)
