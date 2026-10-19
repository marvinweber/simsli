package net.marvinweber.simsli.data.repository

/**
 * Which kind of server the app talks to (AUTH-5): the hosted cloud with its
 * fixed build-time endpoint, or a user-provided self-hosted instance.
 * Both run the same backend — the choice only sets the endpoint.
 */
enum class HostingMode {
    CLOUD,
    SELF_HOSTED
}
