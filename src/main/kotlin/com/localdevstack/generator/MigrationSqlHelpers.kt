package com.localdevstack.generator

/**
 * SQL fragment for an auto-incrementing primary-key column, dialect-aware.
 * Used by example migrations across SQL-based migration tools.
 */
internal fun identityColumnSql(databaseType: String): String =
    when (databaseType.lowercase()) {
        "postgres", "cockroachdb" -> "id    SERIAL PRIMARY KEY"
        "sqlserver"               -> "id    INT IDENTITY(1,1) PRIMARY KEY"
        else                      -> "id    INT AUTO_INCREMENT PRIMARY KEY"
    }
