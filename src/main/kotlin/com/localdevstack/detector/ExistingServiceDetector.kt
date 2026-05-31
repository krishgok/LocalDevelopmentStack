package com.localdevstack.detector

import java.nio.file.Files
import java.nio.file.Path

class DetectionException(message: String) : Exception(message)

object ExistingServiceDetector {

    private val sentinels = listOf(
        "go.mod"          to "go",
        "Cargo.toml"      to "rust",
        "pom.xml"         to "java",
        "build.gradle.kts" to "springboot",
        "build.gradle"    to "springboot",
        "Gemfile"         to "ruby",
        "composer.json"   to "php",
        "package.json"    to "node",
        "requirements.txt" to "python",
        "pyproject.toml"  to "python",
        "Program.cs"      to "dotnet",
    )

    // .NET projects may have only a *.csproj file (older Startup-style apps
    // didn't always emit a top-level Program.cs). Exact-match on Program.cs
    // above handles modern Minimal-API projects; this glob handles the rest.
    private fun csprojFile(dir: Path): String? =
        if (Files.isDirectory(dir)) {
            Files.list(dir).use { stream ->
                stream
                    .map { it.fileName.toString() }
                    .filter { it.endsWith(".csproj") }
                    .findFirst()
                    .orElse(null)
            }
        } else null

    fun detect(dir: Path): String {
        // distinctBy collapses duplicate sentinels of the same type (e.g.
        // build.gradle + build.gradle.kts both → "springboot") so the
        // "multiple types" branch only fires for genuinely different languages.
        val exact = sentinels
            .filter { (file, _) -> Files.exists(dir.resolve(file)) }
            .map { (file, type) -> file to type }

        val withCsproj = if (exact.none { (_, type) -> type == "dotnet" }) {
            csprojFile(dir)?.let { exact + (it to "dotnet") } ?: exact
        } else {
            exact
        }

        val matches = withCsproj.distinctBy { (_, type) -> type }

        if (matches.isEmpty()) {
            throw DetectionException(
                "Could not detect service type in ${dir.toAbsolutePath()}.\n" +
                "No known project file found (go.mod, Cargo.toml, pom.xml, build.gradle.kts,\n" +
                "  Gemfile, composer.json, package.json, requirements.txt, pyproject.toml,\n" +
                "  Program.cs, *.csproj).\n" +
                "Pass --service to specify the type explicitly. Examples:\n" +
                "  localdevstack --existing-dir ${dir.fileName} --service go   --database postgres\n" +
                "  localdevstack --existing-dir ${dir.fileName} --service node --database postgres"
            )
        }

        if (matches.size > 1) {
            val detected = matches.joinToString("\n") { (file, type) -> "  - $file → $type" }
            throw DetectionException(
                "Multiple language indicators found in ${dir.toAbsolutePath()}:\n" +
                "$detected\n\n" +
                "Pass --service to specify which to use. Examples:\n" +
                matches.joinToString("\n") { (_, type) ->
                    "  localdevstack --existing-dir ${dir.fileName} --service $type --database postgres"
                }
            )
        }

        return matches.single().second
    }
}
