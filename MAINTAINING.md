# Maintaining LocalDevelopmentStack

Internal maintainer documentation — release process, contribution patterns for adding new types, and the integration sweep used to validate end-to-end behavior before tagging a release.

---

## Repository model

The project uses two Git repositories. Both can be public under Apache 2.0; the split exists for **publication hygiene**, not source secrecy.

| Repo                              | Audience      | Contents                                                                                                            |
|-----------------------------------|---------------|---------------------------------------------------------------------------------------------------------------------|
| This repo (development)           | Maintainers   | Full Kotlin source, Gradle build, CI workflows, **`CLAUDE.md`**, **`MAINTAINING.md`** (this file), `gen-build-deploy-tests/`, the integration sweep artifacts |
| `krishgok/localdevstack` (mirror) | End users     | Same source + `LICENSE` + `README.md` + `Formula/` + `bucket/` + `scripts/`. Native binaries published as GitHub release assets. |

**What stays out of the public mirror:**

- `CLAUDE.md` — internal architecture / decision notes; useful for Claude Code agents and project maintainers, noisy for end users.
- `MAINTAINING.md` — this file. Release process and maintainer-only procedures.
- `gen-build-deploy-tests/` — the integration sweep script and its artifacts.
- Anything under `claude-test-stack*/`, `claude-test-results.tsv`, `claude-test-logs/`, `sweep*.out`, `smoke-test*/`, `smoke-v*/` (also in `.gitignore`).

**Mirror sync mechanism.** The release workflow (`.github/workflows/release.yml`) builds binaries from this repo on every version tag, publishes them as a GitHub release on the mirror, and updates the Homebrew / Scoop manifests. Source-code sync to the mirror is a separate step — see "Publishing source to the mirror" below.

**Why not just one public repo?** Two reasons. (1) `CLAUDE.md` is dense maintainer context that confuses first-time visitors who land on the README. (2) Keeping the integration-sweep artifacts (`claude-test-results.tsv`, the per-combo logs, the `gen-build-deploy-tests/` harness) out of the user-facing repo keeps that repo's commit log clean and its file tree short. Neither reason requires the development repo to be private — it can stay public if you want external contributions; it just shouldn't *be* the discoverable home of the project.

### Public mirror setup

Do these steps **once** before the first release. After setup, every release tag automatically publishes binaries to the mirror via CI (see "Tagging a release" below).

#### 1. Create the mirror repo

On GitHub, create `krishgok/localdevstack` with **public** visibility. Settings to apply right away:

- **Default branch**: `main`.
- **Description**: one-liner that matches the README opening sentence.
- **Topics**: `developer-tools`, `docker`, `dev-environment`, `cli`, `kotlin` (helps discoverability).
- **Wiki / Projects**: disabled — the mirror is read-only-ish, no need for collaboration surfaces.
- **Issues**: enabled — this is the canonical issue tracker users will see in `README.md`.
- **Pull requests**: enable but document in the README that external PRs against the mirror are not merged (contributions land in the development repo); add a CONTRIBUTING.md note when seeding the mirror.

#### 2. Generate a `DIST_TOKEN` PAT for CI

The release workflow on this repo pushes release assets and updates `Formula/*.rb` / `bucket/*.json` on the mirror. It needs a fine-grained PAT.

1. GitHub → your account → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. Resource owner: your account. Repository access: **Only select repositories** → `krishgok/localdevstack`.
3. Repository permissions: **Contents: Read and write**, **Metadata: Read-only**. Nothing else.
4. Expiration: 1 year (set a calendar reminder to rotate; the release workflow fails loudly when it lapses).
5. Copy the token (you only see it once).
6. In **this** repo → **Settings → Secrets and variables → Actions → New repository secret** → name `DIST_TOKEN`, paste the value.

#### 3. Seed the mirror with the first source sync

Use `git-filter-repo` on a scratch clone — never run `filter-repo` on your main working copy:

```bash
# Requires `pip install git-filter-repo` (or `brew install git-filter-repo`).

# 1. Make a scratch clone of this repo.
git clone --no-local . /tmp/ldstack-mirror-sync
cd /tmp/ldstack-mirror-sync

# 2. Strip maintainer-only paths from history.
git filter-repo --invert-paths \
  --path CLAUDE.md \
  --path MAINTAINING.md \
  --path .gitattributes \
  --path gen-build-deploy-tests/ \
  --path-glob 'claude-test-*' \
  --path-glob 'sweep*.out' \
  --path-glob 'smoke-*'

# 3. Verify the filtered tree looks right (no mirror-excluded files present).
git log --name-only --oneline | head -40
ls -la

# 4. Push to the mirror. `--force` is required for filter-repo output.
# The scratch clone's branch is `master` (inherited from the dev repo); the mirror's
# default branch is `main`, so use a refspec to push local master → remote main.
git remote add mirror https://github.com/krishgok/localdevstack.git
git push --force mirror master:main
git push --force mirror --tags
```

The filter list matches the `.gitattributes` `export-ignore` patterns at the repo root — keep the two in sync if you add new mirror-excluded paths.

#### 4. Seed the package-manager artifacts on the mirror

The release workflow updates `Formula/localdevstack.rb` and `bucket/localdevstack.json` in-place on each release, but the **initial** files have to exist. The filter-repo push from step 3 carries them across already — verify by checking that the mirror has `Formula/localdevstack.rb` and `bucket/localdevstack.json`. If they're missing, add a fresh commit on the mirror with placeholder versions (`v0.0.0`, all-zero sha256) and push.

#### 5. Verify the round-trip

Tag a no-op release (e.g. `v1.2.0-rc1`) and watch the release workflow run end-to-end.

**The workflow chain.** All automation lives in *this* repo — nothing runs inside the mirror. A tag push here triggers `.github/workflows/release.yml`, which (a) builds the GraalVM native binaries for all four platforms, (b) uses `DIST_TOKEN` to create the GitHub release on `krishgok/localdevstack` and upload the assets, and (c) clones the mirror, rewrites `Formula/localdevstack.rb` + `bucket/localdevstack.json`, and pushes that commit back to the mirror's `main`.

**A note on the tag trigger.** The `push` trigger pattern in `release.yml` is `v[0-9]+.[0-9]+.[0-9]+`, which only fires on strict `vMAJOR.MINOR.PATCH` tags — `-rc1` suffixes are deliberately excluded so prereleases don't ship to brew/scoop users. Use `workflow_dispatch` to fire the workflow against an rc tag:

```bash
# 1. Tag this repo's HEAD with an rc tag. (Does NOT auto-trigger the workflow
#    because of the strict version regex — that exclusion is intentional.)
git tag v1.2.0-rc1
git push origin v1.2.0-rc1
```

2. Then fire the release workflow against the tag from the GitHub UI:
   - This repo on github.com → **Actions** tab → **Release** workflow in the left sidebar.
   - Click the **Run workflow** dropdown on the right.
   - **Use workflow from**: pick **Tags → `v1.2.0-rc1`** (not a branch).
   - **Tag to build**: enter `v1.2.0-rc1`.
   - Click **Run workflow**. Refresh after a few seconds to see the run, then open it to follow the logs until it goes green (or red).

  (CLI equivalent if `gh` is ever available: `gh workflow run release.yml --ref v1.2.0-rc1 -f tag=v1.2.0-rc1 && gh run watch`.)

Verify in order:

1. **Actions → Release** on this repo turns green.
2. `krishgok/localdevstack/releases/tag/v1.2.0-rc1` exists with `.exe`, `.tar.gz`, `.zip` assets + matching `.sha256` files for Linux x64, macOS arm64, Windows x64. (Intel macOS was dropped when GitHub retired the `macos-13` runner — Intel users build from source.)
3. The latest commit on `krishgok/localdevstack/main` is from the release workflow and updates `Formula/localdevstack.rb` + `bucket/localdevstack.json` to the new version + the published binary hashes.
4. `brew install krishgok/localdevstack/localdevstack` and `scoop install localdevstack` resolve to the new version on a fresh machine.

When all four are green, tear down the rc artifacts in both repos before tagging the real release.

**On the mirror (`krishgok/localdevstack`) via github.com:**

1. **Releases** (right sidebar of the repo home) → click the `v1.2.0-rc1` release → trash-can icon → **Delete**.
2. After the release is deleted, the tag still exists. **Tags** tab → find `v1.2.0-rc1` → trash-can icon → **Delete tag**. (Or: **Code** dropdown → **Tags** → same row.)

**On this (dev) repo:**

```bash
# Delete the tag locally and from the dev repo's remote.
git tag -d v1.2.0-rc1
git push origin :refs/tags/v1.2.0-rc1
```

(CLI equivalent for the mirror cleanup if `gh` is ever available: `gh release delete v1.2.0-rc1 --repo krishgok/localdevstack --yes --cleanup-tag`.)

### Per-release source sync to the mirror

The CI release workflow only publishes binaries + updates the formula/manifest — it does **not** push source code to the mirror. Source sync is a manual step on each release (or as often as you want — the mirror's source view can lag behind the dev repo without affecting binary distribution).

```bash
# Run after pushing a release tag and the CI workflow turns green.
rm -rf /tmp/ldstack-mirror-sync
git clone --no-local . /tmp/ldstack-mirror-sync
cd /tmp/ldstack-mirror-sync
git filter-repo --invert-paths \
  --path CLAUDE.md \
  --path MAINTAINING.md \
  --path .gitattributes \
  --path gen-build-deploy-tests/ \
  --path-glob 'claude-test-*' \
  --path-glob 'sweep*.out' \
  --path-glob 'smoke-*'
git remote add mirror https://github.com/krishgok/localdevstack.git
git push --force mirror master:main    # local master → remote main (mirror's default branch)
git push mirror --tags    # no --force on tags; collisions mean someone retagged manually
```

`--force` rewrites the mirror's `main` history because filter-repo always produces a fresh commit graph. This is by design — the mirror is a derived view of this repo, not its own development branch. Users should never `git pull` from the mirror with the expectation of preserving local commits; the README's "contributions go to the dev repo" note exists for this reason.

### Verifying the mirror is in sync

After any sync, sanity-check from a clean directory:

```bash
git clone https://github.com/krishgok/localdevstack.git /tmp/ldstack-mirror-check
cd /tmp/ldstack-mirror-check

# Must NOT exist:
test ! -e CLAUDE.md         && echo "ok: no CLAUDE.md"
test ! -e MAINTAINING.md    && echo "ok: no MAINTAINING.md"
test ! -d gen-build-deploy-tests && echo "ok: no gen-build-deploy-tests"

# Must exist:
test -f LICENSE             && echo "ok: LICENSE present"
test -f README.md           && echo "ok: README.md present"
test -f Formula/localdevstack.rb && echo "ok: Homebrew formula present"
test -f bucket/localdevstack.json && echo "ok: Scoop manifest present"
```

### Recovering from mirror divergence

If a maintainer (or a contributor with mirror write access) commits directly to the mirror and the next filter-repo `--force` push would clobber that work:

1. **Don't push** until you understand what was committed. `git log mirror/main..HEAD` on the mirror clone shows mirror-only commits.
2. **Cherry-pick into this repo** if the change belongs in the canonical source. Then re-run the per-release sync — the change appears on the mirror via the normal flow.
3. **Discard mirror-only commits** only after verifying nothing important is lost. Force-push proceeds as normal.

This is rare in practice because the mirror's README directs contributions to the dev repo. The recovery procedure exists because the mirror is technically write-enabled to accept the CI workflow's formula/manifest updates.

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
2. Builds native binaries on Linux x64, macOS arm64, Windows x64 (Intel macOS dropped — see release.yml comment)
3. Runs smoke tests on the Linux binary only (macOS runners ship no Docker; Windows skips its smoke step too)
4. Publishes all binaries + `.sha256` files to the GitHub release for that tag
5. Updates `Formula/localdevstack.rb` (version, URLs, sha256 hashes) for Homebrew distribution
6. Updates `bucket/localdevstack.json` (version, URL, hash) for Scoop distribution
7. Commits and pushes those updates so package managers pick up the new version

After the workflow completes, `brew upgrade localdevstack` and `scoop update localdevstack` pick up the new version automatically.

---

## Triggering a release manually

If you need to re-run a release without pushing a new tag (e.g. a failed workflow):

1. GitHub → this repo → **Actions → Release → Run workflow**
2. Enter the tag to build (e.g. `v1.2.0`)

---

## CI invariants (don't drift from these)

Lessons learned during the CI bringup that aren't obvious from `release.yml` alone. Reverting any of these re-introduces a real failure that took an rc cycle to diagnose.

- **The Gradle wrapper must stay committed and intact.** Four files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`) plus three guards: `.gitignore`'s `!gradle/wrapper/gradle-wrapper.jar` negation (the `*.jar` rule above eats it otherwise — observed: "Could not find or load main class org.gradle.wrapper.GradleWrapperMain" on the runner), `.gitattributes`'s `gradlew text eol=lf` (Windows `core.autocrlf=true` otherwise rewrites the shebang and Linux runners report "exec format error"), and the executable bit in the git index (`git ls-files --stage gradlew` must show mode `100755`; if it's `100644`, the runner reports "Permission denied"). When committing wrapper changes from Windows, run `git update-index --chmod=+x gradlew` before the commit.

- **Foojay resolver in `settings.gradle.kts` is load-bearing.** `build.gradle.kts:39` requests `jvmToolchain(17)`, but the Windows CI job only installs GraalVM 21 via `setup-graalvm@v1`. Without `org.gradle.toolchains.foojay-resolver-convention`, Gradle fails with "No locally installed toolchains match … toolchain download repositories have not been configured." The Linux/macOS runners pass by accident because they ship JDK 17 preinstalled. Don't remove the plugin.

- **`release.yml`'s tag trigger excludes rc tags by design.** `on.push.tags: 'v[0-9]+.[0-9]+.[0-9]+'` is an anchored glob — `v1.2.0-rc1` does NOT match. Rc testing uses `workflow_dispatch` (see step 5 of "Verify the round-trip"). The strict pattern is intentional: it stops prerelease tags from shipping to brew/scoop users. Don't relax it to `v[0-9]+.[0-9]+.[0-9]+*` without also rethinking the formula/manifest update path.

- **The version-match guard is gated on `github.event_name == 'push'`.** Real tag pushes get the safety check (catches "tagged v1.2.0 but `build.gradle.kts` still says 1.1.0"); rc `workflow_dispatch` runs skip it because the synthetic tag intentionally won't match. Don't drop the `if:`.

- **No smoke step on macOS or Windows; smoke runs Linux-only.** macOS GitHub runners don't ship Docker and the CLI's Docker availability probe exits before any generator runs. Windows runners do ship Docker but historically had startup-time flakes that doubled the wall-clock of the release. The Linux job's smoke step exercises the same Kotlin → native binary; macOS/Windows coverage is "nativeCompile succeeds." Don't add a smoke step to either platform without also providing Docker.

- **Windows native-image needs `ilammy/msvc-dev-cmd@v1` as a separate step** before `./gradlew nativeCompile`. Without it (or with only `setup-graalvm`'s `native-image-msvc: 'true'` — observed to fail on `windows-latest` in May 2026), `native-image.cmd` exits with code 20 and "Failed to find 'vcvarsall.bat' in a Visual Studio installation" even though VS Build Tools are installed on the runner. The `ilammy/msvc-dev-cmd` action invokes `vcvarsall.bat amd64` directly and exports the MSVC env vars to subsequent steps; that's the reliable path.

- **Intel macOS (`build-macos-x64`) was dropped, not just disabled.** GitHub retired the `macos-13` runner image in late 2025; no free Intel macOS runner replaced it (`macos-14-large` etc. exist but are paid). The Homebrew formula's `on_intel do` block was also removed (`Formula/localdevstack.rb`), as were the x64 sha256/url `sed` lines in the publish step. If you ever re-add Intel macOS support, all four sites must change together: a build job, the `publish` job's `needs:` list, the `sed` rules, and the formula.

- **Pushing the wrapper from a fresh `gradle wrapper` may fail SSL verification locally.** The wrapper's first-use download from `services.gradle.org` can hit `PKIX path building failed` on machines with stale JDK truststores — this is a local issue, not a wrapper-generation issue. The wrapper files are correct; CI runners have current CA bundles and will fetch fine.

---

## (Optional) Test native compilation locally

Verify `nativeCompile` works on your machine before tagging:

```bash
# Install GraalVM 21 via SDKMAN (macOS/Linux)
sdk install java 21-graalce

# Windows — download from https://www.graalvm.org/downloads/ and set JAVA_HOME

./gradlew nativeCompile
./build/native/nativeCompile/localdevstack --version
```

---

## Repository structure reference

Files marked **[mirror-excluded]** stay out of the public mirror repo (see "Repository model" above).

```
LocalDevelopmentStack/
├── .github/workflows/
│   └── release.yml             ← 4-platform CI: tests, native binaries, GitHub release
├── src/
│   └── main/kotlin/com/localdevstack/
│       ├── LocalDevStackCli.kt           ← picocli CLI: two modes, --migration, --with, --dry-run
│       ├── detector/
│       │   └── ExistingServiceDetector.kt
│       └── generator/
│           ├── *ServiceGenerator.kt      ← 9 implementations
│           ├── *DatabaseGenerator.kt     ← 8 implementations
│           ├── *DockerfileGenerator.kt   ← 9 implementations (hot-reload)
│           ├── *MigrationGenerator.kt    ← 4 implementations + interface
│           ├── *CompanionGenerator.kt    ← 2 implementations + interface
│           ├── MigrationComposeAppender.kt
│           ├── CompanionComposeAppender.kt
│           ├── EnvFileGenerator.kt
│           ├── GitignoreGenerator.kt
│           └── ServiceComposeConfig.kt
├── gen-build-deploy-tests/     ← [mirror-excluded] integration sweep harness
│   └── claude-test-sweep.sh
├── Formula/
│   └── localdevstack.rb        ← Homebrew formula (CI updates per release)
├── bucket/
│   └── localdevstack.json      ← Scoop manifest (CI updates per release)
├── scripts/
│   ├── install.sh              ← curl installer
│   └── install.ps1             ← PowerShell installer
├── README.md                   ← end-user documentation
├── MAINTAINING.md              ← [mirror-excluded] this file
├── CLAUDE.md                   ← [mirror-excluded] Claude Code guidance
├── LICENSE                     ← Apache 2.0
├── .gitattributes              ← marks mirror-excluded paths with export-ignore
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

1. Implement `DatabaseGenerator`. The compose YAML **must** end with `\nvolumes:\n  <name>_data:` so `appendMigrateBlockToCompose` and `appendCompanionBlocksToCompose` can splice their additions. `MigrationComposeAppenderTest` and `CompanionComposeAppenderTest` enforce this.
2. Add **one entry** to the `DATABASES` map in the companion object: `"<type>" to DbSpec(::YourDatabaseGenerator, mapOf("<ENV_KEY>" to "<url>"), { DbConnectionInfo(it, jdbcUrl = "...", user = "...", password = "...") })`. JDBC URL, env var, credentials all in one place.
3. Add the database to `SUPPORTED_MIGRATIONS` (in the same companion object) with the compatible migration tools, or an empty list for "no migration support". The supported-databases error string is auto-derived.
4. Update the supported-types tables in `README.md` and `CLAUDE.md`.

## Adding a new companion type

1. Implement `CompanionGenerator` (interface in `generator/CompanionGenerator.kt`) — `companionName` (lowercase identifier, also the compose service name and `--with` token), `composeServiceBlock()` (YAML snippet starting with `  <name>:`, ending in newline). Optional: `envOverlay()`, `namedVolumes()`.
2. Add **one entry** to the `COMPANIONS` map in `LocalDevStackCli`'s companion object: `"<name>" to CompanionSpec(::YourCompanionGenerator)`. The supported-list error and `--name` collision check are auto-derived.
3. Add rows to `AllCompanionGeneratorsTest.companions()` plus a CLI-level test in `LocalDevStackCliTest` (mirror the existing `--with mailhog` / `--with minio` tests).
4. Score the candidate against the five companion criteria before merging — universal need across personas, zero-config single container, drop-in for a real cloud service, visible UI, mature stable image. If criterion #2 fails, it does not belong behind `--with`.
5. Add the companion to the integration sweep — at minimum, one combo of `--service <any> --database <any> --with <new-companion>` that boots `/health` 200 plus the companion's own health endpoint.
6. Update the companion table in `README.md` and `CLAUDE.md`.

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

`gen-build-deploy-tests/claude-test-sweep.sh` iterates every `(--service, --database)` pair (9 × 8 = 72 combos), generates each into `claude-test-stack-v*/`, runs `docker compose up -d --build`, polls `http://localhost:8080/health` until a 200, records the result to `claude-test-results.tsv`, then tears down. It is **not** wired into CI — Docker images alone are ~10 GB and a cold sweep is ~2 hours — but run it locally before merging anything that touches a `*ServiceGenerator`, `*DockerfileGenerator`, `*DatabaseGenerator`, `*CompanionGenerator`, or the `appendServiceBlock` healthcheck/env-interpolation contract.

### Extended sweep (1.2.0+)

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

### Companion sweep (`claude-test-sweep-companions.sh`)

Sibling to the core sweep, focused on the `--with` flag. Runs 9 services × `--with mailhog` (postgres backend) + 9 × `--with minio` + 1 omnibus (`springboot+postgres+mailhog,minio+flyway`). Each combo probes `/health` AND the companion's own endpoint (`http://localhost:8025/` for mailhog, `http://localhost:9000/minio/health/live` for minio). Append-only results land in `claude-test-results-companions.tsv`. Run after the core sweep, when the layer caches are warm:

```bash
gradle shadowJar && bash gen-build-deploy-tests/claude-test-sweep-companions.sh
```

The script is resume-safe (skips combos already in the TSV). To rerun a specific combo, delete its row first.

### Known transient flakes

These have failed once in a sweep, passed cleanly on retry, and the failure is not reproducible in adjacent runs. When you hit one, **retry it first** before debugging — don't sink time into chasing a flake that the next run will pass.

- **`rust + mailhog` binary-exits-0-at-startup.** Observed once (2026-05-20, 909s timeout in the 1.2.0 companion sweep). Symptoms: cargo-watch prints `Finished dev … in 0.17s` then `Running target/debug/<bin>` then `[Finished running. Exit status: 0]`, with no `println!` output and no panic message. The container stays `Up (unhealthy)` until the deadline. `rust + minio` ran immediately after with identical Dockerfile / template / anonymous volume state and passed in 51s; `rust + mailhog` retried clean at 57s. Hypothesis: Docker Desktop on Windows occasionally serves a stale `target/debug/<bin>` from the warmup `cargo build` layer when bind-mount mtimes race with cargo's freshness check. If this recurs *reproducibly* in a sweep, fix candidates live in `RustDockerfileGenerator.kt`: (a) add `cargo clean -p <crate>` before `cargo watch`, (b) replace `cargo build` warmup with `cargo fetch` (deps only, no binary), or (c) drop the warmup and accept slower cold start.

### Last full-sweep status

Update this table whenever you run a full sweep (drop the row, add a new one). It's the durable answer to "does end-to-end still work?" — the per-combo logs themselves aren't worth committing.

| Date       | JAR version | Result      | Notes                                                            |
|------------|-------------|-------------|------------------------------------------------------------------|
| 2026-05-14 | 1.1.0       | 72/72 PASS  | 9 services × 8 databases, all `/health` 200. Host: Windows 11.   |
| 2026-05-20 | 1.2.0       | 91/91 PASS  | Core 72/72 (9 services × 8 databases, all `/health` 200) + Extended 19/19 (9 services × `--with mailhog` + 9 × `--with minio` + 1 omnibus `springboot+postgres+mailhog,minio+flyway`). Validates: the new healthcheck stanza, `${VAR}` env-interpolation, `CompanionGenerator` post-processing for both companions, and the omnibus four-post-processor stack. `rust+mailhog` failed once at 909s (binary exited with stub-binary-like signature) but passed cleanly on retry at 57s; not reproducible, treated as transient Docker Desktop / Cargo freshness flake — adjacent rust_minio and core rust runs all passed. Host: Windows 11. Per-tier timing: cold tier mean ~135s (springboot heaviest at 319s cold); warm tiers 24–60s. |
