# Maintaining LocalDevelopmentStack

This document is for the repository owner only. It lives in the **private source repo** and is never published to the public distribution repo.

---

## Two-repo model

| Repo | Visibility | Contents |
|------|-----------|---------|
| This repo (private source) | **Private** | Kotlin source, Gradle build, GitHub Actions CI, this file |
| `krishgok/localdevstack` (dist repo) | **Public** | Native binaries (via releases), Homebrew formula, Scoop manifest, install scripts, README |

The CI in this private repo builds native binaries and pushes everything to the public repo automatically on each version tag. End-users never interact with this repo.

---

## One-time setup

Do these steps once before tagging the first release.

### 1. Create the public distribution repository

Create `krishgok/localdevstack` on GitHub with **public** visibility.

### 2. Seed the distribution repository

Copy the following files from this source repo into the root of `krishgok/localdevstack` and push them:

```
Formula/localdevstack.rb      ← Homebrew formula with placeholder sha256 values
bucket/localdevstack.json     ← Scoop manifest with placeholder hash
scripts/install.sh            ← curl installer for macOS/Linux
scripts/install.ps1           ← PowerShell installer for Windows
README.md                     ← End-user documentation
```

These seed files contain `0000...` placeholder hashes. The CI replaces them with real checksums on every release — you only need to seed them once.

### 3. Add the `DIST_TOKEN` secret to this repo

The release workflow pushes artifacts and updates the formula/manifest in `krishgok/localdevstack`. It needs a PAT with write access to that repo.

1. GitHub → your account → **Settings → Developer settings → Personal access tokens → Fine-grained tokens**
2. Create a token scoped to `krishgok/localdevstack` with **Contents: Read and write**
3. In this private source repo → **Settings → Secrets and variables → Actions → New repository secret**
4. Name: `DIST_TOKEN`, value: the token

### 4. (Optional) Test native compilation locally

Verify `nativeCompile` works on your machine before the first tag:

```bash
# Install GraalVM 21 via SDKMAN (macOS/Linux)
sdk install java 21-graalce

# Windows — download from https://www.graalvm.org/downloads/ and set JAVA_HOME

./gradlew nativeCompile
./build/native/nativeCompile/localdevstack --version
```

---

## Tagging a release

Every release after initial setup is a single command.

**Before tagging**, ensure `build.gradle.kts` has the correct version:
```kotlin
version = "1.2.0"   // must match the tag exactly
```

**Tag and push:**
```bash
git tag v1.2.0
git push origin v1.2.0
```

The CI workflow (`.github/workflows/release.yml`) then automatically:
1. Runs all tests
2. Builds native binaries on Linux x64, macOS x64, macOS arm64, Windows x64
3. Runs smoke tests on each binary (new service scaffold + existing service scaffold)
4. Publishes all binaries + `.sha256` files to a GitHub release on `krishgok/localdevstack`
5. Updates `Formula/localdevstack.rb` (version, URLs, sha256 hashes)
6. Updates `bucket/localdevstack.json` (version, URL, hash)
7. Commits and pushes those changes to `krishgok/localdevstack`

After the workflow completes, `brew upgrade localdevstack` and `scoop update localdevstack` pick up the new version automatically.

---

## Triggering a release manually

If you need to re-run a release without pushing a new tag (e.g. a failed workflow):

1. GitHub → this repo → **Actions → Release → Run workflow**
2. Enter the tag to build (e.g. `v1.1.0`)

---

## Repository structure reference

```
LocalDevelopmentStack/          ← private source repo
├── .github/workflows/
│   └── release.yml             ← 4-platform CI + publish to dist repo
├── src/
│   └── main/kotlin/com/localdevstack/
│       ├── LocalDevStackCli.kt         ← picocli CLI, two modes + --migration flag
│       ├── detector/
│       │   └── ExistingServiceDetector.kt
│       └── generator/
│           ├── *ServiceGenerator.kt    ← 9 implementations
│           ├── *DatabaseGenerator.kt   ← 8 implementations
│           ├── *DockerfileGenerator.kt ← 9 implementations (hot-reload)
│           ├── *MigrationGenerator.kt  ← 4 implementations (Flyway, Liquibase,
│           │                              migrate-mongo, golang-migrate) + interface
│           ├── MigrationComposeAppender.kt ← post-processes compose.yml to insert
│           │                                  the migrate service block
│           └── ServiceComposeConfig.kt
├── Formula/
│   └── localdevstack.rb        ← seed for Homebrew formula (CI keeps updated)
├── bucket/
│   └── localdevstack.json      ← seed for Scoop manifest (CI keeps updated)
├── scripts/
│   ├── install.sh              ← curl installer
│   └── install.ps1             ← PowerShell installer
├── README.md                   ← public end-user docs (copy to dist repo)
├── MAINTAINING.md              ← this file (private, do not publish)
├── CLAUDE.md                   ← Claude Code guidance (private)
└── build.gradle.kts            ← Gradle + GraalVM native image config
```

---

## Adding a new service type

1. Implement `ServiceGenerator` (include `override val runCommand`).
2. Subclass `DockerfileGenerator` — only override `protected fun dockerfile(): String`. The base class handles the rest.
3. Add **one entry** to the `SERVICES` map in `LocalDevStackCli`'s companion object: `"<type>" to ServiceSpec(::YourServiceGenerator, ::YourDockerfileGenerator, listOf(".:/app", ...))`.
4. Add parameterized rows in `AllServiceGeneratorsTest` and `AllDockerfileGeneratorsTest`.
5. Update the supported-types tables in `README.md` and `CLAUDE.md`.

## Adding a new database type

1. Implement `DatabaseGenerator`. The compose YAML **must** end with `\nvolumes:\n  <name>_data:` so `appendMigrateBlockToCompose` can splice the migrate block. `MigrationComposeAppenderTest` enforces this.
2. Add **one entry** to the `DATABASES` map in the companion object: `"<type>" to DbSpec(::YourDatabaseGenerator, mapOf("<ENV_KEY>" to "<url>"), { DbConnectionInfo(it, jdbcUrl = "...", user = "...", password = "...") })`. JDBC URL, env var, credentials all in one place.
3. Add the database to `SUPPORTED_MIGRATIONS` (in the same companion object) with the compatible migration tools, or an empty list for "no migration support". The supported-databases error string is auto-derived.
4. Update the supported-types tables in `README.md` and `CLAUDE.md`.

## Adding a new migration tool

1. Implement `MigrationGenerator` (interface in `generator/MigrationGenerator.kt`) — `toolName`, `generateScaffold`, `composeServiceBlock` (must include `profiles: ["migrations"]`, `restart: "no"`, and `depends_on.db.condition: service_healthy`), `createMigrationHint`.
2. For SQL-based tools, reuse `identityColumnSql(databaseType)` from `MigrationSqlHelpers.kt` for the example migration's primary-key column.
3. Add the tool to `LocalDevStackCli.resolveMigrationGenerator()` (the inner factory `when`) and to the `SUPPORTED_MIGRATIONS` map for each compatible DB.
4. Pin the tool image at major+minor (matching the project convention for DB images). For npm-distributed tools, generate a `Dockerfile.migrate` in `generateScaffold` and pin the package version strictly.
5. Add a per-tool unit test in `src/test/kotlin/com/localdevstack/generator/`, plus tuples in `AllMigrationGeneratorsTest.allGenerators()` (and `sqlGenerators()` if SQL-only).
6. Add valid/invalid `(database, tool)` rows to the `@CsvSource` matrices in `LocalDevStackCliTest`.
7. Update the migration tools table in `README.md` and the migration generators table in `CLAUDE.md`.
