package net.marvinweber.simsli.data.remote

import java.io.IOException

class SimsliApiException(
    val statusCode: Int,
    val errorResponse: String?,
    override val message: String = "Simsli API error ($statusCode): $errorResponse"
) : IOException(message)
