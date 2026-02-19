#!/bin/bash
set -e

MODE=$1

if [ -z "$MODE" ]; then
    echo "Usage: ./deploy/deploy.sh [dev|production|bootstrap]"
    exit 1
fi

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

if [ "$MODE" == "dev" ]; then
    echo "Starting in DEV mode (Monolith)..."
    cd "$PROJECT_ROOT"
    ./gradlew :interfaces:local:quarkusDev
    exit 0
fi

if [ "$MODE" == "production" ]; then
    echo "Starting in PRODUCTION mode (Separate Services)..."
    
    echo "Building ubers-jars..."
    cd "$PROJECT_ROOT"
    ./gradlew clean :engine:quarkusBuild :interfaces:rest:quarkusBuild -x test -x spotlessCheck

    echo "Starting Engine (Port 8081, gRPC 9000)..."
    java --enable-preview -Dquarkus.http.port=8081 -Dquarkus.grpc.server.port=9000 -jar engine/build/quarkus-app/quarkus-run.jar &
    ENGINE_PID=$!

    echo "Starting REST (Port 8080)..."
    # Wait a bit for engine to start
    sleep 5
    java --enable-preview -Dquarkus.http.port=8080 -jar interfaces/rest/build/quarkus-app/quarkus-run.jar &

    REST_PID=$!

    echo "Services started. Engine PID: $ENGINE_PID, REST PID: $REST_PID"
    exit 0
fi

if [ "$MODE" == "bootstrap" ]; then
    echo "Bootstrapping Agents and Models..."
    
    API_URL="http://localhost:8080"
    
    # Bootstrap Models
    if [ -d "$PROJECT_ROOT/configs/models" ]; then
        echo "Deploying Models..."
        for file in "$PROJECT_ROOT"/configs/models/*.json; do
            if [ -f "$file" ]; then
                echo "  - $(basename "$file")"
                curl -s -X POST "$API_URL/v1/model" \
                     -H "Content-Type: application/json" \
                     -d @"$file" > /dev/null
            fi
        done
    fi

    # Bootstrap Agents
    if [ -d "$PROJECT_ROOT/configs/agents" ]; then
        echo "Deploying Agents..."
        for file in "$PROJECT_ROOT"/configs/agents/*.json; do
            if [ -f "$file" ]; then
                echo "  - $(basename "$file")"
                curl -s -X POST "$API_URL/v1/agent/agent" \
                     -H "Content-Type: application/json" \
                     -d @"$file" > /dev/null
            fi
        done
    fi
    
    echo "Bootstrap complete."
    exit 0
fi

echo "Invalid mode: $MODE. Use 'dev' or 'production'."
exit 1
