package com.localdevstack.generator

class JavaDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM eclipse-temurin:21-jdk-alpine
        RUN apk add --no-cache maven
        WORKDIR /app
        COPY pom.xml ./
        RUN mvn dependency:go-offline -q 2>/dev/null || true
        EXPOSE 8080
        CMD ["mvn", "spring-boot:run", "-q"]
    """.trimIndent()
}
