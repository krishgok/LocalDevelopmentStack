# Usage: Scaffold a new service

This is "new service" mode — generates source code + `Dockerfile.dev` + `docker-compose.yml` from scratch. Hot-reload is enabled: edit source files and the watcher inside the container recompiles/restarts your service automatically.

> Looking for "wrap an existing service" instead? See [usage-existing-service.md](usage-existing-service.md).

## Step 1 — Generate the stack

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

## Step 2 — Start the full local stack

```bash
cd my-project
docker-compose up --build
```

Builds the dev image on first run and starts both the database and your service. Subsequent `docker-compose up` calls skip the build.

## Step 3 — Verify

```bash
curl http://localhost:8080/health
# → {"status":"ok"}
```

## Step 4 — Edit, save, and see changes reload automatically

Hot-reload works the same way as in [existing-service mode](usage-existing-service.md#step-4--edit-save-and-see-changes-reload-automatically) — edit a source file under `service/`, the watcher inside the container picks it up. Only re-run `docker-compose up --build` if you change `Dockerfile.dev` itself or add/remove dependencies (e.g. `go.mod`, `package.json`, `requirements.txt`).
