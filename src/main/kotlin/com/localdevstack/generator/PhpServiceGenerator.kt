package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class PhpServiceGenerator : ServiceGenerator {

    override val runCommand = "composer install && php artisan serve --host=0.0.0.0 --port=8080"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val routesDir = serviceDir.resolve("routes")
        val appDir = serviceDir.resolve("app/Services")

        listOf(serviceDir, routesDir, appDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("composer.json"), composerJson(projectName))
        Files.writeString(serviceDir.resolve(".env.example"), envExample())
        Files.writeString(routesDir.resolve("api.php"), routesApiPhp())
        Files.writeString(appDir.resolve("HealthService.php"), healthServicePhp())

        println("  [OK] PHP (Laravel) service ->  $serviceDir")
    }

    private fun composerJson(projectName: String) = """
        {
          "name": "example/$projectName",
          "require": {
            "php": "^8.2",
            "laravel/framework": "^11.0"
          },
          "autoload": {
            "psr-4": { "App\\": "app/" }
          },
          "scripts": {
            "post-autoload-dump": [
              "Illuminate\\Foundation\\ComposerScripts::postAutoloadDump",
              "@php artisan package:discover --ansi"
            ]
          }
        }
    """.trimIndent()

    private fun envExample() = """
        APP_NAME=${'$'}{projectName}
        APP_ENV=local
        APP_KEY=
        APP_DEBUG=true
        APP_URL=http://localhost:8080

        DB_CONNECTION=pgsql
        DB_HOST=db
        DB_PORT=5432
        DB_DATABASE=app_db
        DB_USERNAME=postgres
        DB_PASSWORD=postgres_dev_only
    """.trimIndent()

    private fun routesApiPhp() = """
        <?php

        use Illuminate\Support\Facades\Route;
        use App\Services\HealthService;

        Route::get('/health', function () {
            ${'$'}healthService = new HealthService();
            return response()->json(['status' => ${'$'}healthService->status()]);
        });
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
