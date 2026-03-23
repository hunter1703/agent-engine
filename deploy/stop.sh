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
echo "Stopping Docker containers..."
cd "$PROJECT_ROOT"
docker-compose -f deploy/docker-compose.yaml down 2>/dev/null || true

# 3. Clean up log files (optional)
if [ "$1" = "--clean" ]; then
  echo "Cleaning up log files..."
  rm -f "$PROJECT_ROOT/logs/*.log"
fi

echo "✅ All services stopped."
echo ""
echo "To start services:"
echo "  Docker:       ./deploy/deploy.sh docker"
echo "  Production:   ./deploy/deploy.sh production"
echo "  Development:  ./deploy/deploy.sh dev"
