# Advanced — environment file, dry-run, multi-database

## Environment file

Every generated stack ships a `.env` file alongside `docker-compose.yml`:

- **`.env`** — resolved values (database URL, companion credentials). Already added to `.gitignore` so it doesn't get committed.
- **`.env.example`** — same keys with `<change-me>` placeholders. Safe to commit; use it to onboard new collaborators.

The compose file references each value via `${VAR}`. To rotate credentials, edit `.env` and re-run `docker-compose up`.

In existing-dir mode, `.env` is added to your existing `.gitignore` if missing; your other rules are preserved.

## Preview without writing files

Pass `--dry-run` to print the resolved plan and the files that would be generated, then exit without touching the filesystem.

```bash
localdevstack --service go --database postgres --with minio --dry-run
```

Useful before running in `--existing-dir` mode to confirm exactly which files will land in your repo.

## Multi-database setup

One database per invocation by design. To add a second database (e.g. Redis for caching alongside Postgres):

```bash
# First database — already generated in your service directory:
localdevstack --existing-dir ./my-api --database postgres

# Second database — generate into a temporary directory:
localdevstack --existing-dir ./my-api --database redis --output ./tmp-redis

# Copy the 'db:' (redis) service block from tmp-redis/docker-compose.yml
# into your existing docker-compose.yml under the 'services:' key.
# Rename the service to avoid collision, e.g. 'redis:'.
rm -rf ./tmp-redis
```
