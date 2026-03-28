#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
ENVIRONMENT=${ENVIRONMENT:-$DEFAULT_ENVIRONMENT}
MONGODB_CONNECTION_SECRET_NAME=${MONGODB_CONNECTION_SECRET_NAME:-agent-engine-infra-mongodb-connection}
MONGODB_CONNECTION_SECRET_KEY=${MONGODB_CONNECTION_SECRET_KEY:-connection-string}
POSTGRES_SECRET_NAME=${POSTGRES_SECRET_NAME:-agent-engine-infra-postgres-auth}
POSTGRES_USERNAME_KEY=${POSTGRES_USERNAME_KEY:-username}
POSTGRES_PASSWORD_KEY=${POSTGRES_PASSWORD_KEY:-password}
POSTGRES_SERVICE_NAME=${POSTGRES_SERVICE_NAME:-postgres}
POSTGRES_PORT=${POSTGRES_PORT:-5432}
POSTGRES_DATABASE=${POSTGRES_DATABASE:-agent_engine_events}
RUNTIME_SEED_NODE_COUNT=${RUNTIME_SEED_NODE_COUNT:-3}
CORE_SERVICE_NAME=${CORE_SERVICE_NAME:-agent-engine-core}
RUNTIME_SERVICE_NAME=${RUNTIME_SERVICE_NAME:-agent-engine-runtime}
RUNTIME_HEADLESS_SERVICE=${RUNTIME_HEADLESS_SERVICE:-agent-engine-runtime-internal}
RUNTIME_STATEFULSET_NAME=${RUNTIME_STATEFULSET_NAME:-agent-engine-runtime}
RUNTIME_CLUSTER_NAME=${RUNTIME_CLUSTER_NAME:-agent-engine-runtime}
PEKKO_SNAPSHOT_THRESHOLD=${PEKKO_SNAPSHOT_THRESHOLD:-100}
DEFAULT_MODEL_ID=${DEFAULT_MODEL_ID:-qwen3-coder-30b}
TITLE_MODEL_ID=${TITLE_MODEL_ID:-$DEFAULT_MODEL_ID}
COMPACTION_MODEL_ID=${COMPACTION_MODEL_ID:-$DEFAULT_MODEL_ID}
EVALUATOR_MODEL_ID=${EVALUATOR_MODEL_ID:-$DEFAULT_MODEL_ID}
WAIT_TIMEOUT=${WAIT_TIMEOUT:-180s}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/seed-infra-configs.sh
  ./k8s/scripts/seed-infra-configs.sh -n agent-engine
  ./k8s/scripts/seed-infra-configs.sh -e prod

Behavior:
  - Upserts configs/infra into INFRA.InfraConfig directly from the existing MongoDB pod.
  - Writes Pekko, SQL, and microservice bootstrap config before app rollout.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -n|--namespace)
      NAMESPACE=$2
      shift 2
      ;;
    -e|--environment)
      ENVIRONMENT=$2
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

require_command kubectl
require_command node

decode_base64() {
  if printf 'dGVzdA==' | base64 -d >/dev/null 2>&1; then
    base64 -d
  else
    base64 -D
  fi
}

encode_base64() {
  base64 | tr -d '\n'
}

service_port() {
  service_name=$1
  port_name=$2
  fallback=$3
  value=$(kubectl get svc "$service_name" --namespace "$NAMESPACE" -o jsonpath="{.spec.ports[?(@.name=='$port_name')].port}" 2>/dev/null || true)
  if [ -n "$value" ]; then
    echo "$value"
  else
    echo "$fallback"
  fi
}

statefulset_replicas() {
  statefulset_name=$1
  fallback=$2
  value=$(kubectl get statefulset "$statefulset_name" --namespace "$NAMESPACE" -o jsonpath="{.spec.replicas}" 2>/dev/null || true)
  if [ -n "$value" ]; then
    echo "$value"
  else
    echo "$fallback"
  fi
}

json_secret_value() {
  secret_name=$1
  secret_key=$2
  kubectl get secret "$secret_name" --namespace "$NAMESPACE" -o "jsonpath={.data['$secret_key']}" | decode_base64
}

CORE_GRPC_PORT=$(service_port "$CORE_SERVICE_NAME" grpc 9000)
RUNTIME_GRPC_PORT=$(service_port "$RUNTIME_SERVICE_NAME" grpc 9000)
RUNTIME_PEKKO_PORT=$(service_port "$RUNTIME_HEADLESS_SERVICE" pekko 2552)
RUNTIME_REPLICAS=$(statefulset_replicas "$RUNTIME_STATEFULSET_NAME" 3)
PEKKO_HOSTNAME_TEMPLATE="\${PEKKO_HOSTNAME}"

seed_node_count=$RUNTIME_SEED_NODE_COUNT
if [ "$seed_node_count" -gt "$RUNTIME_REPLICAS" ]; then
  seed_node_count=$RUNTIME_REPLICAS
fi

RUNTIME_SEED_NODES_JSON="["
index=0
while [ "$index" -lt "$seed_node_count" ]; do
  entry="\"pekko://${RUNTIME_CLUSTER_NAME}@${RUNTIME_STATEFULSET_NAME}-${index}.${RUNTIME_HEADLESS_SERVICE}.${NAMESPACE}.svc.cluster.local:${RUNTIME_PEKKO_PORT}\""
  if [ "$index" -gt 0 ]; then
    RUNTIME_SEED_NODES_JSON="${RUNTIME_SEED_NODES_JSON},${entry}"
  else
    RUNTIME_SEED_NODES_JSON="${RUNTIME_SEED_NODES_JSON}${entry}"
  fi
  index=$((index + 1))
done
RUNTIME_SEED_NODES_JSON="${RUNTIME_SEED_NODES_JSON}]"

SQL_JDBC_URL=${SQL_JDBC_URL:-jdbc:postgresql://${POSTGRES_SERVICE_NAME}:${POSTGRES_PORT}/${POSTGRES_DATABASE}}
SQL_JDBC_USER=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_USERNAME_KEY")
SQL_JDBC_PASSWORD=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_PASSWORD_KEY")
MONGODB_CONNECTION_STRING=$(json_secret_value "$MONGODB_CONNECTION_SECRET_NAME" "$MONGODB_CONNECTION_SECRET_KEY")

TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

CONFIGS_JSON="$TMP_DIR/configs.json"
node -e '
const fs = require("fs");
const path = require("path");
const baseDir = process.argv[1];
const overlayDir = process.argv[2];
const files = [];
const seen = new Set();

for (const file of fs.readdirSync(baseDir).filter(file => file.endsWith(".json")).sort()) {
  const overlayFile = path.join(overlayDir, file);
  if (fs.existsSync(overlayFile)) {
    files.push(overlayFile);
    seen.add(file);
  } else {
    files.push(path.join(baseDir, file));
    seen.add(file);
  }
}

if (fs.existsSync(overlayDir)) {
  for (const file of fs.readdirSync(overlayDir).filter(file => file.endsWith(".json")).sort()) {
    if (!seen.has(file)) {
      files.push(path.join(overlayDir, file));
    }
  }
}

const merged = files.flatMap(file => JSON.parse(fs.readFileSync(file, "utf8")));
fs.writeFileSync(process.argv[3], JSON.stringify(merged));
' "$K8S_DIR/../configs/infra" "$K8S_DIR/environments/$ENVIRONMENT/configs/infra" "$CONFIGS_JSON"

CONFIGS_B64=$(cat "$CONFIGS_JSON" | encode_base64)
NAMESPACE_B64=$(printf '%s' "$NAMESPACE" | encode_base64)
CORE_SERVICE_NAME_B64=$(printf '%s' "$CORE_SERVICE_NAME" | encode_base64)
CORE_GRPC_PORT_B64=$(printf '%s' "$CORE_GRPC_PORT" | encode_base64)
RUNTIME_SERVICE_NAME_B64=$(printf '%s' "$RUNTIME_SERVICE_NAME" | encode_base64)
RUNTIME_GRPC_PORT_B64=$(printf '%s' "$RUNTIME_GRPC_PORT" | encode_base64)
PEKKO_HOSTNAME_TEMPLATE_B64=$(printf '%s' "$PEKKO_HOSTNAME_TEMPLATE" | encode_base64)
RUNTIME_SEED_NODES_JSON_B64=$(printf '%s' "$RUNTIME_SEED_NODES_JSON" | encode_base64)
RUNTIME_CLUSTER_NAME_B64=$(printf '%s' "$RUNTIME_CLUSTER_NAME" | encode_base64)
PEKKO_SNAPSHOT_THRESHOLD_B64=$(printf '%s' "$PEKKO_SNAPSHOT_THRESHOLD" | encode_base64)
SQL_JDBC_URL_B64=$(printf '%s' "$SQL_JDBC_URL" | encode_base64)
SQL_JDBC_USER_B64=$(printf '%s' "$SQL_JDBC_USER" | encode_base64)
SQL_JDBC_PASSWORD_B64=$(printf '%s' "$SQL_JDBC_PASSWORD" | encode_base64)
TITLE_MODEL_ID_B64=$(printf '%s' "$TITLE_MODEL_ID" | encode_base64)
COMPACTION_MODEL_ID_B64=$(printf '%s' "$COMPACTION_MODEL_ID" | encode_base64)
EVALUATOR_MODEL_ID_B64=$(printf '%s' "$EVALUATOR_MODEL_ID" | encode_base64)

IMPORT_SCRIPT="$TMP_DIR/import.js"
cat <<EOF > "$IMPORT_SCRIPT"
function decode(value) {
  return Buffer.from(value, "base64").toString("utf8");
}

const rawConfigs = JSON.parse(decode("${CONFIGS_B64}"));
const configs = Array.isArray(rawConfigs) ? rawConfigs : Object.values(rawConfigs);
const namespace = decode("${NAMESPACE_B64}");
const coreServiceName = decode("${CORE_SERVICE_NAME_B64}");
const coreGrpcPort = Number(decode("${CORE_GRPC_PORT_B64}"));
const runtimeServiceName = decode("${RUNTIME_SERVICE_NAME_B64}");
const runtimeGrpcPort = Number(decode("${RUNTIME_GRPC_PORT_B64}"));
const pekkoHostnameTemplate = decode("${PEKKO_HOSTNAME_TEMPLATE_B64}");
const runtimeSeedNodes = JSON.parse(decode("${RUNTIME_SEED_NODES_JSON_B64}"));
const runtimeClusterName = decode("${RUNTIME_CLUSTER_NAME_B64}");
const pekkoSnapshotThreshold = Number(decode("${PEKKO_SNAPSHOT_THRESHOLD_B64}"));
const sqlJdbcUrl = decode("${SQL_JDBC_URL_B64}");
const sqlJdbcUser = decode("${SQL_JDBC_USER_B64}");
const sqlJdbcPassword = decode("${SQL_JDBC_PASSWORD_B64}");
const titleModelId = decode("${TITLE_MODEL_ID_B64}");
const compactionModelId = decode("${COMPACTION_MODEL_ID_B64}");
const evaluatorModelId = decode("${EVALUATOR_MODEL_ID_B64}");

function applyDeploymentOverrides(config) {
  const next = { ...config };

  if (next.type === "default_model") {
    next.titleModelId = titleModelId || next.titleModelId;
    next.compactionModelId = compactionModelId || next.compactionModelId;
    next.evaluatorModelId = evaluatorModelId || next.evaluatorModelId;
    return next;
  }

  if (next.type === "PEKKO") {
    next.hostname = pekkoHostnameTemplate || next.hostname;
    next.port = Number(next.port);
    next.seedNodes = runtimeSeedNodes;
    next.clusterName = runtimeClusterName || next.clusterName;
    next.snapshotThreshold = pekkoSnapshotThreshold;
    return next;
  }

  if (next.type === "sql") {
    next.jdbcUrl = sqlJdbcUrl || next.jdbcUrl;
    next.jdbcUser = sqlJdbcUser || next.jdbcUser;
    next.jdbcPassword = sqlJdbcPassword || next.jdbcPassword;
    return next;
  }

  if (next.type === "microservice" && next.serverId === "agent") {
    next.host = \`\${coreServiceName}.\${namespace}.svc.cluster.local\`;
    next.port = coreGrpcPort;
    return next;
  }

  if (next.type === "microservice" && next.serverId === "runtime") {
    next.host = \`\${runtimeServiceName}.\${namespace}.svc.cluster.local\`;
    next.port = runtimeGrpcPort;
    return next;
  }

  return next;
}

function upsertConfig(collection, doc) {
  const id = doc.id || doc._id;
  if (!id) {
    throw new Error(\`Config is missing id: \${JSON.stringify(doc)}\`);
  }

  const existing = collection.findOne({ _id: id });
  const now = Date.now();
  const payload = {
    ...doc,
    _id: id,
    createdTime: existing && typeof existing.createdTime === "number" ? existing.createdTime : now,
    updatedTime: now
  };
  delete payload.id;
  collection.replaceOne({ _id: id }, payload, { upsert: true });
  print(\`Upserted infra config \${id} (\${payload.type})\`);
}

const infraDb = db.getSiblingDB("INFRA");
const collection = infraDb.getCollection("InfraConfig");

for (const config of configs.map(applyDeploymentOverrides)) {
  upsertConfig(collection, config);
}
EOF

kubectl wait --for=condition=available --timeout="$WAIT_TIMEOUT" deployment/mongodb --namespace "$NAMESPACE" >/dev/null
MONGODB_POD=$(kubectl get pods --namespace "$NAMESPACE" -l app.kubernetes.io/name=mongodb -o jsonpath='{.items[0].metadata.name}')

kubectl exec --namespace "$NAMESPACE" -i "$MONGODB_POD" -- sh -c 'cat >/tmp/import.js && mongosh "$1" --quiet /tmp/import.js' sh \
  "$MONGODB_CONNECTION_STRING" < "$IMPORT_SCRIPT"
