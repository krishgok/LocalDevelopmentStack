package com.localdevstack.generator

class PythonDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM python:3.10-slim
        WORKDIR /app
        COPY requirements.txt* pyproject.toml* ./
        RUN pip install --no-cache-dir -r requirements.txt 2>/dev/null || pip install --no-cache-dir uvicorn
        EXPOSE 8080
        CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080", "--reload"]
    """.trimIndent()
}
