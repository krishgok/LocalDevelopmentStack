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
        return if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
            serviceDir.resolve("Dockerfile.dev")
        } else {
            serviceDir.resolve("Dockerfile.dev")
        }
    }

    private fun dockerfile() = """
        FROM ruby:3.2-slim
        WORKDIR /app
        RUN apt-get update && apt-get install -y build-essential libpq-dev && rm -rf /var/lib/apt/lists/*
        COPY Gemfile Gemfile.lock* ./
        RUN bundle install --without development test
        COPY . .
        EXPOSE 8080
        CMD ["bundle", "exec", "rails", "server", "-p", "8080", "-b", "0.0.0.0"]
    """.trimIndent()
}
