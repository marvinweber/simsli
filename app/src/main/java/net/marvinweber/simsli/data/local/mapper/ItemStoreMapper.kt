package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ItemStoreMembership

fun DbItemStore.toDomain(): ItemStore = ItemStore(
    itemId = itemId,
    storeId = storeId,
    membership = membership.toMembershipOrDefault(),
    createdAt = createdAt
)

fun ItemStore.toDb(): DbItemStore = DbItemStore(
    itemId = itemId,
    storeId = storeId,
    membership = membership.name,
    createdAt = createdAt
)

fun String.toMembershipOrDefault(): ItemStoreMembership =
    ItemStoreMembership.entries.firstOrNull { it.name == this } ?: ItemStoreMembership.MEMBER
