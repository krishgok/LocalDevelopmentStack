package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class DotNetServiceGenerator : ServiceGenerator {

    override val runCommand = "dotnet run"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val controllersDir = serviceDir.resolve("Controllers")
        val servicesDir = serviceDir.resolve("Services")

        listOf(serviceDir, controllersDir, servicesDir).forEach {
            Files.createDirectories(it)
        }

        val ns = toCSharpNamespace(projectName)
        Files.writeString(serviceDir.resolve("$projectName.csproj"), csproj(projectName))
        Files.writeString(serviceDir.resolve("Program.cs"), programCs(ns))
        Files.writeString(controllersDir.resolve("HealthController.cs"), healthControllerCs(ns))
        Files.writeString(servicesDir.resolve("HealthService.cs"), healthServiceCs(ns))

        println("  [OK] .NET service         ->  $serviceDir")
    }

    // C# identifiers must start with a letter/underscore and contain only
    // letters/digits/underscores. The on-disk project name often has hyphens
    // (e.g. "my-api") so we sanitize separately for namespace use.
    private fun toCSharpNamespace(projectName: String): String {
        val cleaned = projectName.replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (cleaned.isEmpty() || cleaned[0].isDigit()) "_$cleaned" else cleaned
    }

    private fun csproj(projectName: String) = """
        <Project Sdk="Microsoft.NET.Sdk.Web">
          <PropertyGroup>
            <TargetFramework>net8.0</TargetFramework>
            <Nullable>enable</Nullable>
            <ImplicitUsings>enable</ImplicitUsings>
            <AssemblyName>$projectName</AssemblyName>
          </PropertyGroup>
        </Project>
    """.trimIndent()

    private fun programCs(ns: String) = """
        using $ns.Services;

        var builder = WebApplication.CreateBuilder(args);
        builder.Services.AddControllers();
        builder.Services.AddScoped<HealthService>();

        var app = builder.Build();
        app.MapControllers();
        app.Run("http://0.0.0.0:8080");
    """.trimIndent()

    private fun healthControllerCs(ns: String) = """
        using Microsoft.AspNetCore.Mvc;
        using $ns.Services;

        namespace $ns.Controllers;

        [ApiController]
        [Route("[controller]")]
        public class HealthController : ControllerBase
        {
            private readonly HealthService _healthService;

            public HealthController(HealthService healthService)
            {
                _healthService = healthService;
            }

            [HttpGet("/health")]
            public IActionResult Health() => Ok(new { status = _healthService.Status() });
        }
    """.trimIndent()

    private fun healthServiceCs(ns: String) = """
        namespace $ns.Services;

        public class HealthService
        {
            public string Status() => "ok";
        }
    """.trimIndent()
}
