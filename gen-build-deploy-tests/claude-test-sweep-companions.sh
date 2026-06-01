#!/usr/bin/env bash
# Extended 1.2.0 companion sweep: 9 services x --with mailhog (postgres) +
# 9 services x --with minio (postgres) + 1 omnibus combo
# (springboot+postgres+mailhog,minio+flyway).
#
# Appends to claude-test-results-companions.tsv. Resume-safe.

set -u

JAR="build/libs/LocalDevelopmentStack-1.2.1.jar"
STACK_DIR="claude-test-stack-companion-v1"
RESULTS="claude-test-results-companions.tsv"
LOGS_DIR="claude-test-logs"
TIMEOUT=900
HEALTH_URL="http://localhost:8080/health"

SERVICES=(springboot go python node rust dotnet java php ruby)

mkdir -p "$LOGS_DIR"

if [ ! -f "$RESULTS" ]; then
  printf "service\tcompanions\tresult\treason\tduration_s\n" > "$RESULTS"
fi

tear_down() {
  docker compose -p "$STACK_DIR" down -v --remove-orphans >/dev/null 2>&1 || true
  if [ -d "$STACK_DIR" ]; then
    (cd "$STACK_DIR" && docker compose down -v --remove-orphans >/dev/null 2>&1) || true
  fi
}

remove_stack_dir() {
  for attempt in $(seq 1 40); do
    rm -rf "$STACK_DIR" 2>/dev/null && return 0
    [ ! -e "$STACK_DIR" ] && return 0
    sleep 5
  done
  echo "ABORT: cannot remove $STACK_DIR after 200s of retries (still locked)" >&2
  return 1
}

# poll_until_200 <url> <deadline_epoch>
poll_until_200() {
  local url="$1"
  local deadline="$2"
  while [ "$(date +%s)" -lt "$deadline" ]; do
    curl -fsS -m 3 "$url" >/dev/null 2>&1 && return 0
    sleep 3
  done
  return 1
}

# run_single <svc> <with-token> <probe-url>
run_single() {
  local svc="$1"
  local with="$2"
  local probe="$3"
  local label="${svc}_${with}"
  if grep -qE "^${svc}\s+${with}\s+" "$RESULTS" 2>/dev/null; then
    echo "skip $label (already in $RESULTS)"
    return 0
  fi
  echo
  echo "=== $(date +%H:%M:%S) ${label} ==="
  local start; start=$(date +%s)

  tear_down
  remove_stack_dir || { printf "%s\t%s\tABORT\tfolder locked\t0\n" "$svc" "$with" >> "$RESULTS"; return 2; }

  if ! java -jar "$JAR" --service "$svc" --database postgres \
       --output "./$STACK_DIR" --name claude-comp-api --port 8080 --force \
       --with "$with" \
       > "$LOGS_DIR/${label}-gen.log" 2>&1; then
    local end; end=$(date +%s)
    printf "%s\t%s\tFAIL\tgenerate failed\t%d\n" "$svc" "$with" $((end-start)) >> "$RESULTS"
    echo "  FAIL: generate"
    return 1
  fi

  if ! (cd "$STACK_DIR" && timeout ${TIMEOUT}s docker compose up -d --build) \
       > "$LOGS_DIR/${label}-up.log" 2>&1; then
    (cd "$STACK_DIR" && docker compose ps)       >> "$LOGS_DIR/${label}-up.log" 2>&1 || true
    (cd "$STACK_DIR" && docker compose logs --tail=80) >> "$LOGS_DIR/${label}-up.log" 2>&1 || true
    tear_down
    local end; end=$(date +%s)
    printf "%s\t%s\tFAIL\tcompose up failed/timeout\t%d\n" "$svc" "$with" $((end-start)) >> "$RESULTS"
    echo "  FAIL: compose up"
    return 1
  fi

  local deadline=$((start + TIMEOUT))
  if ! poll_until_200 "$HEALTH_URL" "$deadline"; then
    (cd "$STACK_DIR" && docker compose ps)       >> "$LOGS_DIR/${label}-up.log" 2>&1 || true
    (cd "$STACK_DIR" && docker compose logs --tail=120) >> "$LOGS_DIR/${label}-up.log" 2>&1 || true
    tear_down
    local end; end=$(date +%s)
    printf "%s\t%s\tFAIL\tservice /health never reached\t%d\n" "$svc" "$with" $((end-start)) >> "$RESULTS"
    echo "  FAIL: /health"
    return 1
  fi

  if ! poll_until_200 "$probe" "$deadline"; then
    (cd "$STACK_DIR" && docker compose ps)       >> "$LOGS_DIR/${label}-up.log" 2>&1 || true
    (cd "$STACK_DIR" && docker compose logs --tail=120) >> "$LOGS_DIR/${label}-up.log" 2>&1 || true
    tear_down
    local end; end=$(date +%s)
    printf "%s\t%s\tFAIL\tcompanion %s never 200\t%d\n" "$svc" "$with" "$probe" $((end-start)) >> "$RESULTS"
    echo "  FAIL: companion probe"
    return 1
  fi

  local end; end=$(date +%s)
  printf "%s\t%s\tPASS\thealth+companion 200\t%d\n" "$svc" "$with" $((end-start)) >> "$RESULTS"
  echo "  PASS ($((end-start))s)"
  tear_down
}

# Phase 1: --with mailhog
for svc in "${SERVICES[@]}"; do
  run_single "$svc" "mailhog" "http://localhost:8025/"
done

# Phase 2: --with minio
for svc in "${SERVICES[@]}"; do
  run_single "$svc" "minio" "http://localhost:9000/minio/health/live"
done

# Phase 3: omnibus
if ! grep -qE "^omnibus\s+" "$RESULTS" 2>/dev/null; then
  echo
  echo "=== $(date +%H:%M:%S) omnibus (springboot + postgres + mailhog,minio + flyway) ==="
  start=$(date +%s)
  tear_down
  remove_stack_dir || true
  fail_reason=""
  if ! java -jar "$JAR" --service springboot --database postgres \
       --output "./$STACK_DIR" --name claude-omnibus-api --port 8080 --force \
       --with mailhog,minio --migration flyway \
       > "$LOGS_DIR/omnibus-gen.log" 2>&1; then
    fail_reason="generate failed"
  elif ! (cd "$STACK_DIR" && timeout ${TIMEOUT}s docker compose up -d --build) \
       > "$LOGS_DIR/omnibus-up.log" 2>&1; then
    fail_reason="compose up failed"
  else
    deadline=$((start + TIMEOUT))
    poll_until_200 "$HEALTH_URL" $deadline || fail_reason="/health never reached"
    [ -z "$fail_reason" ] && { poll_until_200 "http://localhost:8025/" $deadline || fail_reason="mailhog never reached"; }
    [ -z "$fail_reason" ] && { poll_until_200 "http://localhost:9000/minio/health/live" $deadline || fail_reason="minio never reached"; }
  fi
  end=$(date +%s)
  if [ -z "$fail_reason" ]; then
    printf "omnibus\tmailhog,minio,flyway\tPASS\tall probes 200\t%d\n" $((end-start)) >> "$RESULTS"
    echo "  PASS ($((end-start))s)"
  else
    (cd "$STACK_DIR" && docker compose logs --tail=120) >> "$LOGS_DIR/omnibus-up.log" 2>&1 || true
    printf "omnibus\tmailhog,minio,flyway\tFAIL\t%s\t%d\n" "$fail_reason" $((end-start)) >> "$RESULTS"
    echo "  FAIL: $fail_reason"
  fi
  tear_down
fi

echo
echo "Done. Results: $RESULTS  Logs: $LOGS_DIR/"
