#!/usr/bin/env bash
# 72-combo sweep: 9 services x 8 databases.
# Generates each combo into claude-test-stack/, runs docker compose up -d --build,
# polls /health, records pass/fail, tears down.

set -u

JAR="build/libs/LocalDevelopmentStack-1.1.0.jar"
STACK_DIR="claude-test-stack-v3"
RESULTS="claude-test-results.tsv"
LOGS_DIR="claude-test-logs"
TIMEOUT=900
HEALTH_URL="http://localhost:8080/health"

SERVICES=(springboot go python node rust dotnet java php ruby)
DATABASES=(postgres mysql mongodb cockroachdb redis mariadb sqlserver elasticsearch)

# Optional filters: SVC_FILTER / DB_FILTER env vars (comma-separated)
if [ -n "${SVC_FILTER:-}" ]; then IFS=',' read -r -a SERVICES <<< "$SVC_FILTER"; fi
if [ -n "${DB_FILTER:-}"  ]; then IFS=',' read -r -a DATABASES <<< "$DB_FILTER";  fi

mkdir -p "$LOGS_DIR"

# Append-mode header only if file missing
if [ ! -f "$RESULTS" ]; then
  printf "service\tdatabase\tresult\treason\tduration_s\n" > "$RESULTS"
fi

tear_down() {
  # Target by compose project name (folder basename) so leftovers without a
  # compose file still get cleaned up.
  docker compose -p "$STACK_DIR" down -v --remove-orphans >/dev/null 2>&1 || true
  if [ -d "$STACK_DIR" ]; then
    (cd "$STACK_DIR" && docker compose down -v --remove-orphans >/dev/null 2>&1) || true
  fi
}

# Windows + Docker Desktop holds a bind-mount lock on the folder for several
# seconds after `compose down`. Retry rm -rf until it succeeds or we give up.
remove_stack_dir() {
  for attempt in $(seq 1 40); do
    rm -rf "$STACK_DIR" 2>/dev/null && return 0
    [ ! -e "$STACK_DIR" ] && return 0
    sleep 5
  done
  echo "ABORT: cannot remove $STACK_DIR after 200s of retries (still locked)" >&2
  return 1
}

for svc in "${SERVICES[@]}"; do
  for db in "${DATABASES[@]}"; do
    combo="${svc}_${db}"
    # Skip if already recorded (resume support)
    if grep -qE "^${svc}\s+${db}\s+" "$RESULTS" 2>/dev/null; then
      echo "skip $combo (already in $RESULTS)"
      continue
    fi
    echo
    echo "=== $(date +%H:%M:%S) $combo ==="
    start=$(date +%s)

    tear_down
    if ! remove_stack_dir; then
      printf "%s\t%s\tABORT\tfolder locked, sweep aborted\t0\n" "$svc" "$db" >> "$RESULTS"
      exit 2
    fi

    # Generate (force overwrite into same folder)
    if ! java -jar "$JAR" --service "$svc" --database "$db" \
         --output "./$STACK_DIR" --name claude-test-api --port 8080 --force \
         > "$LOGS_DIR/${combo}-gen.log" 2>&1; then
      end=$(date +%s)
      printf "%s\t%s\tFAIL\tgenerate failed\t%d\n" "$svc" "$db" $((end-start)) >> "$RESULTS"
      echo "  FAIL: generate (see $LOGS_DIR/${combo}-gen.log)"
      continue
    fi

    # Build + start (detached). The -d --build waits for build to finish before returning.
    if ! (cd "$STACK_DIR" && timeout ${TIMEOUT}s docker compose up -d --build) \
         > "$LOGS_DIR/${combo}-up.log" 2>&1; then
      end=$(date +%s)
      (cd "$STACK_DIR" && docker compose ps)       >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
      (cd "$STACK_DIR" && docker compose logs --tail=80) >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
      tear_down
      printf "%s\t%s\tFAIL\tcompose up failed/timeout\t%d\n" "$svc" "$db" $((end-start)) >> "$RESULTS"
      echo "  FAIL: compose up"
      continue
    fi

    # Poll /health until deadline
    health_pass=false
    deadline=$((start + TIMEOUT))
    while [ "$(date +%s)" -lt $deadline ]; do
      if curl -fsS -m 3 "$HEALTH_URL" >/dev/null 2>&1; then
        health_pass=true
        break
      fi
      sleep 3
    done
    end=$(date +%s)

    if $health_pass; then
      printf "%s\t%s\tPASS\thealth 200\t%d\n" "$svc" "$db" $((end-start)) >> "$RESULTS"
      echo "  PASS ($((end-start))s)"
    else
      (cd "$STACK_DIR" && docker compose ps)       >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
      (cd "$STACK_DIR" && docker compose logs --tail=120) >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
      printf "%s\t%s\tFAIL\thealth never reached\t%d\n" "$svc" "$db" $((end-start)) >> "$RESULTS"
      echo "  FAIL: health never reached ($((end-start))s)"
    fi
    tear_down
  done
done

echo
echo "Done. Results: $RESULTS  Logs: $LOGS_DIR/"
