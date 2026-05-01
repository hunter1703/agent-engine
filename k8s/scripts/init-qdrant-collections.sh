#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
QDRANT_SERVICE=${QDRANT_SERVICE:-qdrant}
QDRANT_PORT=${QDRANT_PORT:-6333}
VECTOR_SIZE=${VECTOR_SIZE:-768}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/init-qdrant-collections.sh
  ./k8s/scripts/init-qdrant-collections.sh -n agent-engine --vector-size 1024

Behavior:
  - Creates the KnowledgeChunk collection in Qdrant if it doesn't exist.
  - Idempotent: checks if collection exists before creating.
  - Uses HTTP REST API (port 6333).

Options:
  -n, --namespace <name>     Kubernetes namespace (default: agent-engine)
  --vector-size <size>       Vector dimension size (default: 768 for nomic-embed-text)
  -h, --help                 Show this help message
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -n|--namespace)
      NAMESPACE=$2
      shift 2
      ;;
    --vector-size)
      VECTOR_SIZE=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

require_command kubectl

echo "Initializing Qdrant collections in namespace $NAMESPACE..."
echo "Vector size: $VECTOR_SIZE"

QDRANT_URL="http://${QDRANT_SERVICE}.${NAMESPACE}.svc.cluster.local:${QDRANT_PORT}"

# Check if KnowledgeChunk collection exists using kubectl run with curl image
echo "Checking if KnowledgeChunk collection exists..."
CHECK_RESULT=$(kubectl run qdrant-check-$$ --rm -i --restart=Never --image=curlimages/curl:latest --namespace="$NAMESPACE" -- \
  curl -s "${QDRANT_URL}/collections/KnowledgeChunk" 2>&1)

if echo "$CHECK_RESULT" | grep -q '"result"'; then
  echo "✓ KnowledgeChunk collection already exists"
else
  echo "Creating KnowledgeChunk collection..."
  CREATE_RESULT=$(kubectl run qdrant-create-$$ --rm -i --restart=Never --image=curlimages/curl:latest --namespace="$NAMESPACE" -- \
    curl -s -X PUT "${QDRANT_URL}/collections/KnowledgeChunk" \
      -H 'Content-Type: application/json' \
      -d "{\"vectors\":{\"size\":${VECTOR_SIZE},\"distance\":\"Cosine\"}}" 2>&1)
  
  if echo "$CREATE_RESULT" | grep -q '"status":"ok"'; then
    echo "✓ KnowledgeChunk collection created"
  else
    echo "✗ Failed to create KnowledgeChunk collection" >&2
    echo "Response: $CREATE_RESULT" >&2
    exit 1
  fi
fi

echo "Qdrant collections initialized successfully"
