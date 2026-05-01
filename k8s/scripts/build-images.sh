#!/usr/bin/env sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
TAG=${TAG:-$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || echo "dev")}
REGISTRY_PREFIX=${REGISTRY_PREFIX:-}
PUSH=false

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/build-images.sh
  ./k8s/scripts/build-images.sh agent catalog rest knowledge

Behavior:
  - Builds the Docker images expected by the Kubernetes charts.
  - Works for local Docker Desktop Kubernetes and remote registries.

Environment:
  TAG=latest                   Image tag to build
  REGISTRY_PREFIX=ghcr.io/me   Optional registry/repository prefix
  PUSH=true                    Push built images after build
EOF
}

normalize_image() {
  component=$1
  if [ -n "$REGISTRY_PREFIX" ]; then
    echo "${REGISTRY_PREFIX}/agent-engine/${component}:${TAG}"
  else
    echo "agent-engine/${component}:${TAG}"
  fi
}

build_component() {
  component=$1
  module=$2
  image=$(normalize_image "$component")
  docker build --build-arg SERVICE_MODULE="$module" -t "$image" -f "$ROOT_DIR/docker/Dockerfile" "$ROOT_DIR"
  if [ "$PUSH" = "true" ]; then
    docker push "$image"
  fi
  echo "Built $image"
}

build_quarkus_apps() {
  tasks=""
  for component in "$@"; do
    case "$component" in
      agent) tasks="$tasks :agent:quarkusBuild" ;;
      catalog) tasks="$tasks :catalog:quarkusBuild" ;;
      rest) tasks="$tasks :interfaces:rest:quarkusBuild" ;;
      knowledge) tasks="$tasks :knowledge:quarkusBuild" ;;
      *)
        echo "Unknown component: $component" >&2
        exit 1
        ;;
    esac
  done

  if [ -n "${tasks# }" ]; then
    (cd "$ROOT_DIR" && ./gradlew $tasks -x test --parallel)
  fi
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Required command not found: docker" >&2
  exit 1
fi

if [ "$#" -eq 0 ]; then
  set -- agent catalog rest knowledge
fi

build_quarkus_apps "$@"

pids=""
for component in "$@"; do
  case "$component" in
    agent) build_component agent agent & pids="$pids $!" ;;
    catalog)    build_component catalog catalog & pids="$pids $!" ;;
    rest)    build_component rest interfaces/rest & pids="$pids $!" ;;
    knowledge)    build_component knowledge knowledge & pids="$pids $!" ;;
    *)
      echo "Unknown component: $component" >&2
      exit 1
      ;;
  esac
done

failed=""
for pid in $pids; do
  wait "$pid" || failed="$failed $pid"
done
if [ -n "${failed# }" ]; then
  echo "One or more image builds failed" >&2
  exit 1
fi
