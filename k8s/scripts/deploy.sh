#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

# Color codes using printf-compatible format
BLUE='\033[0;34m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BOLD='\033[1m'
RESET='\033[0m'
RED='\033[0;31m'

# Helper functions for colored output
print_phase() {
  printf "\n${BOLD}${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}\n"
  printf "${BOLD}${BLUE}  %s${RESET}\n" "$1"
  printf "${BOLD}${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}\n"
}

print_step() {
  printf "${CYAN}  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}\n"
  printf "    ${GREEN}→ %s${RESET}\n" "$1"
  printf "${CYAN}  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}\n"
}

print_info() { printf "    ${GREEN}✓${RESET} %s\n" "$1"; }
print_warn() { printf "    ${YELLOW}⚠ %s${RESET}\n" "$1"; }
print_note() { printf "    ${YELLOW}ℹ${RESET} %s\n" "$1"; }

# Signal handler for graceful shutdown
stop_workloads() {
  printf "\n${BOLD}${YELLOW}⚠ Shutdown signal received. Deleting workloads (preserving volumes)...${RESET}\n"
  rm -rf "$STATE_DIR" 2>/dev/null || true

  printf "${CYAN}  → Stopping port-forward...${RESET}\n"
  pkill -f "kubectl.*port-forward.*:${LOCAL_PORT}" 2>/dev/null || true
  
  printf "${CYAN}  → Deleting deployments...${RESET}\n"
  kubectl delete deployment agent-engine-catalog agent-engine-rest agent-engine-knowledge localstack qdrant -n "$NAMESPACE" 2>/dev/null || true

  printf "${CYAN}  → Deleting statefulsets (preserving PVCs)...${RESET}\n"
  kubectl delete statefulset agent-engine-agent mongodb postgres -n "$NAMESPACE" 2>/dev/null || true

  printf "${CYAN}  → Deleting services (except headless for PVC retention)...${RESET}\n"
  kubectl delete service agent-engine-catalog agent-engine-rest agent-engine-agent agent-engine-knowledge qdrant -n "$NAMESPACE" 2>/dev/null || true
  
  printf "${CYAN}  → Deleting configmaps and secrets...${RESET}\n"
  kubectl delete configmap -l app.kubernetes.io/part-of=agent-engine -n "$NAMESPACE" 2>/dev/null || true
  kubectl delete secret -l app.kubernetes.io/part-of=agent-engine -n "$NAMESPACE" 2>/dev/null || true
  
  printf "${BOLD}${GREEN}✓ Workloads deleted. PVCs preserved for next deployment.${RESET}\n"
  printf "${BOLD}${BLUE}Run 'sh k8s/scripts/deploy.sh' to recreate resources.${RESET}\n\n"
  exit 0
}

# Trap Ctrl+C and SIGTERM
trap stop_workloads INT TERM

ENVIRONMENT=$DEFAULT_ENVIRONMENT
NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
ATOMIC=true
LINT=false
DRY_RUN=false
SKIP_INFRA=false
TIMEOUT=$DEFAULT_TIMEOUT
IMAGE_TAG=${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo "dev")}
EXTRA_VALUES_FILES=""
SET_ARGUMENTS=""
LOCAL_PORT=${LOCAL_PORT:-8080}
STATE_DIR=/tmp/agent-engine-deploy-state

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/deploy.sh
  ./k8s/scripts/deploy.sh -e prod

Stages (run concurrently where dependencies allow):
  build + infra services   All start at T=0 in parallel.
  init postgres schema     Starts once postgres is ready.
  seed infra configs       Starts once mongodb is ready.
  global-properties        Starts at T=0 (static ConfigMap, no deps).
  catalog + rest           Start once build and global-properties are done.
  knowledge                Starts once build and global-properties are done.
  agent                    Starts once catalog is ready.
  seed application configs Starts once mongodb, catalog, and rest are ready.

Flags:
  --no-atomic       Disable atomic rollback on Helm failure.
  --skip-infra      Skip infra deploy and seeding (Phases 2-3). Use when MongoDB/Postgres are already running.
  --lint            Run helm lint before deploying (off by default).
  --dry-run         Render release changes without applying them.
  --timeout <d>     Helm timeout (default: 10m).
EOF
  print_common_usage
}

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -e|--environment)
        ENVIRONMENT=$2
        shift 2
        ;;
      -n|--namespace)
        NAMESPACE=$2
        shift 2
        ;;
      -f|--values)
        EXTRA_VALUES_FILES="$EXTRA_VALUES_FILES $2"
        shift 2
        ;;
      --set)
        SET_ARGUMENTS="$SET_ARGUMENTS $2"
        shift 2
        ;;
      --image-tag)
        IMAGE_TAG=$2
        shift 2
        ;;
      --no-atomic)
        ATOMIC=false
        shift
        ;;
      --skip-infra)
        SKIP_INFRA=true
        shift
        ;;
      --lint)
        LINT=true
        shift
        ;;
      --dry-run)
        DRY_RUN=true
        shift
        ;;
      --timeout)
        TIMEOUT=$2
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
}

helm_flags() {
  set -- -e "$ENVIRONMENT" -n "$NAMESPACE" --timeout "$TIMEOUT"
  [ "$ATOMIC" = "false" ] && set -- "$@" --no-atomic
  [ "$LINT"   = "true"  ] && set -- "$@" --lint
  [ "$DRY_RUN" = "true"  ] && set -- "$@" --dry-run
  [ -n "${IMAGE_TAG:-}"  ] && set -- "$@" --image-tag "$IMAGE_TAG"
  if [ -n "${EXTRA_VALUES_FILES:-}" ]; then
    for f in $EXTRA_VALUES_FILES; do set -- "$@" -f "$f"; done
  fi
  if [ -n "${SET_ARGUMENTS:-}" ]; then
    for s in $SET_ARGUMENTS; do set -- "$@" --set "$s"; done
  fi
  printf '%s\n' "$*"
}

# Polls for each named flag file. Exits immediately if the 'failed' sentinel is present.
wait_for() {
  for flag; do
    while [ ! -f "$STATE_DIR/$flag" ]; do
      [ -f "$STATE_DIR/failed" ] && exit 1
      sleep 1
    done
  done
}

fail() {
  touch "$STATE_DIR/failed"
  exit 1
}

job_build() {
  if [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/builds-done"
    return 0
  fi
  print_phase "Building Docker images"
  require_command docker
  TAG=$IMAGE_TAG "$SCRIPT_DIR/build-images.sh" agent catalog rest knowledge || fail
  touch "$STATE_DIR/builds-done"
}

# Declare infra services: "chart:comma-separated-wait-flags:ready-flag"
# To add a new infra service, append one line here. No other changes required.
INFRA_SERVICES="
mongodb::mongodb-ready
postgres::postgres-ready
localstack::localstack-ready
qdrant::qdrant-ready
"

# Deploys a single infra chart, skipping when --skip-infra is set.
# Accepts optional deps so infra services can depend on each other.
job_infra_svc() {
  chart=$1 deps=$2 ready_flag=$3
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/$ready_flag"
    return 0
  fi
  deploy_service "$chart" "$deps" "$ready_flag"
}

job_seed_infra() {
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/infra-seeded"
    return 0
  fi
  wait_for mongodb-ready
  print_phase "Seeding infrastructure configuration"
  sh "$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/infra-seeded"
}

job_init_postgres() {
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/postgres-schema-ready"
    return 0
  fi
  wait_for postgres-ready
  print_phase "Initializing PostgreSQL schema"
  sh "$SCRIPT_DIR/init-postgres-schema.sh" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/postgres-schema-ready"
}

# Deploys a single chart after waiting for its declared dependencies.
# Usage: deploy_service <chart> <comma-separated-wait-flags> <ready-flag>
deploy_service() {
  chart=$1 deps=$2 ready_flag=$3
  # shellcheck disable=SC2046
  wait_for $(printf '%s' "$deps" | tr ',' ' ')
  print_phase "Deploying $chart"
  IMAGES_PREBUILT=true sh "$SCRIPT_DIR/deploy-services.sh" $(helm_flags) "$chart" || fail
  touch "$STATE_DIR/$ready_flag"
}

# Declare app services: "chart:comma-separated-wait-flags:ready-flag"
# To add a new service, append one line here. No other changes required.
APP_SERVICES="
global-properties::global-properties-ready
catalog:global-properties-ready,builds-done:catalog-ready
rest:global-properties-ready,builds-done:rest-ready
knowledge:global-properties-ready,builds-done:knowledge-ready
agent:global-properties-ready,catalog-ready,builds-done:agent-ready
"

# Ready-flags that must all be set before catalog seeding begins.
# Append a service's ready-flag here if seed-catalog calls through it.
CATALOG_DEPS="mongodb-ready,catalog-ready,rest-ready"

job_seed_catalog() {
  if [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/catalog-seeded"
    return 0
  fi
  # shellcheck disable=SC2046
  wait_for $(printf '%s' "$CATALOG_DEPS" | tr ',' ' ')
  print_phase "Seeding application catalog"
  sh "$SCRIPT_DIR/seed-catalog-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/catalog-seeded"
}

parse_args "$@"

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR"
trap 'rm -rf "$STATE_DIR"' EXIT

pids=""

job_build &
pids="$pids $!"

for entry in $INFRA_SERVICES; do
  [ -n "$entry" ] || continue
  chart=$(printf '%s' "$entry" | cut -d: -f1)
  deps=$(printf '%s' "$entry"  | cut -d: -f2)
  flag=$(printf '%s' "$entry"  | cut -d: -f3)
  job_infra_svc "$chart" "$deps" "$flag" &
  pids="$pids $!"
done

job_seed_infra &
pids="$pids $!"

job_init_postgres &
pids="$pids $!"

for entry in $APP_SERVICES; do
  [ -n "$entry" ] || continue
  chart=$(printf '%s' "$entry" | cut -d: -f1)
  deps=$(printf '%s' "$entry"  | cut -d: -f2)
  flag=$(printf '%s' "$entry"  | cut -d: -f3)
  deploy_service "$chart" "$deps" "$flag" &
  pids="$pids $!"
done

job_seed_catalog &
pids="$pids $!"

failed_pids=""
for pid in $pids; do
  wait "$pid" || failed_pids="$failed_pids $pid"
done

if [ -n "${failed_pids# }" ]; then
  printf "\n${BOLD}${RED}✗ Deployment failed. Check output above for details.${RESET}\n" >&2
  exit 1
fi

print_phase "Deployment complete — application is ready"

if [ "$ENVIRONMENT" = "local" ] && [ "$DRY_RUN" != "true" ]; then
  print_step "Clearing existing port-forwards on port ${LOCAL_PORT}"
  pkill -f "kubectl.*port-forward.*:${LOCAL_PORT}" 2>/dev/null || true
  sleep 1
  print_step "Starting port-forward agent-engine-rest:8080 → localhost:${LOCAL_PORT}"
  print_info "REST API available at http://localhost:${LOCAL_PORT}"
  print_note "Press Ctrl+C to stop."
  while true; do
    kubectl port-forward -n "$NAMESPACE" svc/agent-engine-rest "${LOCAL_PORT}:8080" || true
    print_warn "Port-forward dropped, restarting..."
    sleep 2
  done
fi