#!/bin/bash
set -e

MODE=$1
BOOTSTRAP=true
BUILD=false
CLEAN=false

if [ -z "$MODE" ] || [[ "$MODE" != "dev" && "$MODE" != "production" ]]; then
    echo "Usage: ./deploy/deploy.sh [dev|production] [--build] [--clean] [--no-bootstrap]"
    exit 1
fi

shift
for arg in "$@"; do
    case "$arg" in
        --no-bootstrap) BOOTSTRAP=false ;;
        --build) BUILD=true ;;
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
                curl -s -X POST "$API_URL/v1/model/upsert" \
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
                curl -s -X POST "$API_URL/v1/agent/agent/upsert" \
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
    
    cd "$PROJECT_ROOT"
    mkdir -p "$PROJECT_ROOT/logs"

    if [ "$CLEAN" = true ]; then
        echo "Building ubers-jars in parallel (clean)..."
        ./gradlew :engine:clean :engine:quarkusBuild -x test -x spotlessCheck > "$PROJECT_ROOT/logs/build-engine.log" 2>&1 &
        ENGINE_BUILD_PID=$!
        ./gradlew :interfaces:rest:clean :interfaces:rest:quarkusBuild -x test -x spotlessCheck > "$PROJECT_ROOT/logs/build-rest.log" 2>&1 &
        REST_BUILD_PID=$!
        FAILED=0
        wait $ENGINE_BUILD_PID || FAILED=1
        wait $REST_BUILD_PID || FAILED=1
        if [ $FAILED -ne 0 ]; then
            echo "Build failed. Check logs/build-engine.log and logs/build-rest.log"
            exit 1
        fi
    elif [ "$BUILD" = true ]; then
        echo "Building ubers-jars in parallel..."
        ./gradlew :engine:quarkusBuild -x test -x spotlessCheck > "$PROJECT_ROOT/logs/build-engine.log" 2>&1 &
        ENGINE_BUILD_PID=$!
        ./gradlew :interfaces:rest:quarkusBuild -x test -x spotlessCheck > "$PROJECT_ROOT/logs/build-rest.log" 2>&1 &
        REST_BUILD_PID=$!
        FAILED=0
        wait $ENGINE_BUILD_PID || FAILED=1
        wait $REST_BUILD_PID || FAILED=1
        if [ $FAILED -ne 0 ]; then
            echo "Build failed. Check logs/build-engine.log and logs/build-rest.log"
            exit 1
        fi
    else
        echo "Skipping build (use --build or --clean to rebuild)..."
    fi

    echo "Starting Engine (Port 18081, gRPC 19000)..."
    nohup java --enable-preview -Dquarkus.http.port=18081 -Dquarkus.grpc.server.port=19000 -jar engine/build/quarkus-app/quarkus-run.jar > "$PROJECT_ROOT/logs/engine.log" 2>&1 &

    ENGINE_PID=$!

    echo "Starting REST (Port 18080, waiting for Engine gRPC on 19000)..."
    until nc -z localhost 19000 2>/dev/null; do
        sleep 0.5
    done
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
