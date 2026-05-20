package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

/**
 * Ensures `.env` is excluded from version control next to the generated
 * compose file. In new-scaffold mode the file is created fresh; in
 * existing-dir mode the entries are appended idempotently so the user's
 * existing rules are preserved.
 */
class GitignoreGenerator {

    private val freshEntries = listOf(".env", "*.local")
    private val criticalEntry = ".env"

    fun generate(outputDir: Path) {
        Files.createDirectories(outputDir)
        val gitignore = outputDir.resolve(".gitignore")
        if (Files.exists(gitignore)) {
            val current = Files.readString(gitignore)
            val existing = current.lineSequence().map { it.trim() }.toSet()
            if (criticalEntry in existing) return
            val sep = if (current.endsWith("\n")) "\n" else "\n\n"
            val appended = current + sep + "# Added by LocalDevelopmentStack\n$criticalEntry\n"
            Files.writeString(gitignore, appended)
            println("  [OK] .gitignore (appended) ->  $gitignore")
        } else {
            Files.writeString(gitignore, buildString {
                appendLine("# Added by LocalDevelopmentStack")
                freshEntries.forEach { appendLine(it) }
            })
            println("  [OK] .gitignore           ->  $gitignore")
        }
    }
}
