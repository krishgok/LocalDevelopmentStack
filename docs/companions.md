# Companion services (optional)

Pass `--with <name>[,<name>...]` to include extra dev-only services in `docker-compose.yml`. Both are off by default.

| `--with`  | Image                            | Ports          | Purpose                              | Env vars injected into your service |
|-----------|----------------------------------|----------------|--------------------------------------|-------------------------------------|
| `mailhog` | `mailhog/mailhog:v1.0.1`         | `1025`, `8025` | SMTP catcher with a web UI on `:8025`. Capture outgoing email instead of sending it. | `SMTP_HOST=mailhog`, `SMTP_PORT=1025` |
| `minio`   | `minio/minio:RELEASE.2024-12-18` | `9000`, `9001` | S3-compatible object store with a console on `:9001`. | `S3_ENDPOINT=http://minio:9000`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` |

```bash
localdevstack --service node --database postgres --with mailhog,minio --output ./my-stack
```

Companions land in the same `docker-compose.yml` and start with `docker-compose up`. Their default credentials are local-dev values stored in `.env` — change them there, not in compose.
