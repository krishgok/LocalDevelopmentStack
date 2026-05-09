package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

private const val VOLUMES_MARKER = "\nvolumes:\n"

fun appendMigrateBlockToCompose(composeFile: Path, migrateBlock: String) {
    val original = Files.readString(composeFile)
    val markerIdx = original.indexOf(VOLUMES_MARKER)

    val updated = if (markerIdx == -1) {
        // No top-level volumes block — append the migrate block at the end of services.
        if (original.endsWith("\n")) original + migrateBlock else original + "\n" + migrateBlock
    } else {
        val before = original.substring(0, markerIdx)
        val after = original.substring(markerIdx)
        before + "\n" + migrateBlock.trimEnd() + after
    }

    Files.writeString(composeFile, updated)
}
