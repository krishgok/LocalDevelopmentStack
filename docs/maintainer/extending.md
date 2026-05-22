# Extending the tool

How to add a new service type, database type, companion type, or migration tool. Each one is a single map entry in `LocalDevStackCli`'s companion object plus a generator implementation; the supported-list error strings auto-derive from the map.

For external contributors, the high-level PR checklist in [CONTRIBUTING.md → Before you submit a PR](../../CONTRIBUTING.md#before-you-submit-a-pr) is the entry point; the per-extension steps below give the full detail. Every checklist below ends with "run the integration sweep" — see [integration-sweep.md](integration-sweep.md) for how.

---

## Adding a new service type

1. Implement `ServiceGenerator` (include `override val runCommand`).
2. Subclass `DockerfileGenerator` — only override `protected fun dockerfile(): String`. The base class handles the rest.
3. Add **one entry** to the `SERVICES` map in `LocalDevStackCli`'s companion object: `"<type>" to ServiceSpec(::YourServiceGenerator, ::YourDockerfileGenerator, listOf(".:/app", ...))`.
4. Add parameterized rows in `AllServiceGeneratorsTest` and `AllDockerfileGeneratorsTest`.
5. Update the supported-types tables in `README.md` and `CLAUDE.md`.
6. Run the integration sweep against your new service × all 8 databases before merging — see [integration-sweep.md](integration-sweep.md).

### Dockerfile gotchas to consider for a new service

Patterns the 72-combo integration sweep surfaced repeatedly — check each before you submit:

- **Native deps in the chosen runtime.** If your service installs gems / pip wheels / npm packages with C extensions, the slim variant of the base image probably lacks `build-essential`. Either pick the full image (e.g. `ruby:3.2` not `ruby:3.2-slim`) or `apt-get install build-essential` — but the latter adds minutes to the first build on a cold network. Full image is usually the right tradeoff for dev.
- **Hot-reload tooling install cost.** If the hot-reload tool installs from source (`cargo install`, `go install`, source-compiled), the first build can blow past any reasonable timeout. Prefer a pinned pre-built binary from a GitHub release.
- **Database driver build deps.** A single image is used against all 8 databases, so any database-specific extension (e.g. `pdo_pgsql`) needs its build-time deps unconditionally even when the user's DB choice doesn't need them at runtime.
- **Project-name sanitization.** If `projectName` is interpolated anywhere strict (C# namespaces, Go module paths, Rust crate names, Java packages), sanitize it explicitly — picocli accepts hyphens.
- **Lockfile assumptions.** If your service template doesn't write a lockfile, the Dockerfile must use the package manager's lock-optional install (`npm install`, not `npm ci`; `bundle install` without `--frozen`).
- **Framework weight.** The whole point is "single `docker compose up` boots a healthy `/health`." Heavy frameworks (Laravel, Rails) need a `composer install` / `bundle install` that fights this. We've deliberately picked minimal frameworks for PHP (built-in `php -S`) and Ruby (Sinatra) — don't upgrade these without re-running the sweep.

---

## Adding a new database type

1. Implement `DatabaseGenerator`. The compose YAML **must** end with `\nvolumes:\n  <name>_data:` so `appendMigrateBlockToCompose` and `appendCompanionBlocksToCompose` can splice their additions. `MigrationComposeAppenderTest` and `CompanionComposeAppenderTest` enforce this.
2. Add **one entry** to the `DATABASES` map in the companion object: `"<type>" to DbSpec(::YourDatabaseGenerator, mapOf("<ENV_KEY>" to "<url>"), { DbConnectionInfo(it, jdbcUrl = "...", user = "...", password = "...") })`. JDBC URL, env var, credentials all in one place.
3. Add the database to `SUPPORTED_MIGRATIONS` (in the same companion object) with the compatible migration tools, or an empty list for "no migration support". The supported-databases error string is auto-derived.
4. Update the supported-types tables in `README.md` and `CLAUDE.md`.

---

## Adding a new companion type

1. Implement `CompanionGenerator` (interface in `generator/CompanionGenerator.kt`) — `companionName` (lowercase identifier, also the compose service name and `--with` token), `composeServiceBlock()` (YAML snippet starting with `  <name>:`, ending in newline). Optional: `envOverlay()`, `namedVolumes()`.
2. Add **one entry** to the `COMPANIONS` map in `LocalDevStackCli`'s companion object: `"<name>" to CompanionSpec(::YourCompanionGenerator)`. The supported-list error and `--name` collision check are auto-derived.
3. Add rows to `AllCompanionGeneratorsTest.companions()` plus a CLI-level test in `LocalDevStackCliTest` (mirror the existing `--with mailhog` / `--with minio` tests).
4. Score the candidate against the five companion criteria before merging — universal need across personas, zero-config single container, drop-in for a real cloud service, visible UI, mature stable image. If criterion #2 fails, it does not belong behind `--with`.
5. Add the companion to the integration sweep — at minimum, one combo of `--service <any> --database <any> --with <new-companion>` that boots `/health` 200 plus the companion's own health endpoint.
6. Update the companion table in `README.md` and `CLAUDE.md`.

---

## Adding a new migration tool

1. Implement `MigrationGenerator` (interface in `generator/MigrationGenerator.kt`) — `toolName`, `generateScaffold`, `composeServiceBlock` (must include `profiles: ["migrations"]`, `restart: "no"`, and `depends_on.db.condition: service_healthy`), `createMigrationHint`.
2. For SQL-based tools, reuse `identityColumnSql(databaseType)` from `MigrationSqlHelpers.kt` for the example migration's primary-key column.
3. Add the tool to `LocalDevStackCli.resolveMigrationGenerator()` (the inner factory `when`) and to the `SUPPORTED_MIGRATIONS` map for each compatible DB.
4. Pin the tool image at major+minor (matching the project convention for DB images). For npm-distributed tools, generate a `Dockerfile.migrate` in `generateScaffold` and pin the package version strictly.
5. Add a per-tool unit test in `src/test/kotlin/com/localdevstack/generator/`, plus tuples in `AllMigrationGeneratorsTest.allGenerators()` (and `sqlGenerators()` if SQL-only).
6. Add valid/invalid `(database, tool)` rows to the `@CsvSource` matrices in `LocalDevStackCliTest`.
7. Update the migration tools table in `README.md` and the migration generators table in `CLAUDE.md`.
