package net.marvinweber.simsli

import androidx.lifecycle.SavedStateHandle
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import net.marvinweber.simsli.R
import net.marvinweber.simsli.data.local.OnboardingStorage
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.CategoryRepository
import net.marvinweber.simsli.data.repository.EndpointHealth
import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.PostJoinSyncer
import net.marvinweber.simsli.data.repository.ServerRepository
import net.marvinweber.simsli.data.repository.SignOutCoordinator
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.domain.model.HouseholdMember
import net.marvinweber.simsli.domain.model.MonthlyActivity
import net.marvinweber.simsli.domain.model.StoreCategory
import net.marvinweber.simsli.ui.screens.onboarding.CATEGORY_PRESETS
import net.marvinweber.simsli.ui.screens.onboarding.CategoryPresets
import net.marvinweber.simsli.ui.screens.onboarding.OnboardingEvent
import net.marvinweber.simsli.ui.screens.onboarding.OnboardingMode
import net.marvinweber.simsli.ui.screens.onboarding.OnboardingStep
import net.marvinweber.simsli.ui.screens.onboarding.OnboardingViewModel
import net.marvinweber.simsli.ui.navigation.InviteDeepLinkManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Fakes ---

    private class FakeAuthRepository(initialState: AuthState = AuthState.SignedOut) : AuthRepository {
        val stateFlow = MutableStateFlow(initialState)
        override val authState: Flow<AuthState> = stateFlow
        override val currentAuthState: AuthState get() = stateFlow.value
        override suspend fun currentUserId(): String? = (stateFlow.value as? AuthState.SignedIn)?.userId
        override suspend fun currentUserEmail(): String? = (stateFlow.value as? AuthState.SignedIn)?.email
        override suspend fun sendMagicLink(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<SignOutResult> = Result.success(SignOutResult.Complete)
        override suspend fun handleDeepLink(intent: android.content.Intent?): Boolean = false
    }

    private class FakeServerRepository : ServerRepository {
        override val serverUrl: String = "http://10.0.2.2:8080"
        override val hostingMode: HostingMode = HostingMode.CLOUD
        override fun setHosting(mode: HostingMode, customUrl: String?) = Unit
        override suspend fun probe(customUrl: String?): EndpointHealth =
            error("probe not needed in these tests")
        override fun inviteDeepLink(code: String): String = "simsli://invite/$code"
    }

    private class FakeSignOutCoordinator : SignOutCoordinator {
        override suspend fun signOutAndWipe(): Result<SignOutResult> = Result.success(SignOutResult.Complete)
    }

    private class FakeOnboardingStorage : OnboardingStorage {
        var done = false
        override val isDone: Boolean get() = done
        override fun markDone() {
            done = true
        }
    }

    private class FakePostJoinSyncer : PostJoinSyncer {
        var syncCalls = 0
        override suspend fun syncNow() {
            syncCalls++
        }
    }

    private class FakeHouseholdRepository : HouseholdRepository {
        var createdHouseholds = 0
        var createdName: String? = null
        var existing: Household? = null
        var joinResult: Result<Unit> = Result.success(Unit)
        var joinedCode: String? = null

        override fun getHousehold(): Flow<Household?> = flowOf(existing)
        override suspend fun createHousehold(name: String): Result<Household> {
            createdHouseholds++
            createdName = name
            val household = Household(id = "hh-1", name = name, createdAt = Instant.now(), updatedAt = Instant.now())
            existing = household
            return Result.success(household)
        }

        override suspend fun joinHousehold(token: String): Result<Unit> {
            joinedCode = token
            return joinResult
        }

        override suspend fun updateHousehold(household: Household): Result<Household> = error("not used")
        override suspend fun deleteHousehold(householdId: String): Result<Unit> = error("not used")
        override suspend fun createInviteToken(): Result<String> = error("not used")
        override suspend fun getMembers(householdId: String): Result<List<HouseholdMember>> = error("not used")
        override suspend fun getMonthlyStats(householdId: String): Result<List<MonthlyActivity>> = error("not used")
        override suspend fun removeMember(householdId: String, userId: String): Result<Unit> = error("not used")
    }

    private class FakeCategoryRepository : CategoryRepository {
        val created = mutableListOf<Category>()
        var failNextCreate = false

        override fun getCategoriesByHousehold(householdId: String): Flow<List<Category>> =
            flowOf(created.toList())

        override suspend fun createCategory(category: Category): Result<Category> {
            if (failNextCreate) {
                failNextCreate = false
                return Result.failure(IllegalStateException("disk on fire"))
            }
            created.add(category)
            return Result.success(category)
        }

        override fun getStoreCategories(storeId: String): Flow<List<StoreCategory>> = error("not used")
        override fun observeStoreCategoriesByHousehold(householdId: String): Flow<List<StoreCategory>> = error("not used")
        override suspend fun saveStoreCategories(storeId: String, orderedCategoryIds: List<String>): Result<Unit> = error("not used")
        override suspend fun getCategoryById(id: String): Flow<Category?> = error("not used")
        override suspend fun getMaxCategorySortOrder(householdId: String): Float = error("not used")
        override suspend fun updateCategory(category: Category): Result<Category> = error("not used")
        override suspend fun deleteCategory(categoryId: String): Result<Unit> = error("not used")
        override suspend fun updateCategorySortOrder(categoryId: String, sortOrder: Float): Result<Unit> = error("not used")
        override suspend fun reorderCategories(updates: List<Pair<String, Float>>): Result<Unit> = error("not used")
    }

    // --- Helpers ---

    private fun TestScope.createViewModel(
        mode: String? = null,
        household: FakeHouseholdRepository = FakeHouseholdRepository(),
        category: FakeCategoryRepository = FakeCategoryRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(),
        inviteManager: InviteDeepLinkManager = InviteDeepLinkManager(
            FakeAuthRepository(), FakeServerRepository(), FakeSignOutCoordinator(),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        ),
        storage: FakeOnboardingStorage = FakeOnboardingStorage(),
        joinSyncer: FakePostJoinSyncer = FakePostJoinSyncer()
    ): OnboardingViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = if (mode == null) SavedStateHandle() else SavedStateHandle(mapOf("mode" to mode))
        return OnboardingViewModel(
            savedStateHandle = handle,
            householdRepository = household,
            categoryRepository = category,
            authRepository = auth,
            inviteDeepLinkManager = inviteManager,
            onboardingStorage = storage,
            categoryPresets = CategoryPresets { resId -> "name$resId" },
            postJoinSyncer = joinSyncer,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
    }

    // --- Tests ---

    @Test
    fun `create with presets on creates household and 11 categories with sort orders`() = runTest {
        val household = FakeHouseholdRepository()
        val category = FakeCategoryRepository()
        val storage = FakeOnboardingStorage()
        val vm = createViewModel(household = household, category = category, storage = storage)
        val events = mutableListOf<OnboardingEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events.add(it) } }

        vm.createHousehold("My household")

        assertEquals(1, household.createdHouseholds)
        assertEquals("My household", household.createdName)
        assertEquals(11, category.created.size)
        assertEquals(CATEGORY_PRESETS.map { it.emoji }, category.created.map { it.emoji })
        assertEquals(
            CATEGORY_PRESETS.indices.map { 1000f * (it + 1) },
            category.created.map { it.sortOrder }
        )
        assertTrue(category.created.all { it.householdId == "hh-1" })
        assertTrue(storage.done)
        assertEquals(listOf<OnboardingEvent>(OnboardingEvent.NavigateHome), events)
        job.cancelAndJoin()
    }

    @Test
    fun `create with presets off creates no categories`() = runTest {
        val category = FakeCategoryRepository()
        val vm = createViewModel(category = category)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { } }

        vm.onUsePresetsChange(false)
        vm.createHousehold("My household")

        assertEquals(0, category.created.size)
        job.cancelAndJoin()
    }

    @Test
    fun `create uses default name when input is blank`() = runTest {
        val household = FakeHouseholdRepository()
        val vm = createViewModel(household = household)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { } }

        vm.createHousehold("Mein Haushalt")

        assertEquals("Mein Haushalt", household.createdName)
        job.cancelAndJoin()
    }

    @Test
    fun `create failure surfaces error and does not mark done`() = runTest {
        val category = FakeCategoryRepository()
        val storage = FakeOnboardingStorage()
        val vm = createViewModel(category = category, storage = storage)
        category.failNextCreate = true

        vm.createHousehold("My household")

        assertFalse(storage.done)
        val message = vm.uiState.first { it.statusMessage != null }.statusMessage
        assertEquals(R.string.onboarding_error_setup_failed, message?.resId)
    }

    @Test
    fun `create retry reuses existing household instead of duplicating`() = runTest {
        val household = FakeHouseholdRepository()
        val category = FakeCategoryRepository()
        val vm = createViewModel(household = household, category = category)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { } }

        category.failNextCreate = true
        vm.createHousehold("My household")
        assertEquals(1, household.createdHouseholds)

        vm.createHousehold("My household")

        // The second attempt must not create a second household row.
        assertEquals(1, household.createdHouseholds)
        assertEquals(11, category.created.size)
        job.cancelAndJoin()
    }

    @Test
    fun `join success marks done emits NavigateHome and clears pending invite code`() = runTest {
        val storage = FakeOnboardingStorage()
        val joinSyncer = FakePostJoinSyncer()
        val household = FakeHouseholdRepository()
        val auth = FakeAuthRepository()
        val manager = InviteDeepLinkManager(
            auth, FakeServerRepository(), FakeSignOutCoordinator(),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )
        // Arrived via a signed-out invite deep link: the code is pending before
        // the onboarding join-code step (and its ViewModel) comes into existence.
        manager.handleInviteUri("simsli://invite/JOIN1234")
        val vm = createViewModel(household = household, auth = auth, inviteManager = manager, storage = storage, joinSyncer = joinSyncer)
        val events = mutableListOf<OnboardingEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events.add(it) } }

        assertEquals("JOIN1234", vm.uiState.first { it.inviteCodeInput == "JOIN1234" }.inviteCodeInput)

        auth.stateFlow.value = AuthState.SignedIn("user-1", "user@test.de")
        vm.joinHousehold()

        assertEquals("JOIN1234", household.joinedCode)
        assertTrue(storage.done)
        assertEquals(1, joinSyncer.syncCalls)
        assertNull(manager.pendingInviteCode.value)
        assertEquals(listOf<OnboardingEvent>(OnboardingEvent.NavigateHome), events)
        job.cancelAndJoin()
    }

    @Test
    fun `join failure surfaces join error and does not mark done`() = runTest {
        val household = FakeHouseholdRepository()
        household.joinResult = Result.failure(IllegalStateException("code expired"))
        val storage = FakeOnboardingStorage()
        val auth = FakeAuthRepository(AuthState.SignedIn("user-1", "user@test.de"))
        val vm = createViewModel(household = household, auth = auth, storage = storage)

        vm.onInviteCodeChange("JOIN1234")
        vm.joinHousehold()

        assertFalse(storage.done)
        val message = vm.uiState.first { it.statusMessage != null }.statusMessage
        assertEquals(R.string.hh_error_join_failed, message?.resId)
        assertEquals(listOf("code expired"), message?.args)
    }

    @Test
    fun `signing in while on create-or-join moves to join-code step`() = runTest {
        val auth = FakeAuthRepository()
        val vm = createViewModel(auth = auth)

        vm.next() // WELCOME → CONCEPTS
        vm.next() // CONCEPTS → CREATE_OR_JOIN

        auth.stateFlow.value = AuthState.SignedIn("user-1", "user@test.de")

        assertEquals(OnboardingStep.JOIN_CODE, vm.uiState.first { it.step == OnboardingStep.JOIN_CODE }.step)
    }

    @Test
    fun `replay mode closes instead of reaching create-or-join`() = runTest {
        val vm = createViewModel(mode = "replay")
        assertEquals(OnboardingMode.REPLAY, vm.uiState.first { it.mode == OnboardingMode.REPLAY }.mode)
        val events = mutableListOf<OnboardingEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events.add(it) } }

        vm.next() // WELCOME → CONCEPTS
        vm.next() // CONCEPTS: replay → Close, stays on concepts

        assertEquals(listOf<OnboardingEvent>(OnboardingEvent.Close), events)
        assertEquals(OnboardingStep.CONCEPTS, vm.uiState.value.step)
        job.cancelAndJoin()
    }
}
