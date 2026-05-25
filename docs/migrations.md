# Database migrations (optional)

Pass `--migration <tool>` to scaffold migration files plus a one-shot `migrate:` service in your generated `docker-compose.yml`. The migrate service is tagged with the `migrations` profile so it does **not** auto-start with `docker-compose up` — you run it explicitly when you want to apply migrations.

## Supported tools

| `--migration`    | Compatible databases                                                  | Scaffolds                                                                       |
|------------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `flyway`         | `postgres`, `mysql`, `mariadb`, `sqlserver`, `cockroachdb`            | `migrations/V001__init.sql`                                                     |
| `liquibase`      | `postgres`, `mysql`, `mariadb`, `sqlserver`                           | `db/changelog/db.changelog-master.sql` (Liquibase formatted SQL)                |
| `migrate-mongo`  | `mongodb`                                                             | `Dockerfile.migrate` + `migrate-mongo-config.js` + `migrations/0001-init.js`    |
| `golang-migrate` | `postgres`, `mysql`, `mariadb`, `cockroachdb`, `sqlserver`, `mongodb` | `migrations/000001_init.up.sql` + `000001_init.down.sql` (or `.json` for mongo) |

`redis` and `elasticsearch` do not support migration scaffolding (no schema concept). `cockroachdb` + `liquibase` is rejected because the stock Liquibase image does not ship the CockroachDB driver extension.

## New service with migrations

```bash
localdevstack --service go --database postgres --migration flyway --output ./my-project --name my-api
cd my-project
docker-compose up --build -d          # service + db (migrate skipped — compose profile)
docker-compose run --rm migrate       # apply migrations on demand
```

## Existing service with migrations

```bash
localdevstack --existing-dir ./my-existing-api --database postgres --migration flyway
cd my-existing-api
docker-compose up --build -d          # service + db (migrate skipped)
docker-compose run --rm migrate       # apply migrations on demand
```

If `migrations/` (or `db/changelog/` for Liquibase, or `Dockerfile.migrate` for migrate-mongo) already exists in the target directory and contains files, the CLI aborts to protect your real migrations. Pass `--force` to overwrite.

## Adding more migrations

Drop new files into the appropriate directory using your tool's naming convention, then re-run:

```bash
docker-compose run --rm migrate
```

| Tool             | Add new migration                                                                     |
|------------------|---------------------------------------------------------------------------------------|
| `flyway`         | Create `migrations/V<N>__<name>.sql`                                                  |
| `liquibase`      | Append a new `--changeset` block to `db/changelog/db.changelog-master.sql`            |
| `migrate-mongo`  | `docker-compose run --rm migrate create <name>` then edit the generated file          |
| `golang-migrate` | `docker-compose run --rm migrate create -ext sql -dir /migrations -seq <name>`        |

## Restrictions

- The `migrate:` service is **not** auto-run on `docker-compose up`. Run it explicitly.
- Your service container does **not** depend on `migrate`. Run migrations before starting your service if your code expects the schema to exist.
- One migration tool per stack. Switching tools requires regenerating with `--force`.
- `--name migrate` and `--name db` are rejected when migrations are enabled (would collide with the `migrate:` / `db:` compose service names).
