# Release process

Tagging a release, triggering the workflow manually, and the CI invariants you must not let drift.

The mirror setup that this depends on lives in [mirror-sync.md](mirror-sync.md).

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

1. Runs all tests.
2. Builds native binaries on Linux x64, macOS arm64, Windows x64 (Intel macOS dropped — see `release.yml` comment and [CI invariants](#ci-invariants-dont-drift-from-these) below).
3. Runs smoke tests on the Linux binary only (macOS runners ship no Docker; Windows skips its smoke step too).
4. Publishes all binaries + `.sha256` files to the GitHub release for that tag.
5. Updates `Formula/localdevstack.rb` (version, URLs, sha256 hashes) for Homebrew distribution.
6. Updates `bucket/localdevstack.json` (version, URL, hash) for Scoop distribution.
7. Commits and pushes those updates so package managers pick up the new version.

After the workflow completes, `brew upgrade localdevstack` and `scoop update localdevstack` pick up the new version automatically.

---

## Triggering a release manually

If you need to re-run a release without pushing a new tag (e.g. a failed workflow), or to fire the workflow against an rc tag that the strict push trigger ignores:

1. GitHub → dev repo → **Actions → Release → Run workflow**.
2. Enter the tag to build (e.g. `v1.2.0` or `v1.2.0-rc1`).

For the full rc round-trip flow, see [mirror-sync.md → Step 5](mirror-sync.md#5-verify-the-round-trip).

---

## CI invariants (don't drift from these)

Lessons learned during the CI bringup that aren't obvious from `release.yml` alone. Reverting any of these re-introduces a real failure that took an rc cycle to diagnose.

- **The Gradle wrapper must stay committed and intact.** Four files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`) plus three guards: `.gitignore`'s `!gradle/wrapper/gradle-wrapper.jar` negation (the `*.jar` rule above eats it otherwise — observed: "Could not find or load main class org.gradle.wrapper.GradleWrapperMain" on the runner), `.gitattributes`'s `gradlew text eol=lf` (Windows `core.autocrlf=true` otherwise rewrites the shebang and Linux runners report "exec format error"), and the executable bit in the git index (`git ls-files --stage gradlew` must show mode `100755`; if it's `100644`, the runner reports "Permission denied"). When committing wrapper changes from Windows, run `git update-index --chmod=+x gradlew` before the commit.

- **Foojay resolver in `settings.gradle.kts` is load-bearing.** `build.gradle.kts:39` requests `jvmToolchain(17)`, but the Windows CI job only installs GraalVM 21 via `setup-graalvm@v1`. Without `org.gradle.toolchains.foojay-resolver-convention`, Gradle fails with "No locally installed toolchains match … toolchain download repositories have not been configured." The Linux/macOS runners pass by accident because they ship JDK 17 preinstalled. Don't remove the plugin.

- **`release.yml`'s tag trigger excludes rc tags by design.** `on.push.tags: 'v[0-9]+.[0-9]+.[0-9]+'` is an anchored glob — `v1.2.0-rc1` does NOT match. Rc testing uses `workflow_dispatch` (see [mirror-sync.md → Step 5](mirror-sync.md#5-verify-the-round-trip)). The strict pattern is intentional: it stops prerelease tags from shipping to brew/scoop users. Don't relax it to `v[0-9]+.[0-9]+.[0-9]+*` without also rethinking the formula/manifest update path.

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
