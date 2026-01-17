#!/usr/bin/env bash
set -euo pipefail

PLUGIN_NAME=${1:-shell-agent}

./gradlew :engine:jar
./gradlew -p "plugins/${PLUGIN_NAME}" build
./gradlew syncPlugins

echo "Plugin built and synced: ${PLUGIN_NAME}"
