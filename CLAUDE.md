# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`LocalDevelopmentStack` is a Kotlin CLI tool that scaffolds a local development environment. Given a service type and a database type as inputs, it generates a ready-to-run project containing the service source code and database configuration.

## Requirements

### To build and run the utility itself

| Dependency | Version | Purpose |
|---|---|---|
| JDK | 17+ | Compile and run the Kotlin CLI |
| Gradle | 8+ | Build the fat JAR, run the CLI |
| Docker | 24+ | Run the generated `docker-compose.yml` |

### Additional requirements per generated service type

| `--service` | Dependency | Version |
|---|---|---|
| `springboot` | JDK, Gradle | 17+, 8+ |
| `go` | Go | 1.22+ |
| `python` | Python, pip | 3.10+ |
| `node` | Node.js, npm | 18+ |
| `rust` | Rust (via rustup), Cargo | 1.75+ |

### Installing on Windows

**JDK 17+**
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```
Or download from [Adoptium](https://adoptium.net).

**Gradle 8+**
```powershell
winget install Gradle.Gradle
```

**Docker**
Download [Docker Desktop for Windows](https://docs.docker.com/desktop/install/windows-install) (requires WSL 2).

**Go**
```powershell
winget install GoLang.Go
```

**Python**
```powershell
winget install Python.Python.3.12
```

**Node.js**
```powershell
winget install OpenJS.NodeJS.LTS
```

**Rust**
```powershell
winget install Rustlang.Rustup
```
Then run `rustup default stable`.

### Installing on macOS

```bash
brew install temurin@17 gradle go python@3.12 node
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```
Docker: download [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install).

### Installing on Linux (apt)

```bash
# JDK
sudo apt install -y temurin-17-jdk   # requires adoptium PPA

# Gradle (via sdkman is recommended)
curl -s "https://get.sdkman.io" | bash && sdk install gradle

# Docker
sudo apt install -y docker.io docker-compose-plugin

# Go
sudo apt install -y golang-go

# Python
sudo apt install -y python3 python3-pip

# Node.js (via NodeSource)
curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash -
sudo apt install -y nodejs

# Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

## Commands

Build a runnable fat JAR:
```
gradle shadowJar
```

Run directly via Gradle (with defaults):
```
gradle run
```

Run with explicit options:
```
gradle run --args="--service springboot --database postgres --output ./my-stack --name my-service"
```

Run the built JAR:
```
java -jar build/libs/LocalDevelopmentStack-1.0.0.jar --help
```

## Architecture

The tool is structured around two generator interfaces:

- **`ServiceGenerator`** — generates the service project files
- **`DatabaseGenerator`** — generates the database configuration

`LocalDevStackCli` (picocli `@Command`) wires them together based on `--service` and `--database` flags and dispatches to the correct implementation via a `when` block. `Main.kt` is the entry point.

### Current implementations

| Flag value | Implementation | What it generates |
|---|---|---|
| `--service springboot` | `SpringBootServiceGenerator` | Kotlin + Spring Boot, REST controller + service layer, Gradle |
| `--service go` | `GoServiceGenerator` | Go + `net/http`, handler + service layer |
| `--service python` | `PythonServiceGenerator` | Python + FastAPI, router + service layer |
| `--service node` | `NodeServiceGenerator` | Node.js + Express, routes + service layer |
| `--service rust` | `RustServiceGenerator` | Rust + Axum, routes + service layer, Cargo |
| `--database postgres` | `PostgresDatabaseGenerator` | `docker-compose.yml` with Postgres 16 (port 5432) |
| `--database mysql` | `MySqlDatabaseGenerator` | `docker-compose.yml` with MySQL 8 (port 3306) |
| `--database mongodb` | `MongoDbDatabaseGenerator` | `docker-compose.yml` with MongoDB 7 (port 27017) |
| `--database cockroachdb` | `CockroachDbDatabaseGenerator` | `docker-compose.yml` with CockroachDB v23.2 (SQL port 26257, Admin UI 8090) |

### Generated output structure

```
<output>/
├── service/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/com/example/
│       ├── Application.kt
│       ├── controller/HelloController.kt   # GET /api/hello
│       └── service/HelloService.kt
│   └── src/main/resources/
│       └── application.properties          # connects to localhost:5432/app_db
└── docker-compose.yml                      # postgres:16, port 5432
```

### Adding a new service or database type

1. Implement `ServiceGenerator` (include `override val runCommand`) or `DatabaseGenerator`
2. Add a `when` branch in `LocalDevStackCli.run()`

The generated Spring Boot `application.properties` always points to `localhost:5432/app_db` with credentials `postgres/postgres`, matching the Docker Compose defaults.
