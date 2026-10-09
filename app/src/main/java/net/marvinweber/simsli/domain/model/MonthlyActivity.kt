package net.marvinweber.simsli.domain.model

/** One month's household shopping activity, as recorded by the server. */
data class MonthlyActivity(
    val year: Int,
    val month: Int,
    val itemsAdded: Int,
    val itemsChecked: Int
)
