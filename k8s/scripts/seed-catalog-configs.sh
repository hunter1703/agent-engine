#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
ENVIRONMENT=${ENVIRONMENT:-$DEFAULT_ENVIRONMENT}
REST_SERVICE_NAME=${REST_SERVICE_NAME:-agent-engine-rest}
REST_READY_PATH=${REST_READY_PATH:-/q/health/ready}
LOCAL_PORT=${LOCAL_PORT:-18080}
MAX_AGENT_PASSES=${MAX_AGENT_PASSES:-10}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/seed-catalog-configs.sh
  ./k8s/scripts/seed-catalog-configs.sh -n agent-engine
  ./k8s/scripts/seed-catalog-configs.sh -e prod

Behavior:
  - Upserts configs/models and configs/agents through the REST API.
  - Uses a temporary local port-forward instead of an extra in-cluster seed pod.
  - Retries agent upserts across multiple passes so dependency-ordered configs converge.
  - Fails with explicit per-file diagnostics when any config still cannot be seeded.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -n|--namespace)
      NAMESPACE=$2
      shift 2
      ;;
    -e|--environment)
      ENVIRONMENT=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

pick_config_files() {
  base_dir=$1
  overlay_dir=$2

  for file in "$base_dir"/*.json; do
    [ -e "$file" ] || break
    name=$(basename "$file")
    if [ -f "$overlay_dir/$name" ]; then
      printf '%s\n' "$overlay_dir/$name"
    else
      printf '%s\n' "$file"
    fi
  done

  if [ -d "$overlay_dir" ]; then
    for file in "$overlay_dir"/*.json; do
      [ -e "$file" ] || break
      name=$(basename "$file")
      if [ ! -f "$base_dir/$name" ]; then
        printf '%s\n' "$file"
      fi
    done
  fi
}

require_command kubectl
require_command curl

if ! kubectl get svc "$REST_SERVICE_NAME" --namespace "$NAMESPACE" >/dev/null 2>&1; then
  echo "Skipping catalog sync because service '$REST_SERVICE_NAME' is not present in namespace '$NAMESPACE'."
  exit 0
fi

PORT_FORWARD_LOG=$(mktemp)
ERROR_DIR=$(mktemp -d)
PENDING_FILE="$ERROR_DIR/pending.txt"

cleanup() {
  if [ -n "${PORT_FORWARD_PID:-}" ] && kill -0 "$PORT_FORWARD_PID" >/dev/null 2>&1; then
    kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
    wait "$PORT_FORWARD_PID" 2>/dev/null || true
  fi
  rm -f "$PORT_FORWARD_LOG"
  rm -rf "$ERROR_DIR"
}
trap cleanup EXIT

request_path() {
  file=$1
  path=$2
  name=$(basename "$file")
  response_file="$ERROR_DIR/$name.response"
  status_file="$ERROR_DIR/$name.status"
  http_code=$(curl -sS -o "$response_file" -w '%{http_code}' -X POST -H 'Content-Type: application/json' --data @"$file" \
    "${REST_BASE_URL}${path}" || true)
  printf '%s' "$http_code" >"$status_file"
  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    rm -f "$response_file" "$status_file"
    return 0
  fi
  return 1
}

print_error() {
  file=$1
  name=$(basename "$file")
  status=$(cat "$ERROR_DIR/$name.status" 2>/dev/null || printf 'unknown')
  body=$(cat "$ERROR_DIR/$name.response" 2>/dev/null || printf '')
  echo "Failed to seed $name (HTTP $status)" >&2
  if [ -n "$body" ]; then
    echo "$body" >&2
  fi
}

kubectl port-forward --namespace "$NAMESPACE" "service/$REST_SERVICE_NAME" "${LOCAL_PORT}:8080" >"$PORT_FORWARD_LOG" 2>&1 &
PORT_FORWARD_PID=$!

REST_BASE_URL="http://127.0.0.1:${LOCAL_PORT}"
until curl -fsS "${REST_BASE_URL}${REST_READY_PATH}" >/dev/null 2>&1; do
  if ! kill -0 "$PORT_FORWARD_PID" >/dev/null 2>&1; then
    cat "$PORT_FORWARD_LOG" >&2
    exit 1
  fi
  sleep 2
done

MODEL_BASE_DIR="$K8S_DIR/../configs/models"
MODEL_OVERLAY_DIR="$K8S_DIR/environments/$ENVIRONMENT/catalog/models"
AGENT_BASE_DIR="$K8S_DIR/../configs/agents"
AGENT_OVERLAY_DIR="$K8S_DIR/environments/$ENVIRONMENT/catalog/agents"

pick_config_files "$MODEL_BASE_DIR" "$MODEL_OVERLAY_DIR" | while IFS= read -r file; do
  [ -n "$file" ] || continue
  if request_path "$file" "/v1/model/upsert"; then
    echo "Seeded model $(basename "$file")"
    continue
  fi
  print_error "$file"
  exit 1
done

: >"$PENDING_FILE"
pick_config_files "$AGENT_BASE_DIR" "$AGENT_OVERLAY_DIR" | while IFS= read -r file; do
  [ -n "$file" ] || continue
  printf '%s\n' "$file" >>"$PENDING_FILE"
done

pass=1
while [ "$pass" -le "$MAX_AGENT_PASSES" ] && [ -s "$PENDING_FILE" ]; do
  next_pending="$ERROR_DIR/pending.next"
  : >"$next_pending"
  pass_success=0

  while IFS= read -r file; do
    [ -n "$file" ] || continue
    if request_path "$file" "/v1/agent/upsert"; then
      echo "Seeded agent $(basename "$file")"
      pass_success=$((pass_success + 1))
      continue
    fi
    printf '%s\n' "$file" >>"$next_pending"
  done <"$PENDING_FILE"

  if [ ! -s "$next_pending" ]; then
    mv "$next_pending" "$PENDING_FILE"
    break
  fi

  if [ "$pass_success" -eq 0 ]; then
    echo "Catalog sync stalled on pass $pass. Remaining failures:" >&2
    while IFS= read -r file; do
      [ -n "$file" ] || continue
      print_error "$file"
    done <"$next_pending"
    exit 1
  fi

  mv "$next_pending" "$PENDING_FILE"
  pass=$((pass + 1))
done

if [ -s "$PENDING_FILE" ]; then
  echo "Catalog sync exhausted $MAX_AGENT_PASSES passes. Remaining failures:" >&2
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    print_error "$file"
  done <"$PENDING_FILE"
  exit 1
fi
