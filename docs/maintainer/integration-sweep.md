# Integration sweep (`claude-test-sweep.sh`)

Unit tests cover generator output structure; they do not verify that a generated stack actually runs. The integration sweep covers that gap.

`gen-build-deploy-tests/claude-test-sweep.sh` iterates every `(--service, --database)` pair (9 × 8 = 72 combos), generates each into `claude-test-stack-v*/`, runs `docker compose up -d --build`, polls `http://localhost:8080/health` until a 200, records the result to `claude-test-results.tsv`, then tears down. It is **not** wired into CI — Docker images alone are ~10 GB and a cold sweep is ~2 hours — but run it locally before merging anything that touches a `*ServiceGenerator`, `*DockerfileGenerator`, `*DatabaseGenerator`, `*CompanionGenerator`, or the `appendServiceBlock` healthcheck / env-interpolation contract.

---

## Extended sweep (1.2.0+)

The 72-combo grid is the core. Append the following targeted combos when a sweep is required:

| Category | Combos | What to verify |
|----------|--------|----------------|
| Companion: mailhog | 9 (one per service × `postgres`) | `docker compose up` boots clean; `curl localhost:8025` returns the MailHog UI HTML; service container reads `SMTP_HOST=mailhog` from `.env` |
| Companion: minio   | 9 (one per service × `postgres`) | `curl localhost:9000/minio/health/live` returns 200; console reachable on `:9001`; `minio_data` volume created |
| Combined           | 1 (`--with mailhog,minio --migration flyway --service springboot --database postgres`) | All four post-processors run; service+db+migrate+mailhog+minio all listed in `docker compose ps`; `/health` returns 200 |
| `.env` round-trip  | 8 (one per database, default service) | `docker compose --env-file .env config` returns zero unresolved variables; the resolved compose is functionally equivalent to pre-`.env` output |
| `--dry-run`        | 9 services × 8 databases = 72 | Each combo: `--dry-run` exits 0, no files in `tempDir`, plan summary printed |

Extended total = 72 (core) + 9 (mailhog) + 9 (minio) + 1 (omnibus) + 8 (env) + 72 (dry-run, fast — no docker) = **171 combos**. The 72 dry-run combos run in seconds; only the first 99 are Docker-bound.

**Prerequisites:** Docker Desktop running, and a fresh fat JAR at `build/libs/LocalDevelopmentStack-<version>.jar` matching the path in the script.

```bash
# Full 72-combo sweep (rebuild JAR first to pick up any generator changes)
gradle shadowJar && bash gen-build-deploy-tests/claude-test-sweep.sh

# Scoped re-runs (resume logic skips combos already in claude-test-results.tsv)
SVC_FILTER=ruby bash gen-build-deploy-tests/claude-test-sweep.sh                    # one service, all 8 DBs
SVC_FILTER=ruby DB_FILTER=postgres bash gen-build-deploy-tests/claude-test-sweep.sh # single combo

# Force a full re-sweep
rm claude-test-results.tsv && bash gen-build-deploy-tests/claude-test-sweep.sh
```

The sweep is long-running (cold first run ~2 hours; warm re-runs ~30 min). Detach it from your shell if you need the terminal back: `nohup bash gen-build-deploy-tests/claude-test-sweep.sh > sweep.out 2>&1 &` then `tail -F claude-test-results.tsv` to follow progress.

Resume logic: `claude-test-results.tsv` is append-only and the script skips any combo already recorded. To re-run a failing combo, delete its row first. To force a full re-sweep, delete the file (the header is regenerated). The append behaviour is how `SVC_FILTER` / `DB_FILTER` reruns add to the same results table without clobbering prior passes.

Per-combo logs land in `claude-test-logs/{svc}_{db}-up.log` (compose build + container logs). The harness keeps these around after pass/fail; nuke the directory when you're done.

`STACK_DIR` is bumped (v2 → v3 → ...) whenever Docker Desktop locks the folder mid-sweep and won't release it; the simplest workaround is to use a fresh folder name rather than fight the lock. The artifacts (`claude-test-stack-v*/`, `claude-test-results.tsv`, `claude-test-logs/`, `sweep*.out`) are gitignored intentionally.

Per-combo budget is `TIMEOUT=900` seconds. The first build of a service tier (cold cargo / cold Maven / cold composer) eats most of that; subsequent combos in the same tier reuse Docker layer cache and finish in ~30-60s.

---

## Companion sweep (`claude-test-sweep-companions.sh`)

Sibling to the core sweep, focused on the `--with` flag. Runs 9 services × `--with mailhog` (postgres backend) + 9 × `--with minio` + 1 omnibus (`springboot+postgres+mailhog,minio+flyway`). Each combo probes `/health` AND the companion's own endpoint (`http://localhost:8025/` for mailhog, `http://localhost:9000/minio/health/live` for minio). Append-only results land in `claude-test-results-companions.tsv`. Run after the core sweep, when the layer caches are warm:

```bash
gradle shadowJar && bash gen-build-deploy-tests/claude-test-sweep-companions.sh
```

The script is resume-safe (skips combos already in the TSV). To rerun a specific combo, delete its row first.

---

## Existing-service sweep (`claude-test-sweep-existing.sh`)

Sibling to the core sweep, focused on **existing-service mode** (`--existing-dir`). The core sweep only exercises new-service mode — it generates source from templates and asserts the resulting stack boots. Existing-service mode has its own surface that the core sweep doesn't reach: language detection via sentinel files (`ExistingServiceDetector`), per-language `DockerfileGenerator` subclasses run against arbitrary user source, and the Rails branch of `RubyDockerfileGenerator` (which keys on `bin/rails` / `config/application.rb` and emits `bundle exec rails server` rather than the new-service template's Sinatra default).

Two strategies, both writing to `claude-test-results-existing.tsv` (append-only, resume-safe):

- **Strategy A — hand-written fixtures** under `gen-build-deploy-tests/existing-fixtures/<lang>/`. 10 fixtures (9 languages + a `ruby-rails` variant), each a minimal source tree with the sentinel file(s) the detector needs plus a `/health` endpoint on `:8080`. The sweep copies the fixture to a working dir, runs `--existing-dir`, brings the stack up against postgres, polls `/health`. Tests "user's existing code + LDS-emitted Dockerfile.dev" — the path nothing else covers.
- **Strategy B — new→existing round-trip**. For each of the 9 new-service languages, generate as new-service, strip the LDS-emitted `Dockerfile.dev`, then re-run with `--existing-dir` against the generated source. A cross-mode invariant check: does each language's existing-service Dockerfile boot the same source its new-service template just produced?

Diagonal coverage on the DB axis — postgres only. DB-side compose plumbing is already exhaustively tested by the core 72-combo sweep; duplicating it across both modes would add ~2 hrs without coverage. Run after the core sweep when layer caches are warm:

```bash
gradle shadowJar && bash gen-build-deploy-tests/claude-test-sweep-existing.sh
```

Filtering — useful for re-running a failing fixture or skipping one phase:

```bash
FIXTURE_FILTER=ruby-rails bash gen-build-deploy-tests/claude-test-sweep-existing.sh        # one fixture, both phases
FIXTURE_FILTER=none ROUNDTRIP_FILTER=go,python bash ...                                    # skip Strategy A entirely
ROUNDTRIP_FILTER=none bash ...                                                             # skip Strategy B entirely
```

Per-fixture logs at `claude-test-logs-existing/<mode>_<name>-{gen,up}.log`. Both the TSV and logs are gitignored. Cold first run is ~15–25 min (Rails fixture is the long pole due to a heavyweight `bundle install`); warm reruns ~5 min.

**Rails-branch verification**: after a green run, grep `claude-test-logs-existing/fixture_ruby-rails-up.log` for `rails server` (should appear) and confirm `fixture_ruby-up.log` shows `ruby app.rb` (should appear). That cross-check is the specific regression the Rails fixture exists to prevent.

---

## Known transient flakes

These have failed once in a sweep, passed cleanly on retry, and the failure is not reproducible in adjacent runs. When you hit one, **retry it first** before debugging — don't sink time into chasing a flake that the next run will pass.

- **`rust + mailhog` binary-exits-0-at-startup.** Observed once (2026-05-20, 909s timeout in the 1.2.0 companion sweep). Symptoms: cargo-watch prints `Finished dev … in 0.17s` then `Running target/debug/<bin>` then `[Finished running. Exit status: 0]`, with no `println!` output and no panic message. The container stays `Up (unhealthy)` until the deadline. `rust + minio` ran immediately after with identical Dockerfile / template / anonymous volume state and passed in 51s; `rust + mailhog` retried clean at 57s. Hypothesis: Docker Desktop on Windows occasionally serves a stale `target/debug/<bin>` from the warmup `cargo build` layer when bind-mount mtimes race with cargo's freshness check. If this recurs *reproducibly* in a sweep, fix candidates live in `RustDockerfileGenerator.kt`: (a) add `cargo clean -p <crate>` before `cargo watch`, (b) replace `cargo build` warmup with `cargo fetch` (deps only, no binary), or (c) drop the warmup and accept slower cold start. **Recurred 2026-05-31 in the existing-service sweep on `fixture/rust`** (std-only TcpListener — zero crates.io dependencies, so cargo's freshness check has even less reason to invalidate the warmup cache, making this fixture more susceptible to the same race). Retried clean at 21s.

---

## Last full-sweep status

Update this table whenever you run a full sweep (drop the row, add a new one). It's the durable answer to "does end-to-end still work?" — the per-combo logs themselves aren't worth committing.

| Date       | JAR version | Result      | Notes                                                            |
|------------|-------------|-------------|------------------------------------------------------------------|
| 2026-05-14 | 1.1.0       | 72/72 PASS  | 9 services × 8 databases, all `/health` 200. Host: Windows 11.   |
| 2026-05-20 | 1.2.0       | 91/91 PASS  | Core 72/72 (9 services × 8 databases, all `/health` 200) + Extended 19/19 (9 services × `--with mailhog` + 9 × `--with minio` + 1 omnibus `springboot+postgres+mailhog,minio+flyway`). Validates: the new healthcheck stanza, `${VAR}` env-interpolation, `CompanionGenerator` post-processing for both companions, and the omnibus four-post-processor stack. `rust+mailhog` failed once at 909s (binary exited with stub-binary-like signature) but passed cleanly on retry at 57s; not reproducible, treated as transient Docker Desktop / Cargo freshness flake — adjacent rust_minio and core rust runs all passed. Host: Windows 11. Per-tier timing: cold tier mean ~135s (springboot heaviest at 319s cold); warm tiers 24–60s. |
| 2026-05-31 | 1.2.0       | 19/19 PASS  | First existing-service sweep. 10 hand-written fixtures (Strategy A) + 9 new→existing round-trips (Strategy B), all against postgres. **Real bug surfaced and fixed in the same run:** `fixture/dotnet` and `roundtrip/dotnet` both failed at the generate step — `ExistingServiceDetector` was missing `Program.cs` / `*.csproj` sentinels (documented in CLAUDE.md but never implemented). Fix landed in `ExistingServiceDetector.kt` with parameterized + standalone test coverage; retries passed (fixture 91s, roundtrip 44s). `fixture/rust` hit the documented binary-exits-0-at-startup flake (902s timeout); retried clean at 21s — see the flake bullet above. **Rails branch verified end-to-end** — `fixture/ruby-rails` boots a minimal Rails 7.1 api_only app, lambda `/health` route returns 200 at 72s. Host: Windows 11. |
