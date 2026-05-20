package com.localdevstack.generator

import com.localdevstack.Logging
import java.nio.file.Files
import java.nio.file.Path

private const val VOLUMES_MARKER = "\nvolumes:\n"
private val log = Logging.named("CompanionComposeAppender")

/**
 * Post-processes the compose file written by a [DatabaseGenerator] to insert
 * each companion's service block above the top-level `volumes:` mapping, and
 * to append any named volumes the companions declare.
 *
 * No-op if [companions] is empty — guarantees byte-identical output for the
 * default invocation that does not use `--with`.
 */
fun appendCompanionBlocksToCompose(composeFile: Path, companions: List<CompanionGenerator>) {
    if (companions.isEmpty()) return

    val original = Files.readString(composeFile)
    val markerIdx = original.indexOf(VOLUMES_MARKER)

    val updated = if (markerIdx == -1) {
        log.warning("compose volumes marker not found in $composeFile; appending companion blocks at end of file")
        buildString {
            append(if (original.endsWith("\n")) original else original + "\n")
            companions.forEach { append(it.composeServiceBlock().trimEnd()).append("\n") }
        }
    } else {
        val before = original.substring(0, markerIdx)
        val after = original.substring(markerIdx)
        buildString {
            append(before)
            companions.forEach {
                append("\n").append(it.composeServiceBlock().trimEnd()).append("\n")
            }
            append(after)
            val namedVolumes = companions.flatMap { it.namedVolumes() }
            if (namedVolumes.isNotEmpty()) {
                if (!endsWith("\n")) append("\n")
                namedVolumes.forEach { append("  ").append(it).append(":\n") }
            }
        }
    }

    Files.writeString(composeFile, updated)
}
