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
SYNC_CONFIGS=true
TIMEOUT=$DEFAULT_TIMEOUT
IMAGE_TAG=${IMAGE_TAG:-latest}
EXTRA_VALUES_FILES=""
SET_ARGUMENTS=""

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/deploy.sh
  ./k8s/scripts/deploy.sh -n agent-engine-prod
  ./k8s/scripts/deploy.sh --skip-build

Behavior:
  1. Deploy infra workloads.
  2. Upsert infra config into MongoDB.
  3. Deploy runtime, core, and rest.
  4. Upsert model and agent catalog through REST.

Flags:
  --no-wait          Do not wait for rollouts/hooks to complete.
  --no-atomic        Disable atomic rollback on failure.
  --skip-lint        Skip helm lint before deploy.
  --skip-build       Skip automatic Docker image builds for runtime/core/rest.
  --dry-run          Render the release changes without applying them.
  --timeout <d>      Helm timeout (default: 10m).
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

run_stage() {
  script_name=$1
  include_build_flag=$2
  set -- -e "$ENVIRONMENT" -n "$NAMESPACE" --timeout "$TIMEOUT"

  if [ "$WAIT" = "false" ]; then
    set -- "$@" --no-wait
  fi
  if [ "$ATOMIC" = "false" ]; then
    set -- "$@" --no-atomic
  fi
  if [ "$LINT" = "false" ]; then
    set -- "$@" --skip-lint
  fi
  if [ "$DRY_RUN" = "true" ]; then
    set -- "$@" --dry-run
  fi
  if [ "$include_build_flag" = "true" ] && [ "$BUILD_IMAGES" = "false" ]; then
    set -- "$@" --skip-build
  fi
  if [ -n "${IMAGE_TAG:-}" ]; then
    set -- "$@" --image-tag "$IMAGE_TAG"
  fi

  if [ -n "${EXTRA_VALUES_FILES:-}" ]; then
    for values_file in $EXTRA_VALUES_FILES; do
      set -- "$@" -f "$values_file"
    done
  fi

  if [ -n "${SET_ARGUMENTS:-}" ]; then
    for set_argument in $SET_ARGUMENTS; do
      set -- "$@" --set "$set_argument"
    done
  fi

  sh "$SCRIPT_DIR/$script_name" "$@"
}

parse_args "$@"

run_stage deploy-infra.sh false

if [ "$DRY_RUN" != "true" ]; then
  sh "$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
fi

run_stage deploy-services.sh true

if [ "$DRY_RUN" != "true" ]; then
  sh "$SCRIPT_DIR/seed-catalog-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
fi
