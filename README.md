# LocalDevelopmentStack

Instantly scaffold a containerised local development environment for any service and database — with a single command.

Two modes — both produce a stack that comes up with a single `docker-compose up --build`:

- **New service** — generates a complete runnable service + `Dockerfile.dev` + `docker-compose.yml` from scratch. Hot-reload enabled.
- **Existing service** — wraps your existing code with a `Dockerfile.dev` + `docker-compose.yml`. Hot-reload is enabled; source changes are picked up automatically inside the container.

Optional **database migrations** — pass `--migration <tool>` (Flyway, Liquibase, migrate-mongo, golang-migrate) to scaffold an example migration plus a one-shot `migrate:` service that runs on demand via `docker-compose run --rm migrate`.

> **No JVM required.** LocalDevelopmentStack is distributed as a self-contained native binary.

---

## Installation

### macOS / Linux — Homebrew

```bash
brew tap krishgok/localdevstack
brew install localdevstack
```

### Windows — Scoop

```powershell
scoop bucket add localdevstack https://github.com/krishgok/localdevstack
scoop install localdevstack
```

### macOS / Linux — curl

```bash
curl -fsSL https://raw.githubusercontent.com/krishgok/localdevstack/main/scripts/install.sh | bash
```

### Windows — PowerShell

```powershell
irm https://raw.githubusercontent.com/krishgok/localdevstack/main/scripts/install.ps1 | iex
```

---

## Usage: Scaffold a new service

Generates source code + `Dockerfile.dev` + `docker-compose.yml` from scratch. Hot-reload is enabled — edit source files and the watcher inside the container recompiles/restarts your service automatically.

**Step 1 — Generate the stack:**

```bash
localdevstack --service go --database postgres --output ./my-project --name my-api
```

This produces:

```
my-project/
├── service/
│   ├── <generated source files>
│   └── Dockerfile.dev      ← dev container for your service (hot-reload enabled)
└── docker-compose.yml      ← your service + the database, fully wired
```

**Step 2 — Start the full local stack:**

```bash
cd my-project
docker-compose up --build
```

This builds the dev image on first run and starts both the database and your service. Subsequent `docker-compose up` calls skip the build.

**Step 3 — Verify:**

```bash
curl http://localhost:8080/health
# → {"status":"ok"}
```

**Step 4 — Edit, save, and see changes reload automatically:**

Hot-reload works the same way as in [existing-service mode](#step-4--edit-save-and-see-changes-reload-automatically) — edit a source file under `service/`, the watcher inside the container picks it up. Only re-run `docker-compose up --build` if you change `Dockerfile.dev` itself or add/remove dependencies (e.g. `go.mod`, `package.json`, `requirements.txt`).

---

## Usage: Wrap an existing service

Points the tool at your existing code. Generates `Dockerfile.dev` + `docker-compose.yml` so everything runs inside Docker with **hot-reload enabled**.

**Step 1 — Run from or above your service directory:**

```bash
localdevstack --existing-dir ./my-existing-api --database postgres
```

The tool auto-detects the language from your project files. Pass `--service` to override:

```bash
localdevstack --existing-dir ./my-existing-api --service node --database postgres
```

**Step 2 — Review the generated files:**

```
my-existing-api/
├── <your source files — untouched>
├── Dockerfile.dev      ← dev container for your service (hot-reload enabled)
└── docker-compose.yml  ← your service + the database, fully wired
```

`docker-compose.yml` injects the database connection string as an environment variable (e.g. `DATABASE_URL`) so your service connects to the local database automatically.

**Step 3 — Start the full local stack:**

```bash
cd my-existing-api
docker-compose up --build
```

This builds the dev image on first run. Subsequent `docker-compose up` calls skip the build.

**Step 4 — Edit, save, and see changes reload automatically:**

Hot-reload is enabled — there is no need to run `docker-compose up --build` again after editing source files. The watcher inside the container picks up changes and recompiles/restarts your service automatically.

| Language   | Hot-reload tool                                    |
|------------|----------------------------------------------------|
| Go         | [`air`](https://github.com/air-verse/air) (file watcher + incremental rebuild) |
| Node.js    | [`nodemon`](https://nodemon.io)                    |
| Python     | `uvicorn --reload`                                 |
| Ruby       | Rails dev server (auto-reloads in development)     |
| PHP        | PHP built-in server (serves files on request)      |
| Spring Boot| `./gradlew bootRun`                                |
| Java       | `mvn spring-boot:run`                              |
| .NET       | `dotnet watch run`                                 |
| Rust       | [`cargo-watch`](https://crates.io/crates/cargo-watch) |

> **When to rebuild:** Run `docker-compose up --build` again only if you change `Dockerfile.dev` itself or add/remove dependencies (e.g. change `go.mod`, `package.json`, `requirements.txt`).

**Step 5 — Verify your service:**

```bash
curl http://localhost:8080/health   # or your own endpoint
```

LocalDevelopmentStack does not add or modify any endpoints in your service.

---

## Supported service types

| `--service`  | Language / Framework        | Sentinel file detected        | Default port |
|--------------|-----------------------------|-------------------------------|--------------|
| `springboot` | Kotlin + Spring Boot        | `build.gradle.kts`, `build.gradle` | 8080    |
| `go`         | Go + net/http               | `go.mod`                      | 8080         |
| `python`     | Python + FastAPI            | `requirements.txt`, `pyproject.toml` | 8080  |
| `node`       | Node.js + Express           | `package.json`                | 8080         |
| `rust`       | Rust + Axum                 | `Cargo.toml`                  | 8080         |
| `dotnet`     | C# + ASP.NET Core 8         | `*.csproj`, `Program.cs`      | 8080         |
| `java`       | Java 21 + Spring Boot (Maven) | `pom.xml`                   | 8080         |
| `php`        | PHP 8.2 + Laravel 11        | `composer.json`               | 8080         |
| `ruby`       | Ruby 3.2 + Rails 7          | `Gemfile`                     | 8080         |

The port auto-selects 8080 → 8081 → 8082 if lower ports are occupied. Pass `--port` to set it explicitly.

---

## Supported database types

| `--database`    | Engine            | Version                    | Port  | Injected env var     |
|-----------------|-------------------|----------------------------|-------|----------------------|
| `postgres`      | PostgreSQL        | 16                         | 5432  | `DATABASE_URL`       |
| `mysql`         | MySQL             | 8                          | 3306  | `DATABASE_URL`       |
| `mongodb`       | MongoDB           | 7                          | 27017 | `MONGODB_URI`        |
| `cockroachdb`   | CockroachDB       | v23.2                      | 26257 | `DATABASE_URL`       |
| `redis`         | Redis             | 7-alpine                   | 6379  | `REDIS_URL`          |
| `mariadb`       | MariaDB           | 11                         | 3306  | `DATABASE_URL`       |
| `sqlserver`     | SQL Server        | 2022-latest                | 1433  | `DATABASE_URL`       |
| `elasticsearch` | Elasticsearch     | 8.12                       | 9200  | `ELASTICSEARCH_URL`  |

The injected connection string uses `db` as the hostname — this resolves correctly inside the Docker Compose network.

### Reading the connection in your service

Your service must read the connection string from the environment variable at startup, not from a hardcoded string or config file checked into source control. This is standard [12-factor app](https://12factor.net/config) practice and ensures the same code works locally (pointing at Docker) and in production (pointing at your real database) without any changes.

**Go**
```go
import "os"

dsn := os.Getenv("DATABASE_URL")
// e.g. "postgresql://postgres:postgres_dev_only@db:5432/app_db"
db, err := sql.Open("pgx", dsn)
```

**Python (FastAPI / SQLAlchemy)**
```python
import os
DATABASE_URL = os.environ["DATABASE_URL"]
engine = create_engine(DATABASE_URL)
```

**Node.js**
```js
const { DATABASE_URL } = process.env;
const pool = new Pool({ connectionString: DATABASE_URL });
```

**Spring Boot (Kotlin / Java)**

Spring Boot reads `DATABASE_URL` automatically when you set `spring.datasource.url` to the env var in `application.properties`:
```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASS:postgres_dev_only}
```
Or in `application.yml`:
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
```

**Rust (sqlx)**
```rust
let database_url = std::env::var("DATABASE_URL").expect("DATABASE_URL must be set");
let pool = PgPool::connect(&database_url).await?;
```

**PHP (Laravel)**

Laravel reads `DATABASE_URL` from `.env` automatically. If you use a Docker Compose env var instead of a `.env` file, set it in `config/database.php`:
```php
'url' => env('DATABASE_URL'),
```

**Ruby (Rails)**

Rails reads `DATABASE_URL` from the environment automatically — no code change needed. If `DATABASE_URL` is set, it overrides `config/database.yml`.

**.NET (ASP.NET Core)**
```csharp
var connectionString = builder.Configuration["DATABASE_URL"]
    ?? Environment.GetEnvironmentVariable("DATABASE_URL");
builder.Services.AddDbContext<AppDbContext>(opt =>
    opt.UseNpgsql(connectionString));
```

**Redis** (`REDIS_URL`)
```go
// Go
redisUrl := os.Getenv("REDIS_URL") // "redis://db:6379"
```
```js
// Node.js
const client = createClient({ url: process.env.REDIS_URL });
```
```python
# Python
import redis, os
r = redis.from_url(os.environ["REDIS_URL"])
```

**Elasticsearch** (`ELASTICSEARCH_URL`)
```js
// Node.js
const { Client } = require('@elastic/elasticsearch');
const client = new Client({ node: process.env.ELASTICSEARCH_URL });
```
```python
# Python
from elasticsearch import Elasticsearch
import os
es = Elasticsearch(os.environ["ELASTICSEARCH_URL"])
```

> If your service currently uses a hardcoded host like `localhost:5432`, replace it with the env var read shown above. The Docker Compose network resolves `db` to the database container; on your host (outside Docker) `localhost` works as before — so reading from an env var with a sensible local default keeps both workflows working:
> ```go
> dsn := os.Getenv("DATABASE_URL")
> if dsn == "" {
>     dsn = "postgresql://postgres:postgres@localhost:5432/app_db"
> }
> ```

---

## Database migrations (optional)

Pass `--migration <tool>` to scaffold migration files plus a one-shot `migrate:` service in your generated `docker-compose.yml`. The migrate service is tagged with the `migrations` profile so it does **not** auto-start with `docker-compose up` — you run it explicitly when you want to apply migrations.

### Supported tools

| `--migration`    | Compatible databases                                                  | Scaffolds                                                                       |
|------------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `flyway`         | `postgres`, `mysql`, `mariadb`, `sqlserver`, `cockroachdb`            | `migrations/V001__init.sql`                                                     |
| `liquibase`      | `postgres`, `mysql`, `mariadb`, `sqlserver`                           | `db/changelog/db.changelog-master.sql` (Liquibase formatted SQL)                |
| `migrate-mongo`  | `mongodb`                                                             | `Dockerfile.migrate` + `migrate-mongo-config.js` + `migrations/0001-init.js`    |
| `golang-migrate` | `postgres`, `mysql`, `mariadb`, `cockroachdb`, `sqlserver`, `mongodb` | `migrations/000001_init.up.sql` + `000001_init.down.sql` (or `.json` for mongo) |

`redis` and `elasticsearch` do not support migration scaffolding (no schema concept). `cockroachdb` + `liquibase` is rejected because the stock Liquibase image does not ship the CockroachDB driver extension.

### New service with migrations

```bash
localdevstack --service go --database postgres --migration flyway --output ./my-project --name my-api
cd my-project
docker-compose up --build -d          # service + db (migrate skipped — compose profile)
docker-compose run --rm migrate       # apply migrations on demand
```

### Existing service with migrations

```bash
localdevstack --existing-dir ./my-existing-api --database postgres --migration flyway
cd my-existing-api
docker-compose up --build -d          # service + db (migrate skipped)
docker-compose run --rm migrate       # apply migrations on demand
```

If `migrations/` (or `db/changelog/` for Liquibase, or `Dockerfile.migrate` for migrate-mongo) already exists in the target directory and contains files, the CLI aborts to protect your real migrations. Pass `--force` to overwrite.

### Adding more migrations

Drop new files into the appropriate directory using your tool's naming convention, then re-run:

```bash
docker-compose run --rm migrate
```

| Tool             | Add new migration                                                                     |
|------------------|---------------------------------------------------------------------------------------|
| `flyway`         | Create `migrations/V<N>__<name>.sql`                                                  |
| `liquibase`      | Append a new `--changeset` block to `db/changelog/db.changelog-master.sql`            |
| `migrate-mongo`  | `docker-compose run --rm migrate create <name>` then edit the generated file         |
| `golang-migrate` | `docker-compose run --rm migrate create -ext sql -dir /migrations -seq <name>`       |

### Restrictions

- The `migrate:` service is **not** auto-run on `docker-compose up`. Run it explicitly.
- Your service container does **not** depend on `migrate`. Run migrations before starting your service if your code expects the schema to exist.
- One migration tool per stack. Switching tools requires regenerating with `--force`.
- `--name migrate` and `--name db` are rejected when migrations are enabled (would collide with the `migrate:` / `db:` compose service names).

---

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

---

## ⚠️ Important disclaimers

**Database migrations are opt-in and run on demand.**
By default, LocalDevelopmentStack only creates the database container and injects the connection string — it does not create schemas or seed data. Pass `--migration <tool>` (see [Database migrations](#database-migrations-optional)) to scaffold an example migration and a `migrate:` compose service. Even when generated, the migrate service uses the `migrations` compose profile so it does **not** auto-start with `docker-compose up` — invoke it explicitly with `docker-compose run --rm migrate` when you want to apply migrations.

**`Dockerfile.dev` is for local development only.**
The generated `Dockerfile.dev` is optimised for developer convenience — hot-reload, full source access, development dependencies. It is not hardened for production. Review it thoroughly before using it in any shared or production environment. Never use `Dockerfile.dev` in a production pipeline.

**Your existing source code is never modified.**
When using `--existing-dir`, LocalDevelopmentStack only creates `Dockerfile.dev` and `docker-compose.yml`. It does not read, parse, or change any source files in your project.

**Database data is stored in named Docker volumes on your local machine.**
Data persists between `docker-compose up/down` restarts. Running `docker-compose down -v` permanently deletes all data in those volumes. This tool is for local development only — do not use it to manage production or shared databases.

**Default credentials are intentionally insecure.**
Generated files use well-known development defaults (e.g. `postgres/postgres`). Change these before connecting to any non-local environment.

**This software is proprietary.**
The binary is freely usable for development purposes. Redistribution, decompilation, and reverse engineering are prohibited.

---

## Support

[Open an issue](https://github.com/krishgok/localdevstack/issues) on the public distribution repository.

