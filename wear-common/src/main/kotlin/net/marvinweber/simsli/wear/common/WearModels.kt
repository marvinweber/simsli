package net.marvinweber.simsli.wear.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val WEAR_DATA_PATH = "/simsli/shopping_list"
const val WEAR_DATA_KEY = "payload"
const val WEAR_CHECK_ENTRY_PATH = "/simsli/check_entry"

@Serializable
data class WearStoreSummary(
    val id: String?,
    val name: String,
    val activeCount: Int
)

@Serializable
data class WearShoppingItem(
    val entryId: String,
    val itemId: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val comment: String? = null,
    val storeIds: List<String> = emptyList(),
    val isDone: Boolean = false
)

@Serializable
data class WearDataPayload(
    val timestamp: Long,
    val stores: List<WearStoreSummary>,
    val items: List<WearShoppingItem>
) {
    fun toJson(): String = jsonFormat.encodeToString(this)

    companion object {
        private val jsonFormat = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(json: String): WearDataPayload = jsonFormat.decodeFromString(json)
    }
}
