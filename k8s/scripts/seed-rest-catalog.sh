#!/usr/bin/env sh

set -eu

REST_BASE_URL=${REST_BASE_URL:?REST_BASE_URL is required}
READY_PATH=${READY_PATH:-/q/health/ready}

until curl -fsS "${REST_BASE_URL}${READY_PATH}" >/dev/null; do
  echo "Waiting for REST service at ${REST_BASE_URL}${READY_PATH}..."
  sleep 5
done

for file in /seed/model__*.json; do
  [ -e "$file" ] || break
  curl -fsS -X POST -H 'Content-Type: application/json' --data @"$file" "${REST_BASE_URL}/v1/model/upsert" >/dev/null
  echo "Seeded model $(basename "$file")"
done

for file in /seed/agent__*.json; do
  [ -e "$file" ] || break
  curl -fsS -X POST -H 'Content-Type: application/json' --data @"$file" "${REST_BASE_URL}/v1/agent/upsert" >/dev/null
  echo "Seeded agent $(basename "$file")"
done
