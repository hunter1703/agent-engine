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
  
  printf "${CYAN}  → Stopping port-forward...${RESET}\n"
  pkill -f "kubectl.*port-forward.*:${LOCAL_PORT}" 2>/dev/null || true
  
  printf "${CYAN}  → Deleting deployments...${RESET}\n"
  kubectl delete deployment agent-engine-core agent-engine-rest localstack -n "$NAMESPACE" 2>/dev/null || true
  
  printf "${CYAN}  → Deleting statefulsets (preserving PVCs)...${RESET}\n"
  kubectl delete statefulset agent-engine-runtime mongodb postgres -n "$NAMESPACE" 2>/dev/null || true
  
  printf "${CYAN}  → Deleting services (except headless for PVC retention)...${RESET}\n"
  kubectl delete service agent-engine-core agent-engine-rest agent-engine-runtime -n "$NAMESPACE" 2>/dev/null || true
  
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

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/deploy.sh
  ./k8s/scripts/deploy.sh -e prod

Phases:
  1. Build Docker images for runtime, core, and rest.
  2. Deploy infrastructure workloads (MongoDB, Postgres).
  3. Seed infrastructure configuration into MongoDB (Pekko, SQL, microservices, default model).
     Also initializes the PostgreSQL Pekko journal schema (CREATE TABLE IF NOT EXISTS).
  4. Deploy application workloads (runtime, core, rest).
  5. Seed application catalog (models, agents) through the REST API.
  6. [local environment only] Port-forward the REST service to localhost.

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

parse_args "$@"

# ── Phase 1: Build ────────────────────────────────────────────────────────────
if [ "$DRY_RUN" != "true" ]; then
  print_phase "Phase 1: Building Docker images"
  require_command docker
  TAG=$IMAGE_TAG "$SCRIPT_DIR/build-images.sh" runtime core rest
else
  print_phase "Phase 1: Skipping image build (dry-run)"
fi

# ── Phase 2: Deploy infrastructure ───────────────────────────────────────────
if [ "$SKIP_INFRA" = "true" ]; then
  print_phase "Phase 2: Skipping infrastructure deploy (--skip-infra)"
else
  print_phase "Phase 2: Deploying infrastructure workloads"
  # shellcheck disable=SC2046
  sh "$SCRIPT_DIR/deploy-infra.sh" $(helm_flags)
fi

# ── Phase 3: Seed infrastructure configuration ───────────────────────────────
if [ "$SKIP_INFRA" = "true" ]; then
  print_phase "Phase 3: Skipping infrastructure seeding (--skip-infra)"
elif [ "$DRY_RUN" != "true" ]; then
  print_phase "Phase 3: Seeding infrastructure configuration"
  sh "$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
fi

# ── Phase 4: Deploy application workloads ────────────────────────────────────
print_phase "Phase 4: Deploying application workloads"
print_step "Linting and deploying service charts"
# shellcheck disable=SC2046
sh "$SCRIPT_DIR/deploy-services.sh" $(helm_flags)

# ── Phase 5: Seed application catalog ────────────────────────────────────────
print_phase "Phase 5: Seeding application catalog"
if [ "$DRY_RUN" != "true" ]; then
  sh "$SCRIPT_DIR/seed-catalog-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
fi

# ── Phase 6: Port-forward ────────────────────────────────────────────────────
if [ "$ENVIRONMENT" = "local" ] && [ "$DRY_RUN" != "true" ]; then
  print_phase "Phase 6: Port-forward"
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