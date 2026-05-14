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
6. Run the integration sweep against your new service × all 8 databases before merging (see "Integration sweep" below).

### Dockerfile gotchas to consider for a new service

Patterns the 72-combo integration sweep surfaced repeatedly — check each before you submit:

- **Native deps in the chosen runtime.** If your service installs gems / pip wheels / npm packages with C extensions, the slim variant of the base image probably lacks `build-essential`. Either pick the full image (e.g. `ruby:3.2` not `ruby:3.2-slim`) or `apt-get install build-essential` — but the latter adds minutes to the first build on a cold network. Full image is usually the right tradeoff for dev.
- **Hot-reload tooling install cost.** If the hot-reload tool installs from source (`cargo install`, `go install`, source-compiled), the first build can blow past any reasonable timeout. Prefer a pinned pre-built binary from a GitHub release.
- **Database driver build deps.** A single image is used against all 8 databases, so any database-specific extension (e.g. `pdo_pgsql`) needs its build-time deps unconditionally even when the user's DB choice doesn't need them at runtime.
- **Project-name sanitization.** If `projectName` is interpolated anywhere strict (C# namespaces, Go module paths, Rust crate names, Java packages), sanitize it explicitly — picocli accepts hyphens.
- **Lockfile assumptions.** If your service template doesn't write a lockfile, the Dockerfile must use the package manager's lock-optional install (`npm install`, not `npm ci`; `bundle install` without `--frozen`).
- **Framework weight.** The whole point is "single `docker compose up` boots a healthy `/health`." Heavy frameworks (Laravel, Rails) need a `composer install` / `bundle install` that fights this. We've deliberately picked minimal frameworks for PHP (built-in `php -S`) and Ruby (Sinatra) — don't upgrade these without re-running the sweep.

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

---

## Integration sweep (`claude-test-sweep.sh`)

Unit tests cover generator output structure; they do not verify that a generated stack actually runs. The integration sweep covers that gap.

`claude-test-sweep.sh` iterates every `(--service, --database)` pair (9 × 8 = 72 combos), generates each into `claude-test-stack-v*/`, runs `docker compose up -d --build`, polls `http://localhost:8080/health` until a 200, records the result to `claude-test-results.tsv`, then tears down. It is **not** wired into CI — Docker images alone are ~10 GB and a cold sweep is ~2 hours — but run it locally before merging anything that touches a `*ServiceGenerator`, `*DockerfileGenerator`, or `*DatabaseGenerator`.

**Prerequisites:** Docker Desktop running, and a fresh fat JAR at `build/libs/LocalDevelopmentStack-<version>.jar` matching the hard-coded path in the script.

```bash
# Full 72-combo sweep (rebuild JAR first to pick up any generator changes)
gradle shadowJar && bash claude-test-sweep.sh

# Scoped re-runs (resume logic skips combos already in claude-test-results.tsv)
SVC_FILTER=ruby bash claude-test-sweep.sh                    # one service, all 8 DBs
SVC_FILTER=ruby DB_FILTER=postgres bash claude-test-sweep.sh # single combo

# Force a full re-sweep
rm claude-test-results.tsv && bash claude-test-sweep.sh
```

The sweep is long-running (cold first run ~2 hours; warm re-runs ~30 min). Detach it from your shell if you need the terminal back: `nohup bash claude-test-sweep.sh > sweep.out 2>&1 &` then `tail -F claude-test-results.tsv` to follow progress.

Resume logic: `claude-test-results.tsv` is append-only and the script skips any combo already recorded. To re-run a failing combo, delete its row first. To force a full re-sweep, delete the file (the header is regenerated). The append behaviour is how `SVC_FILTER` / `DB_FILTER` reruns add to the same results table without clobbering prior passes.

Per-combo logs land in `claude-test-logs/{svc}_{db}-up.log` (compose build + container logs). The harness keeps these around after pass/fail; nuke the directory when you're done.

`STACK_DIR` is bumped (v2 → v3 → ...) whenever Docker Desktop locks the folder mid-sweep and won't release it; the simplest workaround is to use a fresh folder name rather than fight the lock. The artifacts (`claude-test-stack-v*/`, `claude-test-results.tsv`, `claude-test-logs/`, `sweep.out`) are gitignored intentionally.

Per-combo budget is `TIMEOUT=900` seconds. The first build of a service tier (cold cargo / cold Maven / cold composer) eats most of that; subsequent combos in the same tier reuse Docker layer cache and finish in ~30-60s.

### Last full-sweep status

Update this table whenever you run a full sweep (drop the row, add a new one). It's the durable answer to "does end-to-end still work?" — the per-combo logs themselves aren't worth committing.

| Date       | JAR version | Result      | Notes                                                            |
|------------|-------------|-------------|------------------------------------------------------------------|
| 2026-05-14 | 1.1.0       | 72/72 PASS  | 9 services × 8 databases, all `/health` 200. Host: Windows 11.   |
