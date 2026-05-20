package com.localdevstack.generator

class MailhogCompanionGenerator : CompanionGenerator {

    override val companionName: String = "mailhog"

    override fun composeServiceBlock(): String = buildString {
        appendLine("  mailhog:")
        appendLine("    image: mailhog/mailhog:v1.0.1")
        appendLine("    container_name: local-mailhog")
        appendLine("    ports:")
        appendLine("      - \"1025:1025\"")
        appendLine("      - \"8025:8025\"")
        appendLine("    healthcheck:")
        // /dev/tcp is bash-only and the MailHog image uses busybox ash;
        // wget against the HTTP UI port is the most reliable probe available.
        appendLine("      test: [\"CMD-SHELL\", \"wget -q --spider http://localhost:8025/ || exit 1\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 3s")
        appendLine("      retries: 5")
    }

    override fun envOverlay(): Map<String, String> = mapOf(
        "SMTP_HOST" to "mailhog",
        "SMTP_PORT" to "1025",
    )
}
