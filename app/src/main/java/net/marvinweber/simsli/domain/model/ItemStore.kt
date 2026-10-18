package net.marvinweber.simsli.domain.model

import java.time.Instant

/**
 * How an item_stores row relates to the item's store selection (STORE-7):
 * MEMBER = explicitly assigned; EXCLUDED = excluded from a group selection;
 * METADATA = carries favorite/price info without contributing membership.
 */
enum class ItemStoreMembership { MEMBER, EXCLUDED, METADATA }

data class ItemStore(
    val itemId: String,
    val storeId: String,
    val membership: ItemStoreMembership = ItemStoreMembership.MEMBER,
    val favorite: Boolean = false,
    /** Minor units (cents); null = unpriced (STORE-6). */
    val priceMinor: Long? = null,
    val createdAt: Instant
)

/**
 * An item's store selection as edited in the item detail dialog (STORE-5, STORE-7):
 * explicit stores and groups mix freely; exclusions only ever apply to
 * group-covered stores; favorite stores mark preferred places to buy this item.
 */
data class StoreSelection(
    val memberStoreIds: Set<String> = emptySet(),
    val excludedStoreIds: Set<String> = emptySet(),
    val groupIds: Set<String> = emptySet(),
    val favoriteStoreIds: Set<String> = emptySet()
)
