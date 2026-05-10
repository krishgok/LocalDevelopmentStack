package com.localdevstack.generator

class SpringBootDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM eclipse-temurin:17-jdk-alpine
        WORKDIR /app
        COPY build.gradle.kts settings.gradle.kts gradlew ./
        COPY gradle ./gradle
        RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true
        EXPOSE 8080
        CMD ["./gradlew", "bootRun", "--no-daemon"]
    """.trimIndent()
}
