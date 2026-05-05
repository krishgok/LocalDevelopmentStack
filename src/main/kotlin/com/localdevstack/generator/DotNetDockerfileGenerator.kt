package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class DotNetDockerfileGenerator : DockerfileGenerator {

    override fun generate(serviceDir: Path, projectName: String) {
        val target = dockerfilePath(serviceDir)
        Files.writeString(target, dockerfile(projectName))
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

    private fun dockerfile(projectName: String) = """
        # ── Build stage ─────────────────────────────────────────────────────────────
        FROM mcr.microsoft.com/dotnet/sdk:8.0 AS builder
        WORKDIR /app
        COPY *.csproj ./
        RUN dotnet restore
        COPY . .
        RUN dotnet publish -c Release -o /app/publish

        # ── Runtime stage ────────────────────────────────────────────────────────────
        FROM mcr.microsoft.com/dotnet/aspnet:8.0
        WORKDIR /app
        COPY --from=builder /app/publish .
        EXPOSE 8080
        ENV ASPNETCORE_URLS=http://+:8080
        ENTRYPOINT ["dotnet", "$projectName.dll"]
    """.trimIndent()
}
