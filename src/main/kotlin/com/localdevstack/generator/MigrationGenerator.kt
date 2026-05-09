package com.localdevstack.generator

import java.nio.file.Path

data class DbConnectionInfo(
    val databaseType: String,
    val jdbcUrl: String? = null,
    val mongoUri: String? = null,
    val user: String? = null,
    val password: String? = null
)

interface MigrationGenerator {
    val toolName: String

    fun generateScaffold(outputDir: Path, db: DbConnectionInfo, projectName: String)

    fun composeServiceBlock(db: DbConnectionInfo): String

    fun createMigrationHint(): String
}
