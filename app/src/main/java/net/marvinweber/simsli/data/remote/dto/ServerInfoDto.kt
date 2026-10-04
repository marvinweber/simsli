package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoDto(
    @SerialName("server_mode") val serverMode: String,
    @SerialName("database_driver") val databaseDriver: String,
    @SerialName("registration_open") val registrationOpen: Boolean,
    @SerialName("billing_enabled") val billingEnabled: Boolean,
    val version: String,
    @SerialName("api_version") val apiVersion: Int = 1,
    @SerialName("min_app_version") val minAppVersion: String = "0.1.0",
    @SerialName("debug_mode") val debugMode: Boolean = false
)
