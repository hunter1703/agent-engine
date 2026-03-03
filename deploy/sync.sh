#!/bin/bash
set -e

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
API_URL="${AGENT_ENGINE_API_URL:-http://localhost:18080}"

echo "Syncing configurations to $API_URL..."

# 1. Sync Models
if [ -d "$PROJECT_ROOT/configs/models" ]; then
    echo "Deploying Models..."
    for file in "$PROJECT_ROOT"/configs/models/*.json; do
        if [ -f "$file" ]; then
            echo "  - $(basename "$file")"
            curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/v1/model/upsert" \
                 -H "Content-Type: application/json" \
                 -d @"$file" | grep -q "200" || echo "    ❌ Failed to sync $(basename "$file")"
        fi
    done
else
    echo "⚠️  No models directory found at $PROJECT_ROOT/configs/models"
fi

# 2. Sync Agents
if [ -d "$PROJECT_ROOT/configs/agents" ]; then
    echo "Deploying Agents..."
    for file in "$PROJECT_ROOT"/configs/agents/*.json; do
        if [ -f "$file" ]; then
            echo "  - $(basename "$file")"
            curl -s -o /dev/null -w "%{http_code}" -X POST "$API_URL/v1/agent/agent/upsert" \
                 -H "Content-Type: application/json" \
                 -d @"$file" | grep -q "200" || echo "    ❌ Failed to sync $(basename "$file")"
        fi
    done
else
    echo "⚠️  No agents directory found at $PROJECT_ROOT/configs/agents"
fi

echo "✅ Sync complete."
