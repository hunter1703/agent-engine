#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/status.sh
  ./k8s/scripts/status.sh -n agent-engine-prod
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

require_command helm
require_command kubectl

echo "Helm releases:"
helm ls --namespace "$NAMESPACE"
echo
echo "Pods:"
kubectl get pods --namespace "$NAMESPACE"
echo
echo "Services:"
kubectl get svc --namespace "$NAMESPACE"
