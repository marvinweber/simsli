package net.marvinweber.simsli.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.marvinweber.simsli.data.remote.dto.HouseholdDto
import net.marvinweber.simsli.data.remote.dto.HouseholdMemberDto
import net.marvinweber.simsli.data.remote.dto.CategoryDto
import net.marvinweber.simsli.data.remote.dto.ItemDto
import net.marvinweber.simsli.data.remote.dto.ItemStoreDto
import net.marvinweber.simsli.data.remote.dto.ListEntryDto
import net.marvinweber.simsli.data.remote.dto.StoreCategoryDto
import net.marvinweber.simsli.data.remote.dto.StoreDto
import net.marvinweber.simsli.di.IoDispatcher
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All PostgREST access. Authenticated requests automatically carry the
 * session token (installed Auth plugin), so RLS applies to every call.
 */
@Singleton
class SupabaseRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    // --- Household / membership -------------------------------------------------

    suspend fun getMyMemberships(userId: String): List<HouseholdMemberDto> = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("household_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList()
    }

    suspend fun getHousehold(householdId: String): HouseholdDto = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("households")
            .select { filter { eq("id", householdId) } }
            .decodeSingle()
    }

    suspend fun fetchHouseholdsSince(householdId: String, since: Instant): List<HouseholdDto> =
        withContext(ioDispatcher) {
            supabaseClient.postgrest.from("households").select {
                filter {
                    eq("id", householdId)
                    gt("updated_at", since.toString())
                }
            }.decodeList()
        }

    /**
     * Adopts a client-generated household id on the server and adds the calling
     * user as owner — designed for the first sync of an offline household.
     */
    suspend fun createHouseholdWithOwner(householdId: String, name: String): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.rpc(
            function = "create_household_with_owner",
            parameters = buildJsonObject {
                put("p_id", householdId)
                put("p_name", name)
            }
        )
    }

    /**
     * Registers a client-generated single-use invite code. Timestamps are left to
     * the server defaults (created_at = now, expires_at = now + 24h).
     */
    suspend fun insertInviteToken(token: String, householdId: String, createdBy: String): Unit =
        withContext(ioDispatcher) {
            supabaseClient.postgrest.from("invite_tokens").insert(
                buildJsonArray {
                    add(buildJsonObject {
                        put("token", token)
                        put("household_id", householdId)
                        put("created_by", createdBy)
                    })
                }
            )
        }

    /** Redeems an invite code: marks it used and adds the caller to that household. */
    suspend fun acceptInvite(token: String): String = withContext(ioDispatcher) {
        supabaseClient.postgrest.rpc(
            function = "accept_invite",
            parameters = buildJsonObject { put("p_token", token) }
        ).decodeAs()
    }

    // --- Delta pulls ------------------------------------------------------------

    suspend fun fetchStoresSince(householdId: String, since: Instant): List<StoreDto> = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("stores").select {
            filter {
                eq("household_id", householdId)
                gt("updated_at", since.toString())
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun fetchItemsSince(householdId: String, since: Instant): List<ItemDto> = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("items").select {
            filter {
                eq("household_id", householdId)
                gt("updated_at", since.toString())
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun fetchListEntriesSince(householdId: String, since: Instant): List<ListEntryDto> = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("list_entries").select {
            filter {
                eq("household_id", householdId)
                gt("updated_at", since.toString())
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    /** item_stores has no updated_at, so sync reconciles the full assignment set per sync. */
    suspend fun fetchItemStores(itemIds: List<String>): List<ItemStoreDto> = withContext(ioDispatcher) {
        if (itemIds.isEmpty()) {
            emptyList()
        } else {
            supabaseClient.postgrest.from("item_stores")
                .select { filter { isIn("item_id", itemIds) } }
                .decodeList()
        }
    }

    suspend fun fetchCategoriesSince(householdId: String, since: Instant): List<CategoryDto> = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("categories").select {
            filter {
                eq("household_id", householdId)
                gt("updated_at", since.toString())
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()
    }

    /** store_categories has no updated_at, so sync reconciles the full ordering set per sync. */
    suspend fun fetchStoreCategories(categoryIds: List<String>): List<StoreCategoryDto> = withContext(ioDispatcher) {
        if (categoryIds.isEmpty()) {
            emptyList()
        } else {
            supabaseClient.postgrest.from("store_categories")
                .select { filter { isIn("category_id", categoryIds) } }
                .decodeList()
        }
    }

    // --- Pushes -----------------------------------------------------------------

    suspend fun upsertHousehold(dto: HouseholdDto): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("households").upsert(dto)
    }

    suspend fun upsertStore(dto: StoreDto): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("stores").upsert(dto)
    }

    suspend fun upsertItem(dto: ItemDto): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("items").upsert(dto)
    }

    suspend fun upsertListEntry(dto: ListEntryDto): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("list_entries").upsert(dto)
    }

    suspend fun upsertItemStores(dtos: List<ItemStoreDto>): Unit = withContext(ioDispatcher) {
        if (dtos.isNotEmpty()) {
            supabaseClient.postgrest.from("item_stores").upsert(dtos)
        }
    }

    suspend fun upsertCategory(dto: CategoryDto): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("categories").upsert(dto)
    }

    suspend fun upsertStoreCategories(dtos: List<StoreCategoryDto>): Unit = withContext(ioDispatcher) {
        if (dtos.isNotEmpty()) {
            supabaseClient.postgrest.from("store_categories").upsert(dtos)
        }
    }

    suspend fun deleteListEntry(id: String): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("list_entries").delete {
            filter { eq("id", id) }
        }
    }

    suspend fun deleteItemStore(itemId: String, storeId: String): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("item_stores").delete {
            filter {
                eq("item_id", itemId)
                eq("store_id", storeId)
            }
        }
    }

    suspend fun deleteStoreCategory(storeId: String, categoryId: String): Unit = withContext(ioDispatcher) {
        supabaseClient.postgrest.from("store_categories").delete {
            filter {
                eq("store_id", storeId)
                eq("category_id", categoryId)
            }
        }
    }
}
