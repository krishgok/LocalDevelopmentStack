package com.localdevstack.generator

import java.nio.file.Path

interface ServiceGenerator {
    val runCommand: String
    fun generate(outputDir: Path, projectName: String)
}
