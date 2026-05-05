package com.localdevstack.generator

import java.nio.file.Path

interface DockerfileGenerator {
    fun generate(serviceDir: Path, projectName: String)
}
