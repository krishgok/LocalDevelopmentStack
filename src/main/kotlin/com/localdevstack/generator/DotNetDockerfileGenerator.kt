package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class DotNetDockerfileGenerator : DockerfileGenerator {

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
        FROM mcr.microsoft.com/dotnet/sdk:8.0
        WORKDIR /app
        COPY *.csproj ./
        RUN dotnet restore
        EXPOSE 8080
        ENV ASPNETCORE_URLS=http://0.0.0.0:8080
        CMD ["dotnet", "watch", "run", "--no-launch-profile"]
    """.trimIndent()
}
