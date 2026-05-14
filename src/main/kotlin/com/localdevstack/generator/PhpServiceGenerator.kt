package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class PhpServiceGenerator : ServiceGenerator {

    override val runCommand = "php -S 0.0.0.0:8080 -t public"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val publicDir = serviceDir.resolve("public")
        val appDir = serviceDir.resolve("app/Services")

        listOf(serviceDir, publicDir, appDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("composer.json"), composerJson(projectName))
        Files.writeString(publicDir.resolve("index.php"), indexPhp())
        Files.writeString(appDir.resolve("HealthService.php"), healthServicePhp())

        println("  [OK] PHP service          ->  $serviceDir")
    }

    private fun composerJson(projectName: String) = """
        {
          "name": "example/$projectName",
          "require": {
            "php": "^8.2"
          },
          "autoload": {
            "psr-4": { "App\\": "app/" }
          }
        }
    """.trimIndent()

    private fun indexPhp() = """
        <?php
        require __DIR__ . '/../app/Services/HealthService.php';

        ${'$'}path = parse_url(${'$'}_SERVER['REQUEST_URI'], PHP_URL_PATH);
        if (${'$'}path === '/health') {
            header('Content-Type: application/json');
            echo json_encode(['status' => (new App\Services\HealthService())->status()]);
            return;
        }
        http_response_code(404);
        echo json_encode(['error' => 'not found']);
    """.trimIndent()

    private fun healthServicePhp() = """
        <?php

        namespace App\Services;

        class HealthService
        {
            public function status(): string
            {
                return 'ok';
            }
        }
    """.trimIndent()
}
