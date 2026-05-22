# Mirror setup, sync, and recovery

How the public mirror (`krishgok/localdevstack`) is initialised, kept in sync with this dev repo (`krishgok/LocalDevelopmentStack`), verified, and recovered if it diverges. Background on *why* there's a split lives in [MAINTAINING.md → Repository model](../../MAINTAINING.md#repository-model).

---

## Public mirror setup (one-time)

Do these steps once before the first release. After setup, every release tag automatically publishes binaries to the mirror via CI (see [release-process.md](release-process.md)).

### 1. Create the mirror repo

On GitHub, create `krishgok/localdevstack` with **public** visibility. Settings to apply right away:

- **Default branch**: `main`.
- **Description**: one-liner that matches the README opening sentence.
- **Topics**: `developer-tools`, `docker`, `dev-environment`, `cli`, `kotlin` (helps discoverability).
- **Wiki / Projects**: disabled — the mirror is read-only-ish, no need for collaboration surfaces.
- **Issues**: enabled — this is the canonical issue tracker users see in `README.md`.
- **Pull requests**: enable but document in the README that external PRs against the mirror are not merged (contributions land in the development repo); the project's [CONTRIBUTING.md](../../CONTRIBUTING.md) makes this explicit.

### 2. Generate a `DIST_TOKEN` PAT for CI

The release workflow on the dev repo pushes release assets and updates `Formula/*.rb` / `bucket/*.json` on the mirror. It needs a fine-grained PAT.

1. GitHub → your account → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. Resource owner: your account. Repository access: **Only select repositories** → `krishgok/localdevstack`.
3. Repository permissions: **Contents: Read and write**, **Metadata: Read-only**. Nothing else.
4. Expiration: 1 year (set a calendar reminder to rotate; the release workflow fails loudly when it lapses).
5. Copy the token (you only see it once).
6. In the **dev** repo → **Settings → Secrets and variables → Actions → New repository secret** → name `DIST_TOKEN`, paste the value.

### 3. Seed the mirror with the first source sync

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
  --path docs/maintainer/ \
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

The filter list must stay in sync with the `.gitattributes` `export-ignore` patterns at the repo root — when you add a new mirror-excluded path, update both.

### 4. Seed the package-manager artifacts on the mirror

The release workflow updates `Formula/localdevstack.rb` and `bucket/localdevstack.json` in-place on each release, but the **initial** files have to exist. The filter-repo push from step 3 carries them across already — verify by checking that the mirror has `Formula/localdevstack.rb` and `bucket/localdevstack.json`. If they're missing, add a fresh commit on the mirror with placeholder versions (`v0.0.0`, all-zero sha256) and push.

### 5. Verify the round-trip

Tag a no-op release (e.g. `v1.2.0-rc1`) and watch the release workflow run end-to-end.

**The workflow chain.** All automation lives in the *dev* repo — nothing runs inside the mirror. A tag push there triggers `.github/workflows/release.yml`, which (a) builds the GraalVM native binaries for all three platforms, (b) uses `DIST_TOKEN` to create the GitHub release on `krishgok/localdevstack` and upload the assets, and (c) clones the mirror, rewrites `Formula/localdevstack.rb` + `bucket/localdevstack.json`, and pushes that commit back to the mirror's `main`.

**A note on the tag trigger.** The `push` trigger pattern in `release.yml` is `v[0-9]+.[0-9]+.[0-9]+`, which only fires on strict `vMAJOR.MINOR.PATCH` tags — `-rc1` suffixes are deliberately excluded so prereleases don't ship to brew/scoop users. Use `workflow_dispatch` to fire the workflow against an rc tag:

```bash
# 1. Tag this repo's HEAD with an rc tag. (Does NOT auto-trigger the workflow
#    because of the strict version regex — that exclusion is intentional.)
git tag v1.2.0-rc1
git push origin v1.2.0-rc1
```

2. Then fire the release workflow against the tag from the GitHub UI:
   - Dev repo on github.com → **Actions** tab → **Release** workflow in the left sidebar.
   - Click the **Run workflow** dropdown on the right.
   - **Use workflow from**: pick **Tags → `v1.2.0-rc1`** (not a branch).
   - **Tag to build**: enter `v1.2.0-rc1`.
   - Click **Run workflow**. Refresh after a few seconds to see the run, then open it to follow the logs until it goes green (or red).

  (CLI equivalent if `gh` is ever available: `gh workflow run release.yml --ref v1.2.0-rc1 -f tag=v1.2.0-rc1 && gh run watch`.)

Verify in order:

1. **Actions → Release** on the dev repo turns green.
2. `krishgok/localdevstack/releases/tag/v1.2.0-rc1` exists with `.exe`, `.tar.gz`, `.zip` assets + matching `.sha256` files for Linux x64, macOS arm64, Windows x64. (Intel macOS was dropped when GitHub retired the `macos-13` runner — Intel users build from source.)
3. The latest commit on `krishgok/localdevstack/main` is from the release workflow and updates `Formula/localdevstack.rb` + `bucket/localdevstack.json` to the new version + the published binary hashes.
4. `brew install krishgok/localdevstack/localdevstack` and `scoop install localdevstack` resolve to the new version on a fresh machine.

When all four are green, tear down the rc artifacts in both repos before tagging the real release.

**On the mirror (`krishgok/localdevstack`) via github.com:**

1. **Releases** (right sidebar of the repo home) → click the `v1.2.0-rc1` release → trash-can icon → **Delete**.
2. After the release is deleted, the tag still exists. **Tags** tab → find `v1.2.0-rc1` → trash-can icon → **Delete tag**. (Or: **Code** dropdown → **Tags** → same row.)

**On the dev repo:**

```bash
# Delete the tag locally and from the dev repo's remote.
git tag -d v1.2.0-rc1
git push origin :refs/tags/v1.2.0-rc1
```

(CLI equivalent for the mirror cleanup if `gh` is ever available: `gh release delete v1.2.0-rc1 --repo krishgok/localdevstack --yes --cleanup-tag`.)

---

## Per-release source sync to the mirror

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
  --path docs/maintainer/ \
  --path-glob 'claude-test-*' \
  --path-glob 'sweep*.out' \
  --path-glob 'smoke-*'
git remote add mirror https://github.com/krishgok/localdevstack.git
git push --force mirror master:main    # local master → remote main (mirror's default branch)
git push mirror --tags    # no --force on tags; collisions mean someone retagged manually
```

`--force` rewrites the mirror's `main` history because filter-repo always produces a fresh commit graph. This is by design — the mirror is a derived view of the dev repo, not its own development branch. Users should never `git pull` from the mirror expecting to preserve local commits; [CONTRIBUTING.md](../../CONTRIBUTING.md) makes this explicit at the top.

---

## Verifying the mirror is in sync

After any sync, sanity-check from a clean directory:

```bash
git clone https://github.com/krishgok/localdevstack.git /tmp/ldstack-mirror-check
cd /tmp/ldstack-mirror-check

# Must NOT exist:
test ! -e CLAUDE.md         && echo "ok: no CLAUDE.md"
test ! -e MAINTAINING.md    && echo "ok: no MAINTAINING.md"
test ! -d gen-build-deploy-tests && echo "ok: no gen-build-deploy-tests"
test ! -d docs/maintainer   && echo "ok: no docs/maintainer"

# Must exist:
test -f LICENSE                  && echo "ok: LICENSE present"
test -f README.md                && echo "ok: README.md present"
test -f CONTRIBUTING.md          && echo "ok: CONTRIBUTING.md present"
test -f docs/db-connections.md   && echo "ok: db-connections doc present"
test -f Formula/localdevstack.rb && echo "ok: Homebrew formula present"
test -f bucket/localdevstack.json && echo "ok: Scoop manifest present"
```

---

## Recovering from mirror divergence

If a maintainer (or a contributor with mirror write access) commits directly to the mirror and the next filter-repo `--force` push would clobber that work:

1. **Don't push** until you understand what was committed. `git log mirror/main..HEAD` on the mirror clone shows mirror-only commits.
2. **Cherry-pick into the dev repo** if the change belongs in the canonical source. Then re-run the per-release sync — the change appears on the mirror via the normal flow.
3. **Discard mirror-only commits** only after verifying nothing important is lost. Force-push proceeds as normal.

This is rare in practice because [CONTRIBUTING.md](../../CONTRIBUTING.md) and the mirror's README direct contributions to the dev repo. The recovery procedure exists because the mirror is technically write-enabled to accept the CI workflow's formula/manifest updates.
