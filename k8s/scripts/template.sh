#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

ENVIRONMENT=$DEFAULT_ENVIRONMENT
NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
IMAGE_TAG=${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo "dev")}
EXTRA_VALUES_FILES=""
SET_ARGUMENTS=""

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/template.sh
  ./k8s/scripts/template.sh rest
EOF
  print_common_usage
}

parse_args() {
  CHART_ARGS=""
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

for chart in $ALL_CHARTS; do
  # shellcheck disable=SC2086
  if chart_selected "$chart" $REQUESTED_CHARTS; then
    ensure_chart_dependencies "$chart"
    release_name=$(chart_release_name "$chart")
    echo "---"
    echo "# $chart ($release_name)"
    set -- template "$release_name" "$(chart_path "$chart")" --namespace "$NAMESPACE" --set namespace="$NAMESPACE"
    case "$chart" in
      runtime|core|rest)
        set -- "$@" --set image.tag="$IMAGE_TAG"
        ;;
    esac
    # shellcheck disable=SC2046
    set -- $(append_env_values_args "$ENVIRONMENT" "$chart" "$@")
    helm "$@"
  fi
done
