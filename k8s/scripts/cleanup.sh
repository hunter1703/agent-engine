#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
DELETE_NAMESPACE=false
DELETE_VOLUMES=true

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/cleanup.sh
  ./k8s/scripts/cleanup.sh runtime core rest
  ./k8s/scripts/cleanup.sh --delete-namespace
  ./k8s/scripts/cleanup.sh --keep-volumes

Behavior:
  - Removes the requested releases in reverse dependency order.
  - With no charts, removes ALL charts including infrastructure (MongoDB, Postgres).
  - PVCs are deleted by default for a clean slate. Use --keep-volumes to preserve data.
EOF
  cat <<'EOF'
Additional options:
  -n, --namespace <name>  Kubernetes namespace (default: agent-engine)
  -h, --help             Show help
  --delete-namespace     Delete the namespace after release removal.
  --keep-volumes         Preserve PVCs (MongoDB and Postgres data volumes).
EOF
}

parse_args() {
  CHART_ARGS=""
  while [ "$#" -gt 0 ]; do
    case "$1" in
      -n|--namespace)
        NAMESPACE=$2
        shift 2
        ;;
      --delete-namespace)
        DELETE_NAMESPACE=true
        shift
        ;;
      --keep-volumes)
        DELETE_VOLUMES=false
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        CHART_ARGS="$CHART_ARGS $1"
        shift
        ;;
    esac
  done

  # For cleanup, default to all charts including infra
  if [ -z "$CHART_ARGS" ]; then
    REQUESTED_CHARTS="$ALL_CHARTS"
  else
    # shellcheck disable=SC2086
    REQUESTED_CHARTS=$(normalize_requested_charts $CHART_ARGS)
  fi
}

parse_args "$@"
require_command helm

for chart in rest core runtime global-properties infra; do
  # shellcheck disable=SC2086
  if chart_selected "$chart" $REQUESTED_CHARTS; then
    release_name=$(chart_release_name "$chart")
    helm uninstall "$release_name" --namespace "$NAMESPACE" 2>/dev/null || true
    echo "Removed $chart from namespace $NAMESPACE"
  fi
done

if [ "$DELETE_VOLUMES" = "true" ]; then
  require_command kubectl
  echo "Deleting PVCs (data volumes)..."
  kubectl delete pvc -n "$NAMESPACE" --all --ignore-not-found
  echo "Deleted all PVCs from namespace $NAMESPACE"
fi

if [ "$DELETE_NAMESPACE" = "true" ]; then
  require_command kubectl
  kubectl delete namespace "$NAMESPACE" --ignore-not-found >/dev/null
  echo "Deleted namespace $NAMESPACE"
fi
