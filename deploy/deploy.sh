#!/bin/bash
set -e

MODE=$1
BOOTSTRAP=false
CLEAN=false

if [ -z "$MODE" ] || [[ "$MODE" != "dev" && "$MODE" != "production" ]]; then
    echo "Usage: ./deploy/deploy.sh [dev|production] [--bootstrap] [--clean]"
    exit 1
fi

shift
for arg in "$@"; do
    case "$arg" in
        --bootstrap) BOOTSTRAP=true ;;
        --clean) CLEAN=true ;;
    esac
done

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

# 1. Start MongoDB via docker-compose automatically
echo "Starting MongoDB infrastructure..."
cd "$PROJECT_ROOT"
docker-compose -f deploy/docker-compose.yaml up -d mongodb

# 2. Define Bootstrap Function
bootstrap_data() {
    API_URL="http://localhost:18080"

    
    echo "Waiting for REST API to become responsive on $API_URL..."
    until curl -s -m 10 "$API_URL/q/openapi" > /dev/null; do
        sleep 2
    done
    echo "API is responsive. Bootstrapping Agents and Models!"
    
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
    echo "✅ Bootstrap complete."
}

# 3. Launch the requested environment
if [ "$MODE" == "dev" ]; then
    echo "Starting in DEV mode (Monolith)..."
    
    # Run bootstrap asynchronously if requested
    if [ "$BOOTSTRAP" = true ]; then
        bootstrap_data &
    fi

    cd "$PROJECT_ROOT"
    ./gradlew :interfaces:local:quarkusDev
    exit 0
fi

if [ "$MODE" == "production" ]; then
    echo "Starting in PRODUCTION mode (Separate Services)..."
    
    echo "Building ubers-jars..."
    cd "$PROJECT_ROOT"
    if [ "$CLEAN" = true ]; then
        ./gradlew clean :engine:quarkusBuild :interfaces:rest:quarkusBuild -x test -x spotlessCheck
    else
        ./gradlew :engine:quarkusBuild :interfaces:rest:quarkusBuild -x test -x spotlessCheck
    fi

    # Create logs directory if it doesn't exist
    mkdir -p "$PROJECT_ROOT/logs"

    echo "Starting Engine (Port 18081, gRPC 19000)..."
    nohup java --enable-preview -Dquarkus.http.port=18081 -Dquarkus.grpc.server.port=19000 -jar engine/build/quarkus-app/quarkus-run.jar > "$PROJECT_ROOT/logs/engine.log" 2>&1 &

    ENGINE_PID=$!

    echo "Starting REST (Port 18080)..."
    # Wait a bit for engine to start
    sleep 5
    nohup java --enable-preview -Dquarkus.http.port=18080 -Dagentengine.grpc.host=localhost -Dagentengine.grpc.port=19000 -jar interfaces/rest/build/quarkus-app/quarkus-run.jar > "$PROJECT_ROOT/logs/rest.log" 2>&1 &

    REST_PID=$!

    echo "Services started. Engine PID: $ENGINE_PID, REST PID: $REST_PID"
    echo "Logs are being written to logs/engine.log and logs/rest.log"

    # Run bootstrap asynchronously if requested
    if [ "$BOOTSTRAP" = true ]; then
        bootstrap_data &
    fi
    
    # Wait for the java background processes so the script doesn't exit immediately 
    wait
    exit 0
fi
