package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbItemStoreGroup
import net.marvinweber.simsli.domain.model.ItemStoreGroup

fun DbItemStoreGroup.toDomain(): ItemStoreGroup = ItemStoreGroup(
    itemId = itemId,
    groupId = groupId,
    createdAt = createdAt
)

fun ItemStoreGroup.toDb(): DbItemStoreGroup = DbItemStoreGroup(
    itemId = itemId,
    groupId = groupId,
    createdAt = createdAt
)
