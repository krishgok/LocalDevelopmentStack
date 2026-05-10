package com.localdevstack.generator

import com.localdevstack.Logging
import java.nio.file.Files
import java.nio.file.Path

private const val VOLUMES_MARKER = "\nvolumes:\n"
private val log = Logging.named("MigrationComposeAppender")

fun appendMigrateBlockToCompose(composeFile: Path, migrateBlock: String) {
    val original = Files.readString(composeFile)
    val markerIdx = original.indexOf(VOLUMES_MARKER)

    val updated = if (markerIdx == -1) {
        // Marker missing — a DatabaseGenerator emitted a layout that doesn't
        // end with `\nvolumes:\n`. The migrate block is appended at the end
        // as a fallback, but the resulting file may have the migrate service
        // sitting outside `services:` and not work as expected.
        log.warning("compose volumes marker not found in $composeFile; appending migrate block at end of file")
        if (original.endsWith("\n")) original + migrateBlock else original + "\n" + migrateBlock
    } else {
        val before = original.substring(0, markerIdx)
        val after = original.substring(markerIdx)
        before + "\n" + migrateBlock.trimEnd() + after
    }

    Files.writeString(composeFile, updated)
}
