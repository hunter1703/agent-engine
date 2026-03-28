#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
ENVIRONMENT=${ENVIRONMENT:-$DEFAULT_ENVIRONMENT}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/seed-configs.sh
  ./k8s/scripts/seed-configs.sh -n agent-engine

Behavior:
  - Upserts configs/infra into INFRA.InfraConfig.
  - Upserts configs/models and configs/agents through the REST API.
  - Runs the infra phase first, then the catalog phase.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -n|--namespace)
      NAMESPACE=$2
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

"$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
"$SCRIPT_DIR/seed-catalog-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE"
