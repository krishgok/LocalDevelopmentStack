package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RubyDockerfileGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = RubyDockerfileGenerator()

    @Test
    fun `empty directory yields Sinatra Dockerfile`() {
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, "app.rb")
        assertFalse(content.contains("rails"), "no Rails indicators present → must not use rails server")
    }

    @Test
    fun `directory with bin slash rails yields Rails Dockerfile`() {
        Files.createDirectories(tempDir.resolve("bin"))
        Files.writeString(tempDir.resolve("bin/rails"), "#!/usr/bin/env ruby\n")
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, "rails")
        assertContains(content, "server")
        assertContains(content, "-b")
        assertContains(content, "0.0.0.0")
        assertFalse(content.contains("app.rb"), "Rails app must not use Sinatra CMD")
    }

    @Test
    fun `directory with config slash application dot rb yields Rails Dockerfile`() {
        Files.createDirectories(tempDir.resolve("config"))
        Files.writeString(tempDir.resolve("config/application.rb"),
            "module App\n  class Application < Rails::Application\n  end\nend\n")
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, "rails")
        assertFalse(content.contains("app.rb"))
    }

    @Test
    fun `Sinatra and Rails variants both expose 8080`() {
        // Sinatra path
        generator.generate(tempDir, "my-service")
        assertContains(tempDir.resolve("Dockerfile.dev").toFile().readText(), "EXPOSE 8080")

        // Rails path (use a sibling temp dir to avoid carry-over)
        val railsDir = Files.createTempDirectory(tempDir, "rails-")
        Files.createDirectories(railsDir.resolve("bin"))
        Files.writeString(railsDir.resolve("bin/rails"), "#!/usr/bin/env ruby\n")
        generator.generate(railsDir, "my-service")
        assertContains(railsDir.resolve("Dockerfile.dev").toFile().readText(), "EXPOSE 8080")
    }
}
