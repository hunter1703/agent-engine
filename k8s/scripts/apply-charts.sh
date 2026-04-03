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
TIMEOUT=$DEFAULT_TIMEOUT
IMAGE_TAG=${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo "dev")}
EXTRA_VALUES_FILES=""
SET_ARGUMENTS=""
ROLLOUT_REVISION=""

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/apply-charts.sh
  ./k8s/scripts/apply-charts.sh runtime core rest
  ./k8s/scripts/apply-charts.sh -n agent-engine-prod -f /path/to/override.yaml --set rest.ingress.enabled=true

Behavior:
  - Deploys runtime, core, and rest when no charts are provided.
  - Builds runtime, core, and rest images automatically before applying those charts.
  - Applies environment overlays from k8s/environments/<environment>/<chart>.yaml.
  - Enforces dependency order: infra -> runtime -> core -> rest.
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
        CHART_ARGS="$CHART_ARGS $1"
        shift
        ;;
    esac
  done

  # shellcheck disable=SC2086
  REQUESTED_CHARTS=$(normalize_requested_charts $CHART_ARGS)
}

build_selected_images() {
  if [ "$DRY_RUN" = "true" ] || [ "$BUILD_IMAGES" != "true" ]; then
    return 0
  fi

  selected_components=""
  for component in runtime core rest; do
    # shellcheck disable=SC2086
    if chart_selected "$component" $REQUESTED_CHARTS; then
      selected_components="$selected_components $component"
    fi
  done

  if [ -z "${selected_components# }" ]; then
    return 0
  fi

  require_command docker
  ROLLOUT_REVISION=$(date +%s)
  echo "Building Docker images for:${selected_components}"
  # shellcheck disable=SC2086
  TAG=$IMAGE_TAG "$SCRIPT_DIR/build-images.sh" $selected_components
}

build_helm_args() {
  chart=$1
  release_name=$(chart_release_name "$chart")
  if [ "$DRY_RUN" = "true" ]; then
    set -- template "$release_name" "$(chart_path "$chart")" \
      --namespace "$NAMESPACE" \
      --set namespace="$NAMESPACE"
  else
    set -- upgrade --install "$release_name" "$(chart_path "$chart")" \
      --namespace "$NAMESPACE" \
      --create-namespace \
      --set namespace="$NAMESPACE" \
      --timeout "$TIMEOUT"

    if [ "$WAIT" = "true" ]; then
      set -- "$@" --wait
    fi
    if [ "$ATOMIC" = "true" ]; then
      set -- "$@" --atomic
    fi
  fi

  case "$chart" in
    runtime|core|rest)
      set -- "$@" --set image.tag="$IMAGE_TAG"
      if [ -n "${ROLLOUT_REVISION:-}" ]; then
        set -- "$@" --set-string podAnnotations.rolloutRevision="$ROLLOUT_REVISION"
      fi
      ;;
  esac

  # shellcheck disable=SC2046
  set -- $(append_env_values_args "$ENVIRONMENT" "$chart" "$@")
  printf '%s\n' "$*"
}

lint_chart() {
  chart=$1
  release_name=$(chart_release_name "$chart")
  set -- lint "$(chart_path "$chart")" --namespace "$NAMESPACE" --set namespace="$NAMESPACE"
  case "$chart" in
    runtime|core|rest)
      set -- "$@" --set image.tag="$IMAGE_TAG"
      if [ -n "${ROLLOUT_REVISION:-}" ]; then
        set -- "$@" --set-string podAnnotations.rolloutRevision="$ROLLOUT_REVISION"
      fi
      ;;
  esac
  # shellcheck disable=SC2046
  set -- $(append_env_values_args "$ENVIRONMENT" "$chart" "$@")
  helm "$@"
  echo "Linted $chart ($release_name)"
}

deploy_chart() {
  chart=$1
  # shellcheck disable=SC2046
  set -- $(build_helm_args "$chart")
  helm "$@"
  if [ "$DRY_RUN" = "true" ]; then
    echo "Rendered $chart for namespace $NAMESPACE"
  else
    echo "Deployed $chart to namespace $NAMESPACE"
  fi
}

parse_args "$@"

require_command helm
build_selected_images
if [ "$DRY_RUN" != "true" ]; then
  require_command kubectl
  kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
fi

for chart in $ALL_CHARTS; do
  # shellcheck disable=SC2086
  if chart_selected "$chart" $REQUESTED_CHARTS; then
    ensure_chart_dependencies "$chart"
    if [ "$LINT" = "true" ]; then
      lint_chart "$chart"
    fi
    deploy_chart "$chart"
  fi
done
