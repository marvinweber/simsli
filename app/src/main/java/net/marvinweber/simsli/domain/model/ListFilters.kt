package net.marvinweber.simsli.domain.model

/** What the shopping list is filtered to by store (LIST-5). */
sealed interface StoreFilter {
    /** Everything. */
    data object All : StoreFilter

    /** Only items assigned to this store. */
    data class ByStore(val storeId: String) : StoreFilter

    /** Only items without any store assignment. */
    data object NoStore : StoreFilter
}

/** What the shopping list is filtered to by category (LIST-5). */
sealed interface CategoryFilter {
    /** Everything. */
    data object All : CategoryFilter

    /** Only items assigned to this category. */
    data class ByCategory(val categoryId: String) : CategoryFilter

    /** Only items without any category assignment ("Uncategorized"). */
    data object NoCategory : CategoryFilter
}
