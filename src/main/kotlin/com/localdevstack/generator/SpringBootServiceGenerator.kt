package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class SpringBootServiceGenerator : ServiceGenerator {

    override val runCommand = "gradle bootRun"

    private val packageName = "com.example"
    private val packagePath = "com/example"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val srcKotlinDir = serviceDir.resolve("src/main/kotlin/$packagePath")
        val controllerDir = srcKotlinDir.resolve("controller")
        val serviceLayerDir = srcKotlinDir.resolve("service")
        val resourcesDir = serviceDir.resolve("src/main/resources")

        listOf(srcKotlinDir, controllerDir, serviceLayerDir, resourcesDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("build.gradle.kts"), buildGradleKts())
        Files.writeString(serviceDir.resolve("settings.gradle.kts"), settingsGradleKts(projectName))
        Files.writeString(srcKotlinDir.resolve("Application.kt"), applicationKt())
        Files.writeString(controllerDir.resolve("HealthController.kt"), healthControllerKt())
        Files.writeString(serviceLayerDir.resolve("HealthService.kt"), healthServiceKt())
        Files.writeString(resourcesDir.resolve("application.properties"), applicationProperties())

        println("  [OK] Spring Boot service  ->  $serviceDir")
    }

    private fun buildGradleKts() = """
        plugins {
            kotlin("jvm") version "1.9.22"
            kotlin("plugin.spring") version "1.9.22"
            id("org.springframework.boot") version "3.2.3"
            id("io.spring.dependency-management") version "1.1.4"
        }

        group = "com.example"
        version = "0.0.1-SNAPSHOT"

        repositories {
            mavenCentral()
        }

        dependencies {
            implementation("org.springframework.boot:spring-boot-starter-web")
            implementation("org.springframework.boot:spring-boot-starter-data-jpa")
            implementation("org.jetbrains.kotlin:kotlin-reflect")
            runtimeOnly("org.postgresql:postgresql")
            testImplementation("org.springframework.boot:spring-boot-starter-test")
        }

        kotlin {
            jvmToolchain(17)
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    """.trimIndent()

    private fun settingsGradleKts(projectName: String) = """
        rootProject.name = "$projectName"
    """.trimIndent()

    private fun applicationKt() = """
        package $packageName

        import org.springframework.boot.autoconfigure.SpringBootApplication
        import org.springframework.boot.runApplication

        @SpringBootApplication
        class Application

        fun main(args: Array<String>) {
            runApplication<Application>(*args)
        }
    """.trimIndent()

    private fun healthControllerKt() = """
        package $packageName.controller

        import $packageName.service.HealthService
        import org.springframework.web.bind.annotation.GetMapping
        import org.springframework.web.bind.annotation.RestController

        @RestController
        class HealthController(private val healthService: HealthService) {

            @GetMapping("/health")
            fun health(): Map<String, String> = mapOf("status" to healthService.status())
        }
    """.trimIndent()

    private fun healthServiceKt() = """
        package $packageName.service

        import org.springframework.stereotype.Service

        @Service
        class HealthService {
            fun status(): String = "ok"
        }
    """.trimIndent()

    private fun applicationProperties() = """
        spring.application.name=hello-service

        spring.datasource.url=jdbc:postgresql://localhost:5432/app_db
        spring.datasource.username=postgres
        spring.datasource.password=postgres
        spring.datasource.driver-class-name=org.postgresql.Driver

        spring.jpa.hibernate.ddl-auto=update
        spring.jpa.show-sql=true
        spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

        server.port=8080
    """.trimIndent()
}
