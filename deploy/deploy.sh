#!/bin/bash
set -euo pipefail

MODE="${1:-}"
BOOTSTRAP=true
BUILD=false
CLEAN=false

usage() {
  echo "Usage: ./deploy/deploy.sh [dev|production] [--build] [--clean] [--no-bootstrap]"
}

if [[ -z "$MODE" || ("$MODE" != "dev" && "$MODE" != "production") ]]; then
  usage
  exit 1
fi

shift
for arg in "$@"; do
  case "$arg" in
    --no-bootstrap) BOOTSTRAP=false ;;
    --build) BUILD=true ;;
    --clean) CLEAN=true ;;
    *)
      echo "Unknown argument: $arg"
      usage
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
COMPOSE_FILE="$PROJECT_ROOT/deploy/docker-compose.yaml"

compose() {
  docker-compose -f "$COMPOSE_FILE" "$@"
}

start_mongo() {
  echo "Starting MongoDB infrastructure..."
  compose up -d mongodb
}

wait_for_mongo() {
  local max_attempts=60
  local attempt=1

  echo "Waiting for MongoDB to become ready..."
  until compose exec -T mongodb mongosh --quiet --eval 'db.adminCommand({ ping: 1 }).ok' >/dev/null 2>&1; do
    if [ "$attempt" -ge "$max_attempts" ]; then
      echo "❌ MongoDB did not become ready in time."
      return 1
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
}

upsert_connections() {
  local connections_file="$PROJECT_ROOT/deploy/connections.json"

  if [ ! -f "$connections_file" ]; then
    echo "No connections file found at $connections_file. Skipping connection upsert."
    return
  fi

  local connections_json
  connections_json="$(cat "$connections_file")"
  if [ -z "${connections_json//[[:space:]]/}" ]; then
    echo "Connections file is empty. Skipping connection upsert."
    return
  fi

  echo "Upserting connections from $connections_file..."
  compose exec -T \
    -e CONNECTIONS_JSON="$connections_json" \
    mongodb mongosh --quiet --eval '
const docs = JSON.parse(process.env.CONNECTIONS_JSON ?? "[]");
if (!Array.isArray(docs)) {
  throw new Error("connections.json must be a JSON array");
}
const collection = db.getSiblingDB("AGENT_ENGINE").getCollection("Connection");
let upserts = 0;
let skipped = 0;
for (const doc of docs) {
  if (!doc || typeof doc !== "object" || !doc.appName || String(doc.appName).trim() === "") {
    skipped++;
    continue;
  }
  const payload = {
    appName: doc.appName,
    inputs: doc.inputs && typeof doc.inputs === "object" ? doc.inputs : {}
  };
  collection.updateOne({ appName: doc.appName }, { $set: payload }, { upsert: true });
  upserts++;
}
print(`connections_upserted=${upserts}`);
if (skipped > 0) {
  print(`connections_skipped=${skipped}`);
}
'
}

bootstrap_mongo_state() {
  wait_for_mongo
  upsert_connections
}

wait_for_http() {
  local url="$1"
  local max_attempts="${2:-120}"
  local attempt=1

  until curl -s -m 10 "$url" >/dev/null; do
    if [ "$attempt" -ge "$max_attempts" ]; then
      echo "❌ Service did not become ready: $url"
      return 1
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
}

bootstrap_data() {
  local api_url="http://localhost:18080"
  echo "Waiting for REST API to become responsive on $api_url..."
  wait_for_http "$api_url/q/openapi"
  AGENT_ENGINE_API_URL="$api_url" "$PROJECT_ROOT/deploy/sync.sh"
}

build_production_apps() {
  if [ "$CLEAN" != true ] && [ "$BUILD" != true ]; then
    echo "Skipping build (use --build or --clean to rebuild)..."
    return
  fi

  mkdir -p "$PROJECT_ROOT/logs"

  local engine_cmd
  local rest_cmd
  if [ "$CLEAN" = true ]; then
    echo "Building quarkus apps in parallel (clean)..."
    engine_cmd=(./gradlew :engine:clean :engine:quarkusBuild -x test -x spotlessCheck)
    rest_cmd=(./gradlew :interfaces:rest:clean :interfaces:rest:quarkusBuild -x test -x spotlessCheck)
  else
    echo "Building quarkus apps in parallel..."
    engine_cmd=(./gradlew :engine:quarkusBuild -x test -x spotlessCheck)
    rest_cmd=(./gradlew :interfaces:rest:quarkusBuild -x test -x spotlessCheck)
  fi

  "${engine_cmd[@]}" > "$PROJECT_ROOT/logs/build-engine.log" 2>&1 &
  local engine_pid=$!
  "${rest_cmd[@]}" > "$PROJECT_ROOT/logs/build-rest.log" 2>&1 &
  local rest_pid=$!

  local failed=0
  wait "$engine_pid" || failed=1
  wait "$rest_pid" || failed=1

  if [ "$failed" -ne 0 ]; then
    echo "❌ Build failed. Check logs/build-engine.log and logs/build-rest.log"
    exit 1
  fi
}

start_production() {
  echo "Starting in PRODUCTION mode (Separate Services)..."
  build_production_apps

  mkdir -p "$PROJECT_ROOT/logs"

  echo "Starting Engine (Port 18081, gRPC 19000)..."
  nohup java --enable-preview \
    -Dquarkus.http.port=18081 \
    -Dquarkus.grpc.server.port=19000 \
    -jar "$PROJECT_ROOT/engine/build/quarkus-app/quarkus-run.jar" \
    > "$PROJECT_ROOT/logs/engine.log" 2>&1 &
  local engine_pid=$!

  echo "Starting REST (Port 18080, waiting for Engine gRPC on 19000)..."
  until nc -z localhost 19000 2>/dev/null; do
    if ! kill -0 "$engine_pid" 2>/dev/null; then
      echo "❌ Engine process died unexpectedly. Check logs/engine.log"
      exit 1
    fi
    sleep 1
  done

  # agentengine.auth.jwt.secret is required when auth.enabled=true; add:
  #   -Dagentengine.auth.jwt.secret=<your-secret>
  nohup java --enable-preview \
    -Dquarkus.http.port=18080 \
    -Dagentengine.grpc.host=localhost \
    -Dagentengine.grpc.port=19000 \
    -Dagentengine.auth.enabled="${AGENTENGINE_AUTH_ENABLED:-false}" \
    -jar "$PROJECT_ROOT/interfaces/rest/build/quarkus-app/quarkus-run.jar" \
    > "$PROJECT_ROOT/logs/rest.log" 2>&1 &
  local rest_pid=$!

  echo "Services started. Engine PID: $engine_pid, REST PID: $rest_pid"
  echo "Logs: $PROJECT_ROOT/logs/engine.log, $PROJECT_ROOT/logs/rest.log"

  if [ "$BOOTSTRAP" = true ]; then
    bootstrap_data &
  fi

  wait
}

start_dev() {
  echo "Starting in DEV mode (Monolith)..."
  if [ "$BOOTSTRAP" = true ]; then
    bootstrap_data &
  fi
  cd "$PROJECT_ROOT"
  exec ./gradlew :interfaces:local:quarkusDev
}

cd "$PROJECT_ROOT"
start_mongo
bootstrap_mongo_state

case "$MODE" in
  dev) start_dev ;;
  production) start_production ;;
esac
