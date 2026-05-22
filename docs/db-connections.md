# Reading the database connection in your service

LocalDevelopmentStack injects a connection string into your service container as an environment variable. The variable name depends on the database:

| `--database`    | Injected env var     |
|-----------------|----------------------|
| `postgres`      | `DATABASE_URL`       |
| `mysql`         | `DATABASE_URL`       |
| `mariadb`       | `DATABASE_URL`       |
| `cockroachdb`   | `DATABASE_URL`       |
| `sqlserver`     | `DATABASE_URL`       |
| `mongodb`       | `MONGODB_URI`        |
| `redis`         | `REDIS_URL`          |
| `elasticsearch` | `ELASTICSEARCH_URL`  |

Your service must read the connection string from the environment at startup — not from a hardcoded string or a config file checked into source control. This is standard [12-factor app](https://12factor.net/config) practice, and it ensures the same code works locally (pointing at Docker) and in production (pointing at your real database) without changes.

The injected hostname is always `db` — it resolves to the database container on the Docker Compose network.

---

## Per-language examples

### Go

```go
import "os"

dsn := os.Getenv("DATABASE_URL")
// e.g. "postgresql://postgres:postgres_dev_only@db:5432/app_db"
db, err := sql.Open("pgx", dsn)
```

### Python (FastAPI / SQLAlchemy)

```python
import os
DATABASE_URL = os.environ["DATABASE_URL"]
engine = create_engine(DATABASE_URL)
```

### Node.js

```js
const { DATABASE_URL } = process.env;
const pool = new Pool({ connectionString: DATABASE_URL });
```

### Spring Boot (Kotlin / Java)

Spring Boot reads `DATABASE_URL` automatically when you set `spring.datasource.url` to the env var in `application.properties`:

```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASS:postgres_dev_only}
```

Or in `application.yml`:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
```

### Rust (sqlx)

```rust
let database_url = std::env::var("DATABASE_URL").expect("DATABASE_URL must be set");
let pool = PgPool::connect(&database_url).await?;
```

### PHP (Laravel)

Laravel reads `DATABASE_URL` from `.env` automatically. If you use a Docker Compose env var instead of a `.env` file, set it in `config/database.php`:

```php
'url' => env('DATABASE_URL'),
```

### Ruby (Rails)

Rails reads `DATABASE_URL` from the environment automatically — no code change needed. If `DATABASE_URL` is set, it overrides `config/database.yml`.

### .NET (ASP.NET Core)

```csharp
var connectionString = builder.Configuration["DATABASE_URL"]
    ?? Environment.GetEnvironmentVariable("DATABASE_URL");
builder.Services.AddDbContext<AppDbContext>(opt =>
    opt.UseNpgsql(connectionString));
```

### Redis (`REDIS_URL`)

```go
// Go
redisUrl := os.Getenv("REDIS_URL") // "redis://db:6379"
```

```js
// Node.js
const client = createClient({ url: process.env.REDIS_URL });
```

```python
# Python
import redis, os
r = redis.from_url(os.environ["REDIS_URL"])
```

### Elasticsearch (`ELASTICSEARCH_URL`)

```js
// Node.js
const { Client } = require('@elastic/elasticsearch');
const client = new Client({ node: process.env.ELASTICSEARCH_URL });
```

```python
# Python
from elasticsearch import Elasticsearch
import os
es = Elasticsearch(os.environ["ELASTICSEARCH_URL"])
```

---

## Migrating a hardcoded host

If your service currently uses a hardcoded host like `localhost:5432`, replace it with the env var read shown above. The Docker Compose network resolves `db` to the database container; on your host (outside Docker) `localhost` works as before — so reading from an env var with a sensible local default keeps both workflows working:

```go
dsn := os.Getenv("DATABASE_URL")
if dsn == "" {
    dsn = "postgresql://postgres:postgres@localhost:5432/app_db"
}
```
