package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class RubyDockerfileGenerator : DockerfileGenerator {

    override fun generate(serviceDir: Path, projectName: String) {
        val target = dockerfilePath(serviceDir)
        Files.writeString(target, dockerfile())
        println("  [OK] Dockerfile.dev       ->  $target")
    }

    private fun dockerfilePath(serviceDir: Path): Path {
        val existing = serviceDir.resolve("Dockerfile")
        if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
        }
        return serviceDir.resolve("Dockerfile.dev")
    }

    private fun dockerfile() = """
        FROM ruby:3.2-slim
        RUN apt-get update && apt-get install -y --no-install-recommends build-essential libpq-dev && rm -rf /var/lib/apt/lists/*
        WORKDIR /app
        COPY Gemfile* ./
        RUN bundle install
        EXPOSE 8080
        CMD ["bundle", "exec", "rails", "server", "-b", "0.0.0.0", "-p", "8080"]
    """.trimIndent()
}
