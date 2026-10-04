package net.marvinweber.simsli.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ItemLink(
    val url: String,
    val title: String? = null
)
