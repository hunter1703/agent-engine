#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
DELETE_NAMESPACE=false

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/cleanup.sh
  ./k8s/scripts/cleanup.sh runtime core rest
  ./k8s/scripts/cleanup.sh --delete-namespace

Behavior:
  - Removes the requested releases in reverse dependency order.
  - With no charts, removes the default application stack (runtime, core, rest).
EOF
  cat <<'EOF'
Additional options:
  -n, --namespace <name>  Kubernetes namespace (default: agent-engine)
  -h, --help             Show help
  --delete-namespace  Delete the namespace after release removal.
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

  # shellcheck disable=SC2086
  REQUESTED_CHARTS=$(normalize_requested_charts $CHART_ARGS)
}

parse_args "$@"
require_command helm

for chart in rest core runtime infra global-properties; do
  # shellcheck disable=SC2086
  if chart_selected "$chart" $REQUESTED_CHARTS; then
    release_name=$(chart_release_name "$chart")
    helm uninstall "$release_name" --namespace "$NAMESPACE" 2>/dev/null || true
    echo "Removed $chart from namespace $NAMESPACE"
  fi
done

if [ "$DELETE_NAMESPACE" = "true" ]; then
  require_command kubectl
  kubectl delete namespace "$NAMESPACE" --ignore-not-found >/dev/null
  echo "Deleted namespace $NAMESPACE"
fi
