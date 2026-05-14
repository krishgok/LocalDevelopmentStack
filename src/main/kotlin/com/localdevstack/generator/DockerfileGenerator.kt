package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

abstract class DockerfileGenerator {

    // projectName is reserved for future Dockerfile variants that need it (e.g. LABEL injection).
    @Suppress("UNUSED_PARAMETER")
    fun generate(serviceDir: Path, projectName: String) {
        val target = resolveTarget(serviceDir)
        Files.writeString(target, dockerfile(serviceDir))
        println("  [OK] Dockerfile.dev       ->  $target")
    }

    // Subclasses override exactly one:
    //  - dockerfile() — content is fixed, doesn't depend on what exists in serviceDir (8 of 9)
    //  - dockerfile(serviceDir) — content varies based on existing files in serviceDir
    //    (Ruby: Rails vs Sinatra detection in existing-dir mode)
    protected open fun dockerfile(serviceDir: Path): String = dockerfile()
    protected open fun dockerfile(): String =
        error("DockerfileGenerator subclass must override dockerfile() or dockerfile(serviceDir).")

    private fun resolveTarget(serviceDir: Path): Path {
        val existing = serviceDir.resolve("Dockerfile")
        if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
        }
        return serviceDir.resolve("Dockerfile.dev")
    }
}
