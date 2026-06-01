#!/usr/bin/env bash
# Existing-service sweep:
#   Strategy A — hand-written fixtures under gen-build-deploy-tests/existing-fixtures/<lang>/
#                exercise the path "user's existing project + LDS-emitted Dockerfile.dev".
#   Strategy B — new->existing round-trip smoke: for each supported language, generate as
#                new-service, strip the LDS-emitted Dockerfile.dev, then re-run with
#                --existing-dir against the generated source. Cross-mode invariant check.
#
# Diagonal coverage on the DB axis (postgres only). DB-side compose plumbing is already
# exercised by claude-test-sweep.sh's 72-combo grid; duplicating it here would add cost
# without coverage.

set -u

JAR="build/libs/LocalDevelopmentStack-1.2.1.jar"
STACK_DIR="claude-test-stack-existing-v1"
FIXTURES_DIR="gen-build-deploy-tests/existing-fixtures"
RESULTS="claude-test-results-existing.tsv"
LOGS_DIR="claude-test-logs-existing"
TIMEOUT=900
HEALTH_URL="http://localhost:8080/health"
DATABASE="postgres"

# Strategy A: hand-written fixtures, one per supported language. The ruby-rails
# variant exercises the Rails branch of RubyDockerfileGenerator (detected via
# bin/rails and config/application.rb).
FIXTURES=(dotnet go java node php python ruby ruby-rails rust springboot)

# Strategy B: round-trip smoke. Every new-service language gets generated then re-wrapped.
ROUNDTRIP_LANGS=(springboot go python node rust dotnet java php ruby)

# Optional filters: FIXTURE_FILTER / ROUNDTRIP_FILTER env vars (comma-separated).
# Setting either to "none" disables that phase entirely.
if [ -n "${FIXTURE_FILTER:-}" ]; then
  if [ "$FIXTURE_FILTER" = "none" ]; then FIXTURES=(); else IFS=',' read -r -a FIXTURES <<< "$FIXTURE_FILTER"; fi
fi
if [ -n "${ROUNDTRIP_FILTER:-}" ]; then
  if [ "$ROUNDTRIP_FILTER" = "none" ]; then ROUNDTRIP_LANGS=(); else IFS=',' read -r -a ROUNDTRIP_LANGS <<< "$ROUNDTRIP_FILTER"; fi
fi

mkdir -p "$LOGS_DIR"

# Append-mode header only if file missing
if [ ! -f "$RESULTS" ]; then
  printf "mode\tname\tresult\treason\tduration_s\n" > "$RESULTS"
fi

tear_down() {
  docker compose -p "$STACK_DIR" down -v --remove-orphans >/dev/null 2>&1 || true
  if [ -d "$STACK_DIR" ]; then
    (cd "$STACK_DIR" && docker compose down -v --remove-orphans >/dev/null 2>&1) || true
  fi
}

# Windows + Docker Desktop holds a bind-mount lock for several seconds after `compose down`.
# Retry rm -rf until it succeeds or we give up. Same pattern as the core sweep.
remove_stack_dir() {
  local target="${1:-$STACK_DIR}"
  for attempt in $(seq 1 40); do
    rm -rf "$target" 2>/dev/null && return 0
    [ ! -e "$target" ] && return 0
    sleep 5
  done
  echo "ABORT: cannot remove $target after 200s of retries (still locked)" >&2
  return 1
}

# $1 = mode, $2 = name. Brings the stack up, polls /health, records pass/fail to TSV.
run_health_check() {
  local mode="$1"
  local name="$2"
  local combo="${mode}_${name}"
  local start=$(date +%s)

  if ! (cd "$STACK_DIR" && timeout ${TIMEOUT}s docker compose up -d --build) \
       > "$LOGS_DIR/${combo}-up.log" 2>&1; then
    local end=$(date +%s)
    (cd "$STACK_DIR" && docker compose ps)              >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
    (cd "$STACK_DIR" && docker compose logs --tail=80)  >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
    tear_down
    printf "%s\t%s\tFAIL\tcompose up failed/timeout\t%d\n" "$mode" "$name" $((end-start)) >> "$RESULTS"
    echo "  FAIL: compose up"
    return 1
  fi

  local health_pass=false
  local deadline=$((start + TIMEOUT))
  while [ "$(date +%s)" -lt $deadline ]; do
    if curl -fsS -m 3 "$HEALTH_URL" >/dev/null 2>&1; then
      health_pass=true
      break
    fi
    sleep 3
  done
  local end=$(date +%s)

  if $health_pass; then
    printf "%s\t%s\tPASS\thealth 200\t%d\n" "$mode" "$name" $((end-start)) >> "$RESULTS"
    echo "  PASS ($((end-start))s)"
    tear_down
    return 0
  fi

  (cd "$STACK_DIR" && docker compose ps)              >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
  (cd "$STACK_DIR" && docker compose logs --tail=120) >> "$LOGS_DIR/${combo}-up.log" 2>&1 || true
  printf "%s\t%s\tFAIL\thealth never reached\t%d\n" "$mode" "$name" $((end-start)) >> "$RESULTS"
  echo "  FAIL: health never reached ($((end-start))s)"
  tear_down
  return 1
}

# -----------------------------------------------------------------------------
# Strategy A: hand-written fixtures
# -----------------------------------------------------------------------------
for fixture in "${FIXTURES[@]}"; do
  combo="fixture_${fixture}"
  if grep -qE "^fixture\s+${fixture}\s+" "$RESULTS" 2>/dev/null; then
    echo "skip $combo (already in $RESULTS)"
    continue
  fi
  echo
  echo "=== $(date +%H:%M:%S) $combo ==="
  start=$(date +%s)

  tear_down
  if ! remove_stack_dir; then
    printf "fixture\t%s\tABORT\tfolder locked, sweep aborted\t0\n" "$fixture" >> "$RESULTS"
    exit 2
  fi

  if [ ! -d "$FIXTURES_DIR/$fixture" ]; then
    end=$(date +%s)
    printf "fixture\t%s\tFAIL\tfixture dir missing: %s\t%d\n" "$fixture" "$FIXTURES_DIR/$fixture" $((end-start)) >> "$RESULTS"
    echo "  FAIL: fixture dir missing ($FIXTURES_DIR/$fixture)"
    continue
  fi
  cp -r "$FIXTURES_DIR/$fixture" "$STACK_DIR"

  if ! java -jar "$JAR" --existing-dir "./$STACK_DIR" --database "$DATABASE" \
       --name claude-test-api --port 8080 --force \
       > "$LOGS_DIR/${combo}-gen.log" 2>&1; then
    end=$(date +%s)
    printf "fixture\t%s\tFAIL\tgenerate failed\t%d\n" "$fixture" $((end-start)) >> "$RESULTS"
    echo "  FAIL: generate (see $LOGS_DIR/${combo}-gen.log)"
    continue
  fi

  run_health_check fixture "$fixture"
done

# -----------------------------------------------------------------------------
# Strategy B: new->existing round-trip smoke
# -----------------------------------------------------------------------------
TMP_GEN="${STACK_DIR}-gentmp"

for lang in "${ROUNDTRIP_LANGS[@]}"; do
  combo="roundtrip_${lang}"
  if grep -qE "^roundtrip\s+${lang}\s+" "$RESULTS" 2>/dev/null; then
    echo "skip $combo (already in $RESULTS)"
    continue
  fi
  echo
  echo "=== $(date +%H:%M:%S) $combo ==="
  start=$(date +%s)

  tear_down
  if ! remove_stack_dir; then
    printf "roundtrip\t%s\tABORT\tfolder locked, sweep aborted\t0\n" "$lang" >> "$RESULTS"
    exit 2
  fi
  if ! remove_stack_dir "$TMP_GEN"; then
    printf "roundtrip\t%s\tABORT\ttmp folder locked, sweep aborted\t0\n" "$lang" >> "$RESULTS"
    exit 2
  fi

  # Phase 1: generate as new-service into TMP_GEN
  if ! java -jar "$JAR" --service "$lang" --database "$DATABASE" \
       --output "./$TMP_GEN" --name claude-test-api --port 8080 --force \
       > "$LOGS_DIR/${combo}-gen.log" 2>&1; then
    end=$(date +%s)
    printf "roundtrip\t%s\tFAIL\tnew-service generate failed\t%d\n" "$lang" $((end-start)) >> "$RESULTS"
    echo "  FAIL: new-service generate (see $LOGS_DIR/${combo}-gen.log)"
    continue
  fi

  # Phase 2: extract the generated source, strip the LDS-emitted Dockerfile, re-wrap as existing-service
  if [ ! -d "$TMP_GEN/service" ]; then
    end=$(date +%s)
    printf "roundtrip\t%s\tFAIL\tnew-service did not produce service/\t%d\n" "$lang" $((end-start)) >> "$RESULTS"
    echo "  FAIL: $TMP_GEN/service missing after new-service generate"
    continue
  fi
  mv "$TMP_GEN/service" "$STACK_DIR"
  rm -f "$STACK_DIR/Dockerfile.dev"
  remove_stack_dir "$TMP_GEN" || true

  if ! java -jar "$JAR" --existing-dir "./$STACK_DIR" --database "$DATABASE" \
       --name claude-test-api --port 8080 --force \
       >> "$LOGS_DIR/${combo}-gen.log" 2>&1; then
    end=$(date +%s)
    printf "roundtrip\t%s\tFAIL\texisting-dir generate failed\t%d\n" "$lang" $((end-start)) >> "$RESULTS"
    echo "  FAIL: existing-dir generate (see $LOGS_DIR/${combo}-gen.log)"
    continue
  fi

  run_health_check roundtrip "$lang"
done

echo
echo "Done. Results: $RESULTS  Logs: $LOGS_DIR/"
