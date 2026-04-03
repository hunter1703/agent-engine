#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

ENVIRONMENT=$DEFAULT_ENVIRONMENT
NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
WAIT=true
ATOMIC=true
LINT=true
DRY_RUN=false
BUILD_IMAGES=true
SKIP_SEED_INFRA=false
SKIP_SEED_CATALOG=false
TIMEOUT=$DEFAULT_TIMEOUT
IMAGE_TAG=${IMAGE_TAG:-latest}
EXTRA_VALUES_FILES=""
SET_ARGUMENTS=""
LOCAL=false
LOCAL_PORT=${LOCAL_PORT:-8080}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/deploy.sh
  ./k8s/scripts/deploy.sh --skip-build --skip-seed-infra --skip-seed-catalog

Phases:
  1. Build Docker images for runtime, core, and rest.
  2. Deploy infrastructure workloads (MongoDB, Postgres).
  3. Seed infrastructure configuration into MongoDB (Pekko, SQL, microservices, default model).
     Also initializes the PostgreSQL Pekko journal schema (CREATE TABLE IF NOT EXISTS).
  4. Deploy application workloads (runtime, core, rest).
  5. Seed application catalog (models, agents) through the REST API.
  6. [--local only] Port-forward the REST service to localhost.

Flags:
  --skip-build          Skip Docker image builds (step 1). Use when images are pre-built.
  --skip-seed-infra     Skip infra config seeding (step 3). Use on re-deploys when
                        infra config is already present and unchanged.
  --skip-seed-catalog   Skip catalog seeding (step 5). Use on re-deploys when
                        agent/model catalog is already present and unchanged.
  --local               After deploy, port-forward agent-engine-rest to localhost.
                        Set LOCAL_PORT to override the local port (default: 8080).
  --no-wait             Do not wait for rollouts to complete.
  --no-atomic           Disable atomic rollback on Helm failure.
  --skip-lint           Skip helm lint before deploy.
  --dry-run             Render release changes without applying them (implies --skip-build).
  --timeout <d>         Helm timeout (default: 10m).
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
      --no-wait)
        WAIT=false
        shift
        ;;
      --no-atomic)
        ATOMIC=false
        shift
        ;;
      --skip-lint)
        LINT=false
        shift
        ;;
      --skip-build)
        BUILD_IMAGES=false
        shift
        ;;
      --skip-seed-infra)
        SKIP_SEED_INFRA=true
        shift
        ;;
      --skip-seed-catalog)
        SKIP_SEED_CATALOG=true
        shift
        ;;
      --dry-run)
        DRY_RUN=true
        shift
        ;;
      --local)
        LOCAL=true
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
  set -- -e "$ENVIRONMENT" -n "$NAMESPACE" --timeout "$TIMEOUT" --skip-build
  [ "$WAIT"   = "false" ] && set -- "$@" --no-wait
  [ "$ATOMIC" = "false" ] && set -- "$@" --no-atomic
  [ "$LINT"   = "false" ] && set -- "$@" --skip-lint
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
if [ "$DRY_RUN" != "true" ] && [ "$BUILD_IMAGES" = "true" ]; then
  echo "==> Phase 1: Building Docker images"
  require_command docker
  TAG=$IMAGE_TAG "$SCRIPT_DIR/build-images.sh" runtime core rest
else
  echo "==> Phase 1: Skipping image build"
fi

# ── Phase 2: Deploy infrastructure ───────────────────────────────────────────
echo "==> Phase 2: Deploying infrastructure workloads"
# shellcheck disable=SC2046
sh "$SCRIPT_DIR/deploy-infra.sh" $(helm_flags)

# ── Phase 3: Seed infrastructure configuration ───────────────────────────────
if [ "$DRY_RUN" != "true" ] && [ "$SKIP_SEED_INFRA" != "true" ]; then
  echo "==> Phase 3: Seeding infrastructure configuration"
  sh "$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
else
  echo "==> Phase 3: Skipping infra config seeding"
fi

# ── Phase 4: Deploy application workloads ────────────────────────────────────
echo "==> Phase 4: Deploying application workloads"
# shellcheck disable=SC2046
sh "$SCRIPT_DIR/deploy-services.sh" $(helm_flags)

# ── Phase 5: Seed application catalog ────────────────────────────────────────
if [ "$DRY_RUN" != "true" ] && [ "$SKIP_SEED_CATALOG" != "true" ]; then
  echo "==> Phase 5: Seeding application catalog"
  sh "$SCRIPT_DIR/seed-catalog-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
else
  echo "==> Phase 5: Skipping catalog seeding"
fi

# ── Phase 6: Local port-forward ───────────────────────────────────────────────
if [ "$LOCAL" = "true" ] && [ "$DRY_RUN" != "true" ]; then
  echo "==> Phase 6: Port-forwarding agent-engine-rest:8080 → localhost:${LOCAL_PORT}"
  echo "    REST API available at http://localhost:${LOCAL_PORT}"
  echo "    Press Ctrl+C to stop."
  while true; do
    kubectl port-forward -n "$NAMESPACE" svc/agent-engine-rest "${LOCAL_PORT}:8080" || true
    echo "    Port-forward dropped, restarting..."
    sleep 2
  done
fi
