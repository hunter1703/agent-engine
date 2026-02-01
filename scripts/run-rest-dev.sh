#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
mongo_args=${*:-"$root_dir/configs"}

export MONGODB_CONNECTION_STRING=${MONGODB_CONNECTION_STRING:-mongodb://localhost:27002}
export CONFIG_DB_NAME=${CONFIG_DB_NAME:-AGENT_ENGINE}
export PLUGIN_DIR=${PLUGIN_DIR:-"$root_dir/plugins"}

./gradlew deployEngine -PmongoArgs="$mongo_args"
