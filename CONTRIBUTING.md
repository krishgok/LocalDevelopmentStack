# Contributing to LocalDevelopmentStack

Thanks for your interest. This guide covers the popular ways to contribute on GitHub and what each one assumes about you and your change.

---

## Where to contribute

**All contributions go to the development repository:**

> **https://github.com/krishgok/LocalDevelopmentStack**

The repository at https://github.com/krishgok/localdevstack is a **read-only public mirror**. It exists only to distribute pre-built binaries via Homebrew, Scoop, and the curl / PowerShell installers. Its commit history is **force-rewritten on every sync** from the dev repo, which means anything committed directly to the mirror is **silently overwritten on the next release** without notice.

| Action                                | Where to do it                                                            |
|---------------------------------------|---------------------------------------------------------------------------|
| File an issue or feature request      | Dev repo — https://github.com/krishgok/LocalDevelopmentStack/issues       |
| Open a pull request                   | Dev repo — https://github.com/krishgok/LocalDevelopmentStack/pulls        |
| Browse source / clone for development | Dev repo                                                                  |
| Download a pre-built binary           | Mirror — https://github.com/krishgok/localdevstack/releases               |
| `brew install` / `scoop install`      | Mirror (the package managers point there by design)                       |

PRs opened against the mirror will be closed with a redirect link. Issues filed at the mirror will be acknowledged but any code discussion moves to the dev repo.

---

## Popular contribution approaches

GitHub supports several contribution workflows. The right one depends on whether you have direct write access, whether the change needs discussion before code is written, and how big it is.

### 1. Open an issue

**Best for:** bug reports, feature requests, questions, design discussion *before* code is written.

**Assumes:**
- You can describe what you observed vs. what you expected.
- For bugs: you can include the failing command (or a `--dry-run` plan), the relevant slice of the generated files, your OS / Docker version, and the error output.
- For feature requests: you've checked the [Roadmap](README.md#roadmap) and the existing issue list for duplicates.

**Limitations:**
- Issues are **public from the moment they're filed**. Do not include secrets, credentials, or proprietary code. For security issues, see [Security disclosure](#security-disclosure).
- Labels, milestones, and assignees can only be set by maintainers.
- Issues with no reproducer or no proposed acceptance criteria may be closed as "needs more info" rather than worked on.

### 2. Fork + pull request (the standard external flow)

**Best for:** external contributors. This is the workflow GitHub assumes by default for anyone without write access.

**Steps:**
1. Click **Fork** (top-right of the dev repo) to create `<your-account>/LocalDevelopmentStack`.
2. Clone your fork: `git clone https://github.com/<your-account>/LocalDevelopmentStack.git`.
3. Create a branch: `git checkout -b feat/short-description` (or `fix/`, `docs/`, `chore/`).
4. Make your change. Run `./gradlew test` locally before pushing.
5. Push to your fork: `git push origin feat/short-description`.
6. Open a PR from your branch into `krishgok/LocalDevelopmentStack:master`. GitHub offers a "Compare & pull request" button on your fork after the push.

**Assumes:**
- You have a free GitHub account and Git installed locally.
- The change is yours, or you have the right to submit it under Apache 2.0 — see [License agreement](#license-agreement).
- For **non-trivial changes** (~50+ lines, a new generator, a CLI flag change, a public-API tweak), you opened an issue first and the maintainer agreed on the approach. Surprise PRs for large changes are likely to be closed without merge.

**Limitations:**
- **First-time fork PRs do not run CI automatically.** A maintainer must approve the first workflow run. After that approval, your subsequent PRs run automatically.
- **CI secrets are never exposed to fork PRs** — `DIST_TOKEN` and other release-time secrets are unavailable, by GitHub's design. Your PR build runs tests and `nativeCompile`, but cannot publish.
- Your branch can drift from `master` over a long review cycle. Rebase regularly: `git pull --rebase upstream master` (where `upstream` is the dev repo).
- Force-pushes to your fork's PR branch are fine, but lose existing review comments' line anchoring. Prefer follow-up commits during review; squash at the end if needed.

### 3. Branch + pull request (for collaborators with write access)

**Best for:** maintainers and collaborators who have been added to the dev repo.

**Steps:**
1. Clone the dev repo directly: `git clone https://github.com/krishgok/LocalDevelopmentStack.git`.
2. Create a branch in-repo: `git checkout -b feat/short-description`.
3. Push the branch: `git push origin feat/short-description`.
4. Open a PR from your branch into `master`.

**Assumes:**
- You've been granted **Write** access in **Settings → Collaborators**.
- You respect branch protection on `master` — no direct commits, no force-pushes, always via PR.

**Limitations:**
- Long-lived feature branches in the dev repo are visible to everyone; for exploratory or speculative work, prefer a fork.
- Branch protection means there is no "just push to master" shortcut — even maintainers go via PR.

### 4. GitHub Discussions

**Best for:** open-ended questions ("how would you approach X?"), brainstorming, design proposals that aren't concrete enough for an issue yet.

**Assumes:**
- Discussions are enabled on the dev repo. Check for a **Discussions** tab in the top navigation. If absent, file an issue instead.
- Your topic isn't actionable yet — if it is, an issue is the better home.

**Limitations:**
- Discussions don't have milestones or assignees and don't appear on the project board.
- Maintainers may not respond as fast on discussions as on issues / PRs.
- A "winning" outcome from a discussion still needs an issue or PR to actually land in code.

### 5. Reviewing someone else's PR

Code review is also a contribution. Anyone can comment on any public PR.

**Assumes:**
- You've read the PR description and skimmed the diff.
- Your feedback is specific — quote the line / file, suggest a change, not just "this looks wrong."

**Limitations:**
- Only the PR author or a maintainer can mark a thread resolved.
- Only maintainers can approve a PR for merge (Settings → Branch protection → required reviewers).

---

## Before you submit a PR

1. **Run the tests:** `./gradlew test`. Every PR must pass.
2. **Match the surrounding style.** There is no separate style guide — mirror the conventions of the file you're editing.
3. **Architecture invariants live in `CLAUDE.md`.** Read the relevant section before making non-trivial changes. The "don't drift from these" callouts encode lessons learned from real failures.
4. **Adding a new service / database / companion / migration type** has its own checklist — see [docs/maintainer/extending.md](docs/maintainer/extending.md) for the full list.
5. **Update docs in the same PR.** If you change CLI flags, supported types, or generator behaviour, update `README.md` (user-facing) and `CLAUDE.md` (architecture) together with the code.
6. **Keep PRs focused.** Drive-by refactors and tangential cleanups belong in a separate PR — they slow review and make rollback harder.

---

## License agreement

LocalDevelopmentStack is licensed under [Apache 2.0](LICENSE). By opening a pull request, you certify that:

- You have the right to submit the contribution.
- The contribution is your original work, or properly attributed if not.
- You agree your contribution is licensed under Apache 2.0, the same as the rest of the project.

There is no separate CLA. You may add a `Signed-off-by` trailer via `git commit -s` (the Developer Certificate of Origin convention), but it is not required.

---

## Security disclosure

**Do not** open public issues for security vulnerabilities. The Apache 2.0 license disclaims warranty, but credible reports are still worth fixing privately first.

Use **GitHub's private vulnerability reporting** on the dev repo: **Security** tab → **Report a vulnerability**. The maintainer is notified privately and can coordinate disclosure with you.

---

## Code of conduct

This project adopts the [Contributor Covenant v1.4](https://www.contributor-covenant.org/version/1/4/code-of-conduct/). By participating — opening an issue, submitting a PR, commenting, or contributing in any other form — you agree to uphold it.

**In summary:** be respectful, welcoming, and considerate. Critique code and ideas, not people. Harassment, personal attacks, derogatory comments, and trolling are not acceptable.

**Reporting:** report unacceptable behaviour privately via the **Security → Report a vulnerability** form on the dev repo. The maintainer is the only recipient and all reports are kept confidential.

**Enforcement:** the maintainer may remove comments, lock threads, close issues / PRs, or temporarily ban participants who violate the code. Persistent or serious violations may result in permanent removal.

The full pledge, standards, scope, and attribution are on the [Contributor Covenant v1.4 page](https://www.contributor-covenant.org/version/1/4/code-of-conduct/).
