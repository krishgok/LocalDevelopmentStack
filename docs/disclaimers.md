# Disclaimers & limitations

## Intended use is local development only

Generated artifacts (`Dockerfile.dev`, `docker-compose.yml`, `.env`) are optimised for developer convenience and are not hardened for production. Do not deploy them to shared or production environments without thorough review and changes.

## Default credentials are intentionally insecure

Generated files use well-known development defaults (e.g. `postgres/postgres_dev_only`, `minio_dev/minio_dev_only`). The `.env` file is gitignored to discourage accidental commits, but you are responsible for not exposing local credentials beyond your machine.

## Data is ephemeral

Database and companion data live in named Docker volumes on your machine. Running `docker-compose down -v` permanently deletes all data. Do not use this tool to manage production data.

## Migrations are opt-in and run on demand

The `migrate:` compose service uses the `migrations` profile, so it does **not** auto-start with `docker-compose up`. Invoke it explicitly with `docker-compose run --rm migrate`. Your service container does not depend on `migrate`; run migrations first if your code expects the schema.

## Your existing source code is never modified

In `--existing-dir` mode, LocalDevelopmentStack only writes `Dockerfile.dev`, `docker-compose.yml`, `.env`, `.env.example`, and (idempotently) `.gitignore`. It does not read, parse, or change source files.

## Third-party container images

The generated `docker-compose.yml` references images from Docker Hub, Microsoft Container Registry, and similar third-party registries (Postgres, MySQL, MailHog, MinIO, etc.). Use of those images is governed by their own licenses and terms; this tool does not bundle, modify, or relicense them. You are responsible for compliance.

## Warranty

This software is provided "AS IS", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and noninfringement. To the maximum extent permitted by law, no liability shall attach to the authors or maintainers for damages arising from use of this tool. See the [LICENSE](../LICENSE) file.
