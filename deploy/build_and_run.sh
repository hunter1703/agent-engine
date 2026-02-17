#!/bin/bash
set -e

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

echo "Building Quarkus application..."
cd "$PROJECT_ROOT"
./gradlew clean build buildPlugins -x test

echo "Building and starting Docker containers..."
docker compose up --build -d

echo "Deployment complete."
echo "API is available at http://localhost:8080/v1/agent"
echo "MongoDB is running on localhost:27027 (internal: 27017)"
