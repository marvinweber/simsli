package net.marvinweber.simsli.data.local.mapper

import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.domain.model.Household

fun DbHousehold.toDomain(): Household = Household(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Household.toDb(): DbHousehold = DbHousehold(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null
)
