package com.localdevstack.generator

import java.nio.file.Path

/**
 * Generates a `docker-compose.yml` containing a single `db:` service plus
 * optionally a service container block when [serviceConfig] is provided.
 *
 * **Output contract — required for migration post-processing:** the generated
 * file must end with a top-level `volumes:` mapping preceded by a newline,
 * i.e. the bytes `"\nvolumes:\n  <name>_data:"`. `appendMigrateBlockToCompose`
 * uses the `\nvolumes:\n` literal as the insertion-point marker; if a
 * generator emits a different layout the migrate block falls back to
 * end-of-file and a WARNING is logged. `MigrationComposeAppenderTest`
 * exercises this contract for every implementation.
 */
interface DatabaseGenerator {
    fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig? = null)
}
