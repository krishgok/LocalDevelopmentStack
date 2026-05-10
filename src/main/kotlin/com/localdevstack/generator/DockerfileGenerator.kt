package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

abstract class DockerfileGenerator {

    // projectName is reserved for future Dockerfile variants that need it (e.g. LABEL injection).
    @Suppress("UNUSED_PARAMETER")
    fun generate(serviceDir: Path, projectName: String) {
        val target = resolveTarget(serviceDir)
        Files.writeString(target, dockerfile())
        println("  [OK] Dockerfile.dev       ->  $target")
    }

    protected abstract fun dockerfile(): String

    private fun resolveTarget(serviceDir: Path): Path {
        val existing = serviceDir.resolve("Dockerfile")
        if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
        }
        return serviceDir.resolve("Dockerfile.dev")
    }
}
