package com.localdevstack.generator

class GoDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM golang:1.25-alpine
        RUN go install github.com/air-verse/air@v1.65.1
        WORKDIR /app
        COPY go.mod go.sum* ./
        RUN go mod download
        EXPOSE 8080
        CMD ["air", "--build.cmd", "go build -o ./tmp/main .", "--build.bin", "./tmp/main"]
    """.trimIndent()
}
