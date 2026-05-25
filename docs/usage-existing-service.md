# Usage: Wrap an existing service

Points the tool at your existing code. Generates `Dockerfile.dev` + `docker-compose.yml` so everything runs inside Docker with **hot-reload enabled**.

> Looking for "scaffold a new service" instead? See [usage-new-service.md](usage-new-service.md).

## Step 1 — Run from or above your service directory

```bash
localdevstack --existing-dir ./my-existing-api --database postgres
```

The tool auto-detects the language from your project files. Pass `--service` to override:

```bash
localdevstack --existing-dir ./my-existing-api --service node --database postgres
```

## Step 2 — Review the generated files

```
my-existing-api/
├── <your source files — untouched>
├── Dockerfile.dev      ← dev container for your service (hot-reload enabled)
└── docker-compose.yml  ← your service + the database, fully wired
```

`docker-compose.yml` injects the database connection string as an environment variable (e.g. `DATABASE_URL`) so your service connects to the local database automatically.

## Step 3 — Start the full local stack

```bash
cd my-existing-api
docker-compose up --build
```

Builds the dev image on first run. Subsequent `docker-compose up` calls skip the build.

## Step 4 — Edit, save, and see changes reload automatically

Hot-reload is enabled — there is no need to run `docker-compose up --build` again after editing source files. The watcher inside the container picks up changes and recompiles/restarts your service automatically.

| Language   | Hot-reload tool                                    |
|------------|----------------------------------------------------|
| Go         | [`air`](https://github.com/air-verse/air) (file watcher + incremental rebuild) |
| Node.js    | [`nodemon`](https://nodemon.io)                    |
| Python     | `uvicorn --reload`                                 |
| Ruby       | Rails dev server (auto-reloads) if `bin/rails` or `config/application.rb` exists; otherwise `bundle exec ruby app.rb` (Sinatra) |
| PHP        | PHP built-in server (serves files on request)      |
| Spring Boot| `./gradlew bootRun`                                |
| Java       | `mvn spring-boot:run`                              |
| .NET       | `dotnet watch run`                                 |
| Rust       | [`cargo-watch`](https://crates.io/crates/cargo-watch) |

> **When to rebuild:** Run `docker-compose up --build` again only if you change `Dockerfile.dev` itself or add/remove dependencies (e.g. change `go.mod`, `package.json`, `requirements.txt`).

## Step 5 — Verify your service

```bash
curl http://localhost:8080/health   # or your own endpoint
```

LocalDevelopmentStack does not add or modify any endpoints in your service.
