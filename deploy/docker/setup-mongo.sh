#!/usr/bin/env bash
set -euo pipefail

force=false
config_dir="./configs"

if [[ $# -gt 0 ]]; then
  if [[ "$1" == "--force" ]]; then
    force=true
    shift
  fi
  if [[ $# -gt 0 ]]; then
    config_dir="$1"
    shift
  fi
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required to set up MongoDB" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to load configs" >&2
  exit 1
fi

MONGODB_CONNECTION_STRING=${MONGODB_CONNECTION_STRING:-mongodb://localhost:27002}
CONFIG_DB_NAME=${CONFIG_DB_NAME:-AGENT_ENGINE}

port=$(echo "$MONGODB_CONNECTION_STRING" | sed -E 's|.*:([0-9]+).*|\1|')
if [[ -z "$port" || "$port" == "$MONGODB_CONNECTION_STRING" ]]; then
  port=27002
fi

image="agent-engine-mongodb"
container="agent-engine-mongodb"

if [[ "$force" == "true" ]]; then
  docker rm -f "$container" >/dev/null 2>&1 || true
fi

if [[ "$force" == "true" ]] || ! docker image inspect "$image" >/dev/null 2>&1; then
  docker build -t "$image" -f "$(dirname "$0")/Dockerfile.mongodb" "$(dirname "$0")"
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
  if docker ps -a --format '{{.Names}}' | grep -q "^${container}$"; then
    docker start "$container" >/dev/null
  else
    docker run -d --name "$container" -p "${port}:27017" "$image" >/dev/null
  fi
fi

for _ in {1..20}; do
  if docker exec "$container" mongosh --quiet --eval 'db.runCommand({ ping: 1 })' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! docker exec "$container" mongosh --quiet --eval 'db.runCommand({ ping: 1 })' >/dev/null 2>&1; then
  echo "MongoDB did not start successfully" >&2
  exit 1
fi

import_dir() {
  local dir="$1"
  local collection="$2"
  if [[ ! -d "$dir" ]]; then
    echo "Skipping missing directory: $dir"
    return
  fi
  for file in "$dir"/*.json; do
    [[ -f "$file" ]] || continue
    local id
    id=$(basename "$file" .json)
    jq --arg id "$id" '. + {"_id": $id}' "$file" \
      | docker exec -i "$container" mongoimport --db "$CONFIG_DB_NAME" \
          --collection "$collection" --mode=upsert --upsertFields _id --file /dev/stdin >/dev/null
  done
}

import_dir "$config_dir/agents" "Agent"
import_dir "$config_dir/models" "Model"

echo "MongoDB setup complete on port $port"
