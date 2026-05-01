#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
K8S_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
DEFAULT_NAMESPACE=agent-engine
DEFAULT_ENVIRONMENT=local
DEFAULT_TIMEOUT=10m
DEFAULT_CHARTS="global-properties agent catalog rest knowledge"
ALL_CHARTS="infra global-properties agent catalog rest knowledge"

chart_release_name() {
  case "$1" in
    infra) echo "agent-engine-infra" ;;
    global-properties) echo "agent-engine-global-properties" ;;
    agent) echo "agent-engine-agent" ;;
    catalog) echo "agent-engine-catalog" ;;
    rest) echo "agent-engine-rest" ;;
    knowledge) echo "agent-engine-knowledge" ;;
    *)
      echo "Unknown chart: $1" >&2
      exit 1
      ;;
  esac
}

chart_path() {
  echo "$K8S_DIR/$1"
}

chart_env_values_file() {
  echo "$K8S_DIR/environments/$1/$2.yaml"
}

ensure_chart_dependencies() {
  chart=$1
  helm dependency build "$(chart_path "$chart")" >/dev/null
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

validate_chart_name() {
  for chart in $ALL_CHARTS; do
    if [ "$chart" = "$1" ]; then
      return 0
    fi
  done

  echo "Unsupported chart '$1'. Valid charts: $ALL_CHARTS" >&2
  exit 1
}

normalize_requested_charts() {
  if [ "$#" -eq 0 ]; then
    echo "$DEFAULT_CHARTS"
    return 0
  fi

  result=""
  for chart in "$@"; do
    validate_chart_name "$chart"
    case " $result " in
      *" $chart "*) ;;
      *) result="$result $chart" ;;
    esac
  done
  echo "${result# }"
}

chart_selected() {
  chart_name=$1
  shift
  for requested in "$@"; do
    if [ "$requested" = "$chart_name" ]; then
      return 0
    fi
  done
  return 1
}

append_env_values_args() {
  environment=$1
  chart=$2
  shift 2
  env_file=$(chart_env_values_file "$environment" "$chart")
  if [ -f "$env_file" ]; then
    set -- "$@" -f "$env_file"
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

  printf '%s\n' "$*"
}

print_common_usage() {
  cat <<'EOF'
Options:
  -e, --environment <name>  Environment overlay under k8s/environments (default: prod)
  -n, --namespace <name>    Kubernetes namespace (default: agent-engine)
  -f, --values <file>       Additional Helm values file (repeatable)
  --set <key=value>         Additional Helm set override (repeatable)
  --image-tag <tag>         Override the app image tag for agent/catalog/rest/knowledge
  -h, --help                Show help
EOF
}
