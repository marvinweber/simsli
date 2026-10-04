package net.marvinweber.simsli.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.data.local.AuthTokenStorage
import net.marvinweber.simsli.data.remote.dto.AcceptInviteRequestDto
import net.marvinweber.simsli.data.remote.dto.AcceptInviteResponseDto
import net.marvinweber.simsli.data.remote.dto.AuthResponseDto
import net.marvinweber.simsli.data.remote.dto.CreateHouseholdRequestDto
import net.marvinweber.simsli.data.remote.dto.DeltaResponseDto
import net.marvinweber.simsli.data.remote.dto.FlushRequestDto
import net.marvinweber.simsli.data.remote.dto.HouseholdDto
import net.marvinweber.simsli.data.remote.dto.HouseholdMemberDto
import net.marvinweber.simsli.data.remote.dto.InviteTokenDto
import net.marvinweber.simsli.data.remote.dto.MagicLinkRequestDto
import net.marvinweber.simsli.data.remote.dto.MagicLinkResponseDto
import net.marvinweber.simsli.data.remote.dto.RefreshRequestDto
import net.marvinweber.simsli.data.remote.dto.ServerInfoDto
import net.marvinweber.simsli.data.remote.dto.TokenPairDto
import net.marvinweber.simsli.data.remote.dto.VerifyRequestDto
import net.marvinweber.simsli.di.IoDispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimsliRemoteDataSource @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val tokenStorage: AuthTokenStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val refreshMutex = Mutex()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // --- Server Info ---

    suspend fun getServerInfo(customUrl: String? = null): ServerInfoDto = withContext(ioDispatcher) {
        val baseUrl = (customUrl ?: tokenStorage.serverUrl).trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/api/v1/server-info")
            .get()
            .applyBaseHeaders()
            .build()

        executeAndDecode(request)
    }

    // --- Authentication ---

    suspend fun requestMagicLink(email: String): MagicLinkResponseDto = withContext(ioDispatcher) {
        val body = json.encodeToString(MagicLinkRequestDto(email)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/auth/magic-link")
            .post(body)
            .applyBaseHeaders()
            .build()

        executeAndDecode(request)
    }

    suspend fun verifyMagicLink(token: String): AuthResponseDto = withContext(ioDispatcher) {
        val body = json.encodeToString(VerifyRequestDto(token)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/auth/verify")
            .post(body)
            .applyBaseHeaders()
            .build()

        val response: AuthResponseDto = executeAndDecode(request)
        tokenStorage.saveSession(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            userId = response.user.id,
            email = response.user.email
        )
        response
    }

    suspend fun logout(): Unit = withContext(ioDispatcher) {
        val rt = tokenStorage.refreshToken
        if (!rt.isNullOrBlank()) {
            val body = json.encodeToString(RefreshRequestDto(rt)).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${tokenStorage.serverUrl}/api/v1/auth/logout")
                .post(body)
                .applyBaseHeaders()
                .build()
            runCatching { httpClient.newCall(request).execute().close() }
        }
        tokenStorage.clearSession()
    }

    // --- Households & Members ---

    suspend fun getMyMemberships(userId: String = ""): List<HouseholdMemberDto> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households/mine")
            .get()
            .buildAuthenticated()

        executeAndDecode(request)
    }

    suspend fun createHousehold(id: String, name: String): HouseholdDto = withContext(ioDispatcher) {
        val body = json.encodeToString(CreateHouseholdRequestDto(id, name)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households")
            .post(body)
            .buildAuthenticated()

        executeAndDecode(request)
    }

    suspend fun createHouseholdWithOwner(householdId: String, name: String) {
        createHousehold(householdId, name)
    }

    suspend fun updateHousehold(id: String, name: String): Unit = withContext(ioDispatcher) {
        val body = json.encodeToString(CreateHouseholdRequestDto(id, name)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households/$id")
            .put(body)
            .buildAuthenticated()

        executeNoContent(request)
    }

    suspend fun getHousehold(householdId: String): HouseholdDto = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households/$householdId")
            .get()
            .buildAuthenticated()

        executeAndDecode(request)
    }

    suspend fun getHouseholdMembers(householdId: String): List<HouseholdMemberDto> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households/$householdId/members")
            .get()
            .buildAuthenticated()

        executeAndDecode(request)
    }

    suspend fun removeHouseholdMember(householdId: String, userId: String): Unit = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households/$householdId/members/$userId")
            .delete()
            .buildAuthenticated()

        executeNoContent(request)
    }

    suspend fun createInvite(householdId: String): InviteTokenDto = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households/$householdId/invites")
            .post("{}".toRequestBody(jsonMediaType))
            .buildAuthenticated()

        executeAndDecode(request)
    }

    suspend fun acceptInvite(token: String): AcceptInviteResponseDto = withContext(ioDispatcher) {
        val body = json.encodeToString(AcceptInviteRequestDto(token)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/households/invites/accept")
            .post(body)
            .buildAuthenticated()

        executeAndDecode(request)
    }

    // --- Sync & Deltas ---

    suspend fun getDeltas(householdId: String, since: Instant): DeltaResponseDto = withContext(ioDispatcher) {
        val sinceIso = since.toString()
        val url = "${tokenStorage.serverUrl}/api/v1/sync/deltas?household_id=$householdId&since=$sinceIso"
        val request = Request.Builder()
            .url(url)
            .get()
            .buildAuthenticated()

        executeAndDecode(request)
    }

    suspend fun flush(req: FlushRequestDto): Unit = withContext(ioDispatcher) {
        val body = json.encodeToString(req).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${tokenStorage.serverUrl}/api/v1/sync/flush")
            .post(body)
            .buildAuthenticated()

        executeNoContent(request)
    }

    // --- Internal Helpers & Token Refresh ---

    private fun Request.Builder.applyBaseHeaders(): Request.Builder {
        header("Accept", "application/json")
        header("X-Simsli-App-Version", BuildConfig.VERSION_NAME)
        header("X-Simsli-Platform", "Android")
        return this
    }

    private fun Request.Builder.buildAuthenticated(): Request {
        applyBaseHeaders()
        val token = tokenStorage.accessToken
        if (!token.isNullOrBlank()) {
            header("Authorization", "Bearer $token")
        }
        return build()
    }

    private suspend inline fun <reified T> executeAndDecode(request: Request): T {
        val response = executeWithAuthRetry(request)
        val bodyString = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw SimsliApiException(response.code, bodyString)
        }
        return json.decodeFromString(bodyString)
    }

    private suspend fun executeNoContent(request: Request) {
        val response = executeWithAuthRetry(request)
        val bodyString = response.body?.string()
        if (!response.isSuccessful) {
            throw SimsliApiException(response.code, bodyString)
        }
    }

    private suspend fun executeWithAuthRetry(request: Request): Response {
        val response = httpClient.newCall(request).execute()
        if (response.code != 401 || tokenStorage.refreshToken.isNullOrBlank()) {
            return response
        }

        // Token expired; attempt refresh under mutex lock
        response.close()
        val refreshed = refreshTokensSafely()
        if (!refreshed) {
            return httpClient.newCall(request).execute()
        }

        // Retry original request with new access token
        val newRequest = request.newBuilder()
            .header("Authorization", "Bearer ${tokenStorage.accessToken}")
            .build()
        return httpClient.newCall(newRequest).execute()
    }

    private suspend fun refreshTokensSafely(): Boolean = refreshMutex.withLock {
        val rt = tokenStorage.refreshToken ?: return false
        return try {
            val body = json.encodeToString(RefreshRequestDto(rt)).toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("${tokenStorage.serverUrl}/api/v1/auth/refresh")
                .post(body)
                .applyBaseHeaders()
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                tokenStorage.clearSession()
                resp.close()
                return false
            }

            val tokens: TokenPairDto = json.decodeFromString(resp.body?.string() ?: "")
            tokenStorage.updateTokens(tokens.accessToken, tokens.refreshToken)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "SimsliRemote"
    }
}
