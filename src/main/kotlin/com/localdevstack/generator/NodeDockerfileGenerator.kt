package com.localdevstack.generator

class NodeDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM node:18-alpine
        RUN npm install -g nodemon
        WORKDIR /app
        COPY package*.json ./
        RUN npm install
        EXPOSE 8080
        CMD ["nodemon", "index.js"]
    """.trimIndent()
}
