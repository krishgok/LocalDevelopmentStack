package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class JavaServiceGenerator : ServiceGenerator {

    override val runCommand = "mvn spring-boot:run"

    private val packageName = "com.example"
    private val packagePath = "com/example"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val srcJavaDir = serviceDir.resolve("src/main/java/$packagePath")
        val controllerDir = srcJavaDir.resolve("controller")
        val serviceLayerDir = srcJavaDir.resolve("service")
        val resourcesDir = serviceDir.resolve("src/main/resources")

        listOf(srcJavaDir, controllerDir, serviceLayerDir, resourcesDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("pom.xml"), pomXml(projectName))
        Files.writeString(srcJavaDir.resolve("Application.java"), applicationJava())
        Files.writeString(controllerDir.resolve("HealthController.java"), healthControllerJava())
        Files.writeString(serviceLayerDir.resolve("HealthService.java"), healthServiceJava())
        Files.writeString(resourcesDir.resolve("application.properties"), applicationProperties())

        println("  [OK] Java service         ->  $serviceDir")
    }

    private fun pomXml(projectName: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <parent>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-parent</artifactId>
            <version>3.2.3</version>
          </parent>
          <groupId>com.example</groupId>
          <artifactId>$projectName</artifactId>
          <version>0.0.1-SNAPSHOT</version>
          <properties>
            <java.version>21</java.version>
          </properties>
          <dependencies>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-web</artifactId>
            </dependency>
            <dependency>
              <groupId>org.postgresql</groupId>
              <artifactId>postgresql</artifactId>
              <scope>runtime</scope>
            </dependency>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-test</artifactId>
              <scope>test</scope>
            </dependency>
          </dependencies>
          <build>
            <plugins>
              <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
              </plugin>
            </plugins>
          </build>
        </project>
    """.trimIndent()

    private fun applicationJava() = """
        package $packageName;

        import org.springframework.boot.SpringApplication;
        import org.springframework.boot.autoconfigure.SpringBootApplication;

        @SpringBootApplication
        public class Application {
            public static void main(String[] args) {
                SpringApplication.run(Application.class, args);
            }
        }
    """.trimIndent()

    private fun healthControllerJava() = """
        package $packageName.controller;

        import $packageName.service.HealthService;
        import org.springframework.web.bind.annotation.GetMapping;
        import org.springframework.web.bind.annotation.RestController;
        import java.util.Map;

        @RestController
        public class HealthController {

            private final HealthService healthService;

            public HealthController(HealthService healthService) {
                this.healthService = healthService;
            }

            @GetMapping("/health")
            public Map<String, String> health() {
                return Map.of("status", healthService.status());
            }
        }
    """.trimIndent()

    private fun healthServiceJava() = """
        package $packageName.service;

        import org.springframework.stereotype.Service;

        @Service
        public class HealthService {
            public String status() {
                return "ok";
            }
        }
    """.trimIndent()

    private fun applicationProperties() = """
        spring.application.name=hello-service
        spring.datasource.url=jdbc:postgresql://localhost:5432/app_db
        spring.datasource.username=postgres
        spring.datasource.password=postgres
        spring.datasource.driver-class-name=org.postgresql.Driver
        spring.jpa.hibernate.ddl-auto=update
        server.port=8080
    """.trimIndent()
}
