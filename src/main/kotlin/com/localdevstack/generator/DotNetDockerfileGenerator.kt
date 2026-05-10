package com.localdevstack.generator

class DotNetDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM mcr.microsoft.com/dotnet/sdk:8.0
        WORKDIR /app
        COPY *.csproj ./
        RUN dotnet restore
        EXPOSE 8080
        ENV ASPNETCORE_URLS=http://0.0.0.0:8080
        CMD ["dotnet", "watch", "run", "--no-launch-profile"]
    """.trimIndent()
}
