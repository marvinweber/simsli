package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbStoreCategory
import net.marvinweber.simsli.domain.model.StoreCategory

fun DbStoreCategory.toDomain(): StoreCategory = StoreCategory(
    storeId = storeId,
    categoryId = categoryId,
    sortOrder = sortOrder,
    createdAt = createdAt
)

fun StoreCategory.toDb(): DbStoreCategory = DbStoreCategory(
    storeId = storeId,
    categoryId = categoryId,
    sortOrder = sortOrder,
    createdAt = createdAt
)
