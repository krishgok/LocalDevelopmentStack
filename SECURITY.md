# Security policy

## Reporting a vulnerability

**Do not open public issues for security vulnerabilities.** The Apache 2.0 license disclaims warranty, but credible reports are still worth fixing privately first.

Use **GitHub's private vulnerability reporting** on this repository:

1. Go to the **Security** tab at the top of the repo page
2. Click **Report a vulnerability**
3. Fill out the form — the maintainer is notified privately and can coordinate disclosure with you

If GitHub's vulnerability reporting is unreachable for any reason, open a minimal placeholder issue *without* sensitive details and request a private channel.

## Supported versions

Only the latest released version is supported with security fixes. Pre-release versions and source builds from `master` are not separately patched — track `master` for fixes.

## Disclosure timeline

- Reports are acknowledged within 7 days
- Fixes are coordinated privately
- Public disclosure follows the release that contains the fix

## Scope

LocalDevelopmentStack is a code-generation CLI. The vulnerability surface most likely concerns:

- **Generated `docker-compose.yml`** — credentials, exposed ports, container privilege boundaries
- **Generated `Dockerfile.dev`** — supply-chain risks via base images
- **CLI input handling** — path traversal, command injection via `--name`, `--output`, or `--existing-dir`

Issues in the *upstream Docker images* (e.g. `postgres:16`, `mysql:8`) should be reported to those projects directly. If our usage of an upstream image creates a downstream issue — insecure default config, deprecated tag, weakened healthcheck — report here.
