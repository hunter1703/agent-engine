#!/usr/bin/env bash
set -euo pipefail

FORCE_BUILD=false

if [ "${1:-}" = "--force" ]; then
  FORCE_BUILD=true
  shift
fi

CONFIG_DIR=${1:-./configs}
CONFIG_DIR=$(cd "$CONFIG_DIR" && pwd)

MONGO_IMAGE=${MONGO_IMAGE:-agent-engine-mongo:latest}
CONTAINER_NAME=${CONTAINER_NAME:-agent-engine-mongodb}
MONGO_PORT=${MONGO_PORT:-27000}
DB_NAME=${CONFIG_DB_NAME:-AGENT_ENGINE}

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if ! docker image inspect "$MONGO_IMAGE" >/dev/null 2>&1 || [ "$FORCE_BUILD" = true ]; then
  docker build -t "$MONGO_IMAGE" -f "$SCRIPT_DIR/Dockerfile.mongodb" "$SCRIPT_DIR"
fi

if ! docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
  docker run -d --name "$CONTAINER_NAME" -p "$MONGO_PORT":27017 "$MONGO_IMAGE"
else
  docker start "$CONTAINER_NAME" >/dev/null
fi

upsert_configs() {
  local dir=$1
  local collection=$2
  local tmp_file

  if [ ! -d "$dir" ]; then
    return
  fi

  tmp_file=$(mktemp)
  trap 'rm -f "$tmp_file"' RETURN

  for file in "$dir"/*.json; do
    [ -e "$file" ] || continue
    local base
    base=$(basename "$file")
    local id=${base%.*}

    jq --arg id "$id" '. + {"_id": $id}' "$file" >> "$tmp_file"
  done

  if [ -s "$tmp_file" ]; then
    docker exec -i "$CONTAINER_NAME" mongoimport \
      --db "$DB_NAME" \
      --collection "$collection" \
      --mode=upsert \
      --upsertFields=_id \
      --file /dev/stdin < "$tmp_file"
  fi
}

upsert_configs "$CONFIG_DIR/models" "Model"
upsert_configs "$CONFIG_DIR/agents" "Agent"

echo "MongoDB ready on localhost:$MONGO_PORT. Imported JSON configs from $CONFIG_DIR."
