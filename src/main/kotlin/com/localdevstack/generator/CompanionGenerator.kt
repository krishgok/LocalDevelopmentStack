package com.localdevstack.generator

/**
 * Generates an opt-in companion service that runs alongside the user's primary
 * service and database (e.g. MailHog for SMTP testing, MinIO for S3-compatible
 * object storage).
 *
 * Companions are activated via the `--with` CLI flag and post-processed into
 * the compose file by [appendCompanionBlocksToCompose] after the
 * [DatabaseGenerator] has written it. The default mode (no `--with`) produces
 * byte-identical output to a build without this feature.
 */
interface CompanionGenerator {
    /** Stable lowercase identifier, used as the `--with` token and compose service name. */
    val companionName: String

    /**
     * YAML snippet for the companion's entry under the top-level `services:`
     * key. The snippet MUST start with `  <name>:` (two-space indent) and end
     * with a trailing newline.
     */
    fun composeServiceBlock(): String

    /**
     * Environment variables that should be injected into the user's service
     * container so it can reach the companion (e.g. `SMTP_HOST=mailhog`).
     * Returned map is merged into [ServiceComposeConfig.envVars] before the
     * compose file is rendered.
     */
    fun envOverlay(): Map<String, String> = emptyMap()

    /**
     * Named volumes the companion needs at the top-level `volumes:` mapping
     * (e.g. `minio_data`). Each entry is appended as `  <name>:` after the
     * database's own volume entry.
     */
    fun namedVolumes(): List<String> = emptyList()
}
