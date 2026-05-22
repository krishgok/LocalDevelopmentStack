# Maintaining LocalDevelopmentStack

Internal maintainer documentation. The detailed procedures live in `docs/maintainer/`; this file is the navigable index.

For external contributors, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Repository model

The project uses two Git repositories. Both are public under Apache 2.0; the split exists for **publication hygiene**, not source secrecy.

| Repo                              | Audience                   | Contents                                                                                                                                  |
|-----------------------------------|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| This repo (development)           | Maintainers + contributors | Full Kotlin source, Gradle build, CI workflows, `CLAUDE.md`, `MAINTAINING.md` (this file), `docs/maintainer/`, `gen-build-deploy-tests/`. |
| `krishgok/localdevstack` (mirror) | End users                  | Same source + `LICENSE`, `README.md`, `CONTRIBUTING.md`, `docs/db-connections.md`, `Formula/`, `bucket/`, `scripts/`. Native binaries published as GitHub release assets. |

**What stays out of the public mirror:**

- `CLAUDE.md` — internal architecture / decision notes; useful for Claude Code agents and project maintainers, noisy for end users.
- `MAINTAINING.md` — this file.
- `docs/maintainer/` — the detailed maintainer docs linked below.
- `gen-build-deploy-tests/` — the integration sweep script and its artifacts.
- Anything under `claude-test-stack*/`, `claude-test-results.tsv`, `claude-test-logs/`, `sweep*.out`, `smoke-test*/`, `smoke-v*/` (also in `.gitignore`).

The mirror-excluded paths are enforced two ways: `.gitattributes` marks them `export-ignore` (for `git archive`), and the per-release source sync uses `git filter-repo --invert-paths` with the same list. Keep the two in sync when you add a new excluded path.

**Mirror sync mechanism.** The release workflow (`.github/workflows/release.yml`) builds binaries from this repo on every version tag, publishes them as a GitHub release on the mirror, and updates the Homebrew / Scoop manifests. Source-code sync to the mirror is a separate manual step — see [docs/maintainer/mirror-sync.md](docs/maintainer/mirror-sync.md).

**Why not just one public repo?** Two reasons. (1) `CLAUDE.md` is dense maintainer context that confuses first-time visitors who land on the README. (2) Keeping the integration-sweep artifacts (`claude-test-results.tsv`, the per-combo logs, the `gen-build-deploy-tests/` harness) out of the user-facing repo keeps that repo's commit log clean and its file tree short. Neither reason requires the development repo to be private — keeping it public is what enables the standard fork+PR contribution flow described in [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Maintainer documentation map

| Topic                          | File                                                              | Covers                                                                                              |
|--------------------------------|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Mirror setup, sync, recovery   | [docs/maintainer/mirror-sync.md](docs/maintainer/mirror-sync.md)  | One-time setup (mirror repo, `DIST_TOKEN` PAT), per-release source sync via `git-filter-repo`, integrity check, divergence recovery. |
| Release process                | [docs/maintainer/release-process.md](docs/maintainer/release-process.md) | Tagging a release, triggering the workflow manually, **CI invariants** ("don't drift from these"), local native-compile check.  |
| Extending the tool             | [docs/maintainer/extending.md](docs/maintainer/extending.md)      | Adding a new service / database / companion type or migration tool — checklists + Dockerfile gotchas surfaced by the sweep. |
| Integration sweep              | [docs/maintainer/integration-sweep.md](docs/maintainer/integration-sweep.md) | 72-combo core sweep + extended sweep + companion sweep, known transient flakes, last full-sweep status. |

---

## Repository structure reference

Files marked **[mirror-excluded]** stay out of the public mirror repo (see [Repository model](#repository-model) above).

```
LocalDevelopmentStack/
├── .github/workflows/
│   └── release.yml             ← 3-platform CI: tests, native binaries, GitHub release
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
├── docs/
│   ├── db-connections.md       ← per-language env-var examples (linked from README)
│   └── maintainer/             ← [mirror-excluded] maintainer-only deep dives
│       ├── mirror-sync.md
│       ├── release-process.md
│       ├── extending.md
│       └── integration-sweep.md
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
├── CONTRIBUTING.md             ← contribution guide (popular GitHub flows)
├── MAINTAINING.md              ← [mirror-excluded] this file
├── CLAUDE.md                   ← [mirror-excluded] Claude Code guidance
├── LICENSE                     ← Apache 2.0
├── .gitattributes              ← marks mirror-excluded paths with export-ignore
└── build.gradle.kts            ← Gradle + GraalVM native image config
```
