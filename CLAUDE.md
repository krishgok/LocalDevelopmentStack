# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`LocalDevelopmentStack` is a Kotlin CLI tool that scaffolds a local development environment. It has two modes:

1. **New service scaffold** — given a service type and database, generates a complete runnable service + `docker-compose.yml` from scratch.
2. **Existing service scaffold** (`--existing-dir`) — auto-detects the language in an existing directory, then generates `Dockerfile.dev` + `docker-compose.yml` so the full local stack (service + database) runs with `docker-compose up --build`. Hot-reload is enabled; source changes are picked up automatically without a rebuild.

## Requirements

### To build and run the utility itself

| Dependency | Version | Purpose                                |
| ---------- | ------- | -------------------------------------- |
| JDK        | 17+     | Compile and run the Kotlin CLI         |
| Gradle     | 8+      | Build the fat JAR, run the CLI         |
| Docker     | 24+     | Run the generated `docker-compose.yml` |

### Additional requirements per generated service type (new service scaffold mode)

| `--service`  | Dependency               | Version |
| ------------ | ------------------------ | ------- |
| `springboot` | JDK, Gradle              | 17+, 8+ |
| `go`         | Go                       | 1.22+   |
| `python`     | Python, pip              | 3.10+   |
| `node`       | Node.js, npm             | 18+     |
| `rust`       | Rust (via rustup), Cargo | 1.75+   |
| `dotnet`     | .NET SDK                 | 8+      |
| `java`       | JDK, Maven               | 21+     |
| `php`        | PHP, Composer            | 8.2+    |
| `ruby`       | Ruby, Bundler            | 3.2+    |

> In existing-service mode these are not required on the host — the service runs inside Docker.

### Installing on Windows

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Gradle.Gradle
winget install GoLang.Go
winget install Python.Python.3.12
winget install OpenJS.NodeJS.LTS
winget install Rustlang.Rustup   # then: rustup default stable
```

Docker: [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install) (requires WSL 2).

### Installing on macOS

```bash
brew install temurin@17 gradle go python@3.12 node
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

Docker: [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install).

### Installing on Linux (apt)

```bash
sudo apt install -y temurin-17-jdk   # requires adoptium PPA
curl -s "https://get.sdkman.io" | bash && sdk install gradle
sudo apt install -y docker.io docker-compose-plugin
sudo apt install -y golang-go python3 python3-pip
curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash - && sudo apt install -y nodejs
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

## Commands

Build a runnable fat JAR:

```
gradle shadowJar
```

Run directly via Gradle (new service scaffold, defaults):

```
gradle run
```

Run with explicit options:

```
gradle run --args="--service go --database postgres --output ./my-stack --name my-api"
```

Wrap an existing service directory:

```
gradle run --args="--existing-dir ./my-existing-service --database postgres"
```

Run the built JAR:

```
java -jar build/libs/LocalDevelopmentStack-1.0.0.jar --help
```

## Architecture

The tool is structured around three generator interfaces:

- **`ServiceGenerator`** — generates a complete new service project (new scaffold mode only)
- **`DatabaseGenerator`** — generates `docker-compose.yml` with the chosen database; optionally includes a service container block when a `ServiceComposeConfig` is provided
- **`DockerfileGenerator`** — generates `Dockerfile.dev` for an existing service (existing-dir mode only); always single-stage with hot-reload tooling, never copies source (source is volume-mounted)

`LocalDevStackCli` (picocli `@Command`) dispatches to the correct implementation via `when` blocks. `Main.kt` is the entry point.

### Service generators (9 types)

| `--service`  | Implementation               | Framework / Stack              |
| ------------ | ---------------------------- | ------------------------------ |
| `springboot` | `SpringBootServiceGenerator` | Kotlin + Spring Boot, Gradle   |
| `go`         | `GoServiceGenerator`         | Go + `net/http`                |
| `python`     | `PythonServiceGenerator`     | Python + FastAPI               |
| `node`       | `NodeServiceGenerator`       | Node.js + Express              |
| `rust`       | `RustServiceGenerator`       | Rust + Axum, Cargo             |
| `dotnet`     | `DotNetServiceGenerator`     | C# + ASP.NET Core 8            |
| `java`       | `JavaServiceGenerator`       | Java 21 + Spring Boot, Maven   |
| `php`        | `PhpServiceGenerator`        | PHP 8.2 + Laravel 11           |
| `ruby`       | `RubyServiceGenerator`       | Ruby 3.2 + Rails 7             |

All generated services expose `GET /health` → `{"status":"ok"}`.

### Database generators (8 types)

| `--database`    | Implementation                  | Image                                          | Port  | Injected env var     |
| --------------- | ------------------------------- | ---------------------------------------------- | ----- | -------------------- |
| `postgres`      | `PostgresDatabaseGenerator`     | `postgres:16`                                  | 5432  | `DATABASE_URL`       |
| `mysql`         | `MySqlDatabaseGenerator`        | `mysql:8`                                      | 3306  | `DATABASE_URL`       |
| `mongodb`       | `MongoDbDatabaseGenerator`      | `mongo:7`                                      | 27017 | `MONGODB_URI`        |
| `cockroachdb`   | `CockroachDbDatabaseGenerator`  | `cockroachdb/cockroach:v23.2.0`                | 26257 | `DATABASE_URL`       |
| `redis`         | `RedisDatabaseGenerator`        | `redis:7-alpine`                               | 6379  | `REDIS_URL`          |
| `mariadb`       | `MariaDbDatabaseGenerator`      | `mariadb:11`                                   | 3306  | `DATABASE_URL`       |
| `sqlserver`     | `SqlServerDatabaseGenerator`    | `mcr.microsoft.com/mssql/server:2022-latest`   | 1433  | `DATABASE_URL`       |
| `elasticsearch` | `ElasticsearchDatabaseGenerator`| `elasticsearch:8.12`                           | 9200  | `ELASTICSEARCH_URL`  |

All database services are named `db:` in the compose file so connection URLs use `@db:PORT` consistently.

### Dockerfile generators (9 types, existing-dir mode only)

| `--service`  | Implementation                   | Hot-reload tool                    |
| ------------ | -------------------------------- | ---------------------------------- |
| `springboot` | `SpringBootDockerfileGenerator`  | `./gradlew bootRun`                |
| `go`         | `GoDockerfileGenerator`          | `air` (cosmtrek/air)               |
| `python`     | `PythonDockerfileGenerator`      | `uvicorn --reload`                 |
| `node`       | `NodeDockerfileGenerator`        | `nodemon`                          |
| `rust`       | `RustDockerfileGenerator`        | `cargo-watch`                      |
| `dotnet`     | `DotNetDockerfileGenerator`      | `dotnet watch run`                 |
| `java`       | `JavaDockerfileGenerator`        | `mvn spring-boot:run`              |
| `php`        | `PhpDockerfileGenerator`         | PHP built-in server (serves files on request) |
| `ruby`       | `RubyDockerfileGenerator`        | Rails dev server (auto-reloads)    |

`Dockerfile.dev` is always single-stage. Source code is **never** copied (`COPY . .` is absent); it is volume-mounted at runtime via the compose `volumes:` block.

### Language detection (`ExistingServiceDetector`)

Detects service type from root-level sentinel files:

| Sentinel file(s)                  | Detected type |
| --------------------------------- | ------------- |
| `go.mod`                          | `go`          |
| `Cargo.toml`                      | `rust`        |
| `build.gradle.kts` / `build.gradle` | `springboot` |
| `pom.xml`                         | `java`        |
| `Program.cs` / `*.csproj`         | `dotnet`      |
| `Gemfile`                         | `ruby`        |
| `composer.json`                   | `php`         |
| `package.json`                    | `node`        |
| `requirements.txt` / `pyproject.toml` | `python`  |

Multiple distinct types → `DetectionException` with explicit `--service` override examples.

### Generated output structure

**New service scaffold:**
```
<output>/
├── service/
│   └── <language-specific source files>
│       └── GET /health → {"status":"ok"}
└── docker-compose.yml    # database only; service runs directly on host
```

**Existing service scaffold:**
```
<existing-dir>/
├── <your source files — untouched>
├── Dockerfile.dev        # single-stage, hot-reload, no COPY . .
└── docker-compose.yml    # database + your service container (volume-mounted source)
```

### Adding a new service or database type

1. Implement `ServiceGenerator` (include `override val runCommand`) **or** `DatabaseGenerator`
2. If it's a service type, also implement `DockerfileGenerator` for existing-dir mode
3. Add a `when` branch in `LocalDevStackCli` (`resolveServiceGenerator`, `resolveDockerfileGenerator`, or `resolveDatabaseGenerator`)
4. Add the volumes list in `serviceVolumes()` for the new service type
5. Add the env var mapping in `dbEnvVars()` for a new database type
