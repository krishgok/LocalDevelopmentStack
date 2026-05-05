package com.localdevstack.generator

import java.nio.file.Path

interface DatabaseGenerator {
    fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig? = null)
}
