package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbCategory
import net.marvinweber.simsli.domain.model.Category

fun DbCategory.toDomain(): Category = Category(
    id = id,
    householdId = householdId,
    name = name,
    emoji = emoji,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Category.toDb(): DbCategory = DbCategory(
    id = id,
    householdId = householdId,
    name = name,
    emoji = emoji,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null
)
