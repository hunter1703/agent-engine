#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
ENVIRONMENT=${ENVIRONMENT:-$DEFAULT_ENVIRONMENT}
REST_SERVICE_NAME=${REST_SERVICE_NAME:-agent-engine-rest}
REST_READY_PATH=${REST_READY_PATH:-/q/health/ready}
LOCAL_PORT=${LOCAL_PORT:-18080}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/seed-catalog-configs.sh
  ./k8s/scripts/seed-catalog-configs.sh -n agent-engine
  ./k8s/scripts/seed-catalog-configs.sh -e prod

Behavior:
  - Upserts configs/models and configs/agents through the REST API.
  - Uses a temporary local port-forward instead of an extra in-cluster seed pod.
  - Topologically sorts agent configs (children before parents) so each upsert
    succeeds on the first attempt without retries.
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

# Sort agent file paths (one per line from $1) in topological order so that
# every agent's subAgentIds are seeded before the agent itself.  Uses repeated
# passes over a "remaining" set, emitting any file whose declared dependencies
# are already in the resolved set, until all files are emitted.  Files with no
# subAgentIds (leaves) are emitted first; root agents last.
topo_sort_agents() {
  input_file=$1
  resolved=$(mktemp)
  output=$(mktemp)
  remaining=$(mktemp)
  cp "$input_file" "$remaining"

  while [ -s "$remaining" ]; do
    next=$(mktemp)
    progress=0

    while IFS= read -r file; do
      [ -n "$file" ] || continue

      # Extract declared sub-agent IDs from "subAgentIds": [ ... ]
      deps=$(awk '
        /"subAgentIds"/ { p=1; next }
        p && match($0, /"[a-zA-Z][^"]*"/) { print substr($0, RSTART+1, RLENGTH-2) }
        p && /\]/ { p=0 }
      ' "$file")

      all_resolved=1
      for dep in $deps; do
        grep -qxF "$dep" "$resolved" || { all_resolved=0; break; }
      done

      if [ "$all_resolved" -eq 1 ]; then
        printf '%s\n' "$file" >>"$output"
        id=$(sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$file" | head -1)
        [ -n "$id" ] && printf '%s\n' "$id" >>"$resolved"
        progress=$((progress + 1))
      else
        printf '%s\n' "$file" >>"$next"
      fi
    done <"$remaining"

    mv "$next" "$remaining"

    if [ "$progress" -eq 0 ]; then
      # No progress — circular or unresolvable deps; append remaining as-is
      cat "$remaining" >>"$output"
      break
    fi
  done

  cat "$output"
  rm -f "$resolved" "$output" "$remaining"
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

SORTED_FILE="$ERROR_DIR/sorted.txt"
topo_sort_agents "$PENDING_FILE" >"$SORTED_FILE"

while IFS= read -r file; do
  [ -n "$file" ] || continue
  if request_path "$file" "/v1/agent/upsert"; then
    echo "Seeded agent $(basename "$file")"
  else
    print_error "$file"
    exit 1
  fi
done <"$SORTED_FILE"
