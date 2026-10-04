package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MagicLinkRequestDto(
    val email: String
)

@Serializable
data class MagicLinkResponseDto(
    val message: String,
    val token: String? = null
)

@Serializable
data class VerifyRequestDto(
    val token: String
)

@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: String,
    val user: UserDto
)

@Serializable
data class TokenPairDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: String
)
