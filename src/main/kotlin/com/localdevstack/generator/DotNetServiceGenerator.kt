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

        Files.writeString(serviceDir.resolve("$projectName.csproj"), csproj(projectName))
        Files.writeString(serviceDir.resolve("Program.cs"), programCs())
        Files.writeString(controllersDir.resolve("HealthController.cs"), healthControllerCs(projectName))
        Files.writeString(servicesDir.resolve("HealthService.cs"), healthServiceCs(projectName))

        println("  [OK] .NET service         ->  $serviceDir")
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

    private fun programCs() = """
        var builder = WebApplication.CreateBuilder(args);
        builder.Services.AddControllers();
        builder.Services.AddScoped<Services.HealthService>();

        var app = builder.Build();
        app.MapControllers();
        app.Run("http://0.0.0.0:8080");
    """.trimIndent()

    private fun healthControllerCs(projectName: String) = """
        using Microsoft.AspNetCore.Mvc;
        using $projectName.Services;

        namespace $projectName.Controllers;

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

    private fun healthServiceCs(projectName: String) = """
        namespace $projectName.Services;

        public class HealthService
        {
            public string Status() => "ok";
        }
    """.trimIndent()
}
