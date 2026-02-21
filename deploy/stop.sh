#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

echo "Stopping Agent Engine services..."

# 1. Kill Java processes
# production mode uses quarkus-run.jar
# dev mode uses quarkusDev
pkill -f "quarkus-run.jar" || true
pkill -f "quarkusDev" || true
pkill -f "GradleDaemon" || true

# 2. Stop Docker infrastructure
echo "Stopping MongoDB infrastructure..."
cd "$PROJECT_ROOT"
docker-compose -f deploy/docker-compose.yaml down

echo "✅ All services stopped."
