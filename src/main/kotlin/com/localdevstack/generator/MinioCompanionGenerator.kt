package com.localdevstack.generator

class MinioCompanionGenerator : CompanionGenerator {

    override val companionName: String = "minio"

    override fun composeServiceBlock(): String = buildString {
        appendLine("  minio:")
        appendLine("    image: minio/minio:RELEASE.2024-12-18T13-15-44Z")
        appendLine("    container_name: local-minio")
        appendLine("    command: server /data --console-address \":9001\"")
        appendLine("    environment:")
        appendLine("      MINIO_ROOT_USER: minio_dev")
        appendLine("      MINIO_ROOT_PASSWORD: minio_dev_only")
        appendLine("    ports:")
        appendLine("      - \"9000:9000\"")
        appendLine("      - \"9001:9001\"")
        appendLine("    volumes:")
        appendLine("      - minio_data:/data")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD\", \"curl\", \"-fsS\", \"http://localhost:9000/minio/health/live\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 3s")
        appendLine("      retries: 5")
    }

    override fun envOverlay(): Map<String, String> = mapOf(
        "S3_ENDPOINT" to "http://minio:9000",
        "S3_ACCESS_KEY" to "minio_dev",
        "S3_SECRET_KEY" to "minio_dev_only",
    )

    override fun namedVolumes(): List<String> = listOf("minio_data")
}
