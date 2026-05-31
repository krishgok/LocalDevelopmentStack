# Using LocalDevelopmentStack with an AI assistant

This page is for AI coding assistants (Claude Code, Cursor, Aider, GitHub Copilot Workspace, Continue, etc.) and the developers directing them. It's a condensed reference covering invocation patterns, the flag surface, and a safe-preview workflow.

## What this tool does

LocalDevelopmentStack generates a Docker-ised local dev stack — service container + database container, with hot-reload — from a single CLI invocation. Two modes:

- **New service** — generates source code + `Dockerfile.dev` + `docker-compose.yml`. Source picks from 9 language templates.
- **Existing service** — detects language from sentinel files (`go.mod`, `package.json`, etc.), generates only `Dockerfile.dev` + `docker-compose.yml`. Source is untouched.

In both modes, source is **volume-mounted, not copied** — edits hot-reload without a rebuild.

## Invocation patterns

New service (most common):

```bash
localdevstack --service <type> --database <db> --output <dir> --name <project>
```

Existing service:

```bash
localdevstack --existing-dir <dir> --database <db>
```

With migration tooling:

```bash
localdevstack --service <type> --database <db> --migration <tool> --output <dir>
```

With opt-in companion services:

```bash
localdevstack --service <type> --database <db> --with <name>[,<name>...] --output <dir>
```

## Flag surface

| Flag | Default | Purpose |
|---|---|---|
| `--service` | `go` | New-service mode: `go`, `node`, `python`, `rust`, `java`, `springboot`, `dotnet`, `php`, `ruby` |
| `--database` | `postgres` | Database container: `postgres`, `mysql`, `mongodb`, `cockroachdb`, `redis`, `mariadb`, `sqlserver`, `elasticsearch` |
| `--existing-dir` | — | Existing-service mode; mutually exclusive with `--service` |
| `--output` | `./local-dev-stack` | Output directory for new-service mode |
| `--name` | `local-dev-stack` | Project name (used in compose service name and `.env`) |
| `--migration` | (off) | Migration tool: `flyway`, `liquibase`, `migrate-mongo`, `golang-migrate` |
| `--with` | (off) | Comma-list of companions: `mailhog`, `minio` |
| `--port` | auto | Pin the service port (defaults auto-select 8080 → 8081 → 8082) |
| `--force` | false | Overwrite existing files (idempotent regenerate) |
| `--dry-run` | false | Print the resolved plan + file list, write nothing |

## Safe preview — `--dry-run`

For agents, `--dry-run` is the safest entry point. It:

- Skips the Docker availability check (so it works on machines without Docker)
- Writes nothing to disk
- Prints the resolved plan and the list of files that would be generated
- Returns exit code 0 on a valid plan, non-zero on validation failures

Use `--dry-run` first to confirm flag compatibility (e.g. `--migration liquibase --database cockroachdb` is rejected with a clear error), then drop the flag for the real run.

## Canned prompts → CLI mapping

> "Generate a Go service with Postgres in `./api`. Use Flyway for migrations."
> → `localdevstack --service go --database postgres --migration flyway --output ./api --name api`

> "I have a Python project in `./app` — wrap it in a docker-compose stack with MongoDB."
> → `localdevstack --existing-dir ./app --database mongodb`

> "Show me what would be generated for a Rust + Redis stack with MinIO."
> → `localdevstack --service rust --database redis --with minio --output ./svc --name svc --dry-run`

> "Spring Boot + SQL Server + Liquibase + MailHog catcher for outgoing email."
> → `localdevstack --service springboot --database sqlserver --migration liquibase --with mailhog --output ./platform-api --name platform-api`

## Compatibility constraints

These combinations are rejected at validation time (no files written):

- `--migration` with `--database redis` or `--database elasticsearch` — no migration support for these engines
- `--migration liquibase --database cockroachdb` — Liquibase's stock image lacks the CockroachDB driver
- `--name migrate` with `--migration` set — would collide with the `migrate:` compose service name
- `--name db` — would collide with the database compose service name
- `--name mailhog` or `--name minio` with the corresponding `--with` — would collide with companion compose service names

Run with `--dry-run` to surface the validation error without side effects.

## Output contract

Every generated stack has:

- A `docker-compose.yml` at the output root
- A `Dockerfile.dev` (single-stage, with hot-reload tooling appropriate to the language)
- A `.env` (resolved values, gitignored) and `.env.example` (placeholders, safe to commit)
- A `.gitignore` — new-service mode: fresh file; existing-service mode: idempotent append
- (optional) `migrations/` or `db/changelog/` and `Dockerfile.migrate` when `--migration` is set

Every service exposes `GET /health` → `{"status":"ok"}`.

The stack runs with one command: `docker compose up --build` (or `docker-compose up --build` on older Docker installations).

## More

- [README.md](README.md) — feature overview and install instructions
- [docs/usage-new-service.md](docs/usage-new-service.md) — new-service walkthrough
- [docs/usage-existing-service.md](docs/usage-existing-service.md) — existing-service walkthrough
- [docs/migrations.md](docs/migrations.md) — migration tool details
- [docs/companions.md](docs/companions.md) — companion service details
- [docs/advanced.md](docs/advanced.md) — power-user flags
