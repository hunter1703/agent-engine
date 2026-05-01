#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
ENVIRONMENT=${ENVIRONMENT:-$DEFAULT_ENVIRONMENT}
MONGODB_CONNECTION_SECRET_NAME=${MONGODB_CONNECTION_SECRET_NAME:-mongodb-connection}
MONGODB_CONNECTION_SECRET_KEY=${MONGODB_CONNECTION_SECRET_KEY:-connection-string}
POSTGRES_SECRET_NAME=${POSTGRES_SECRET_NAME:-postgres-auth}
POSTGRES_USERNAME_KEY=${POSTGRES_USERNAME_KEY:-username}
POSTGRES_PASSWORD_KEY=${POSTGRES_PASSWORD_KEY:-password}
POSTGRES_SERVICE_NAME=${POSTGRES_SERVICE_NAME:-postgres}
POSTGRES_PORT=${POSTGRES_PORT:-5432}
POSTGRES_DATABASE=${POSTGRES_DATABASE:-agent_engine_events}
AGENT_SEED_NODE_COUNT=${AGENT_SEED_NODE_COUNT:-3}
CATALOG_SERVICE_NAME=${CATALOG_SERVICE_NAME:-agent-engine-catalog}
AGENT_SERVICE_NAME=${AGENT_SERVICE_NAME:-agent-engine-agent}
AGENT_HEADLESS_SERVICE=${AGENT_HEADLESS_SERVICE:-agent-engine-agent-internal}
AGENT_STATEFULSET_NAME=${AGENT_STATEFULSET_NAME:-agent-engine-agent}
AGENT_CLUSTER_NAME=${AGENT_CLUSTER_NAME:-agent-engine-agent}
PEKKO_SNAPSHOT_THRESHOLD=${PEKKO_SNAPSHOT_THRESHOLD:-100}
DEFAULT_MODEL_ID=${DEFAULT_MODEL_ID:-}
TITLE_MODEL_ID=${TITLE_MODEL_ID:-}
COMPACTION_MODEL_ID=${COMPACTION_MODEL_ID:-}
EVALUATOR_MODEL_ID=${EVALUATOR_MODEL_ID:-}
LOCALSTACK_SERVICE_NAME=${LOCALSTACK_SERVICE_NAME:-localstack}
QDRANT_SERVICE_NAME=${QDRANT_SERVICE_NAME:-qdrant}
KNOWLEDGE_SERVICE_NAME=${KNOWLEDGE_SERVICE_NAME:-agent-engine-knowledge}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/seed-infra-configs.sh
  ./k8s/scripts/seed-infra-configs.sh -n agent-engine
  ./k8s/scripts/seed-infra-configs.sh -e prod

Behavior:
  - Upserts configs/infra into INFRA.InfraConfig directly from the existing MongoDB pod.
  - Writes Pekko, SQL, and microservice application config before app rollout.
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

CATALOG_GRPC_PORT=$(service_port "$CATALOG_SERVICE_NAME" grpc 9000)
AGENT_GRPC_PORT=$(service_port "$AGENT_SERVICE_NAME" grpc 9000)
KNOWLEDGE_GRPC_PORT=$(service_port "$KNOWLEDGE_SERVICE_NAME" grpc 9000)
AGENT_PEKKO_PORT=$(service_port "$AGENT_HEADLESS_SERVICE" pekko 2552)
AGENT_REPLICAS=$(statefulset_replicas "$AGENT_STATEFULSET_NAME" 3)
seed_node_count=$AGENT_SEED_NODE_COUNT
if [ "$seed_node_count" -gt "$AGENT_REPLICAS" ]; then
  seed_node_count=$AGENT_REPLICAS
fi

AGENT_SEED_NODES_JSON="["
index=0
while [ "$index" -lt "$seed_node_count" ]; do
  entry="\"pekko://${AGENT_CLUSTER_NAME}@${AGENT_STATEFULSET_NAME}-${index}.${AGENT_HEADLESS_SERVICE}.${NAMESPACE}.svc.cluster.local:${AGENT_PEKKO_PORT}\""
  if [ "$index" -gt 0 ]; then
    AGENT_SEED_NODES_JSON="${AGENT_SEED_NODES_JSON},${entry}"
  else
    AGENT_SEED_NODES_JSON="${AGENT_SEED_NODES_JSON}${entry}"
  fi
  index=$((index + 1))
done
AGENT_SEED_NODES_JSON="${AGENT_SEED_NODES_JSON}]"

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
CATALOG_SERVICE_NAME_B64=$(printf '%s' "$CATALOG_SERVICE_NAME" | encode_base64)
CATALOG_GRPC_PORT_B64=$(printf '%s' "$CATALOG_GRPC_PORT" | encode_base64)
AGENT_SERVICE_NAME_B64=$(printf '%s' "$AGENT_SERVICE_NAME" | encode_base64)
AGENT_GRPC_PORT_B64=$(printf '%s' "$AGENT_GRPC_PORT" | encode_base64)
AGENT_SEED_NODES_JSON_B64=$(printf '%s' "$AGENT_SEED_NODES_JSON" | encode_base64)
AGENT_CLUSTER_NAME_B64=$(printf '%s' "$AGENT_CLUSTER_NAME" | encode_base64)

PEKKO_SNAPSHOT_THRESHOLD_B64=$(printf '%s' "$PEKKO_SNAPSHOT_THRESHOLD" | encode_base64)
SQL_JDBC_URL_B64=$(printf '%s' "$SQL_JDBC_URL" | encode_base64)
SQL_JDBC_USER_B64=$(printf '%s' "$SQL_JDBC_USER" | encode_base64)
SQL_JDBC_PASSWORD_B64=$(printf '%s' "$SQL_JDBC_PASSWORD" | encode_base64)
TITLE_MODEL_ID_B64=$(printf '%s' "$TITLE_MODEL_ID" | encode_base64)
COMPACTION_MODEL_ID_B64=$(printf '%s' "$COMPACTION_MODEL_ID" | encode_base64)
EVALUATOR_MODEL_ID_B64=$(printf '%s' "$EVALUATOR_MODEL_ID" | encode_base64)
LOCALSTACK_SERVICE_NAME_B64=$(printf '%s' "$LOCALSTACK_SERVICE_NAME" | encode_base64)
QDRANT_SERVICE_NAME_B64=$(printf '%s' "$QDRANT_SERVICE_NAME" | encode_base64)
KNOWLEDGE_SERVICE_NAME_B64=$(printf '%s' "$KNOWLEDGE_SERVICE_NAME" | encode_base64)
KNOWLEDGE_GRPC_PORT_B64=$(printf '%s' "$KNOWLEDGE_GRPC_PORT" | encode_base64)

IMPORT_SCRIPT="$TMP_DIR/import.js"
cat <<EOF > "$IMPORT_SCRIPT"
function decode(value) {
  return Buffer.from(value, "base64").toString("utf8");
}

const rawConfigs = JSON.parse(decode("${CONFIGS_B64}"));
const configs = Array.isArray(rawConfigs) ? rawConfigs : Object.values(rawConfigs);
const namespace = decode("${NAMESPACE_B64}");
const catalogServiceName = decode("${CATALOG_SERVICE_NAME_B64}");
const catalogGrpcPort = Number(decode("${CATALOG_GRPC_PORT_B64}"));
const agentServiceName = decode("${AGENT_SERVICE_NAME_B64}");
const agentGrpcPort = Number(decode("${AGENT_GRPC_PORT_B64}"));
const agentSeedNodes = JSON.parse(decode("${AGENT_SEED_NODES_JSON_B64}"));
const agentClusterName = decode("${AGENT_CLUSTER_NAME_B64}");
const pekkoSnapshotThreshold = Number(decode("${PEKKO_SNAPSHOT_THRESHOLD_B64}"));
const sqlJdbcUrl = decode("${SQL_JDBC_URL_B64}");
const sqlJdbcUser = decode("${SQL_JDBC_USER_B64}");
const sqlJdbcPassword = decode("${SQL_JDBC_PASSWORD_B64}");
const titleModelId = decode("${TITLE_MODEL_ID_B64}");
const compactionModelId = decode("${COMPACTION_MODEL_ID_B64}");
const evaluatorModelId = decode("${EVALUATOR_MODEL_ID_B64}");
const localstackServiceName = decode("${LOCALSTACK_SERVICE_NAME_B64}");
const qdrantServiceName = decode("${QDRANT_SERVICE_NAME_B64}");
const knowledgeServiceName = decode("${KNOWLEDGE_SERVICE_NAME_B64}");
const knowledgeGrpcPort = Number(decode("${KNOWLEDGE_GRPC_PORT_B64}"));

function applyDeploymentOverrides(config) {
  const next = { ...config };

  if (next.type === "default_models") {
    if (titleModelId) next.titleModelId = titleModelId;
    if (compactionModelId) next.compactionModelId = compactionModelId;
    if (evaluatorModelId) next.evaluatorModelId = evaluatorModelId;
    return next;
  }

  if (next.type === "PEKKO") {
    next.seedNodes = agentSeedNodes;
    next.clusterName = agentClusterName || next.clusterName;
    next.snapshotThreshold = pekkoSnapshotThreshold;
    return next;
  }

  if (next.type === "sql") {
    next.jdbcUrl = sqlJdbcUrl || next.jdbcUrl;
    next.jdbcUser = sqlJdbcUser || next.jdbcUser;
    next.jdbcPassword = sqlJdbcPassword || next.jdbcPassword;
    return next;
  }

  if (next.type === "cloudstorage") {
    next.endpointUrl = \`http://\${localstackServiceName}.\${namespace}.svc.cluster.local:4566\`;
    return next;
  }

  if (next.type === "qdrant") {
    next.host = \`\${qdrantServiceName}.\${namespace}.svc.cluster.local\`;
    return next;
  }

  if (next.type === "microservice" && next.serverId === "catalog") {
    next.host = \`\${catalogServiceName}.\${namespace}.svc.cluster.local\`;
    next.port = catalogGrpcPort;
    return next;
  }

  if (next.type === "microservice" && next.serverId === "agent") {
    next.host = \`\${agentServiceName}.\${namespace}.svc.cluster.local\`;
    next.port = agentGrpcPort;
    return next;
  }

  if (next.type === "microservice" && next.serverId === "knowledge") {
    next.host = \`\${knowledgeServiceName}.\${namespace}.svc.cluster.local\`;
    next.port = knowledgeGrpcPort;
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

MONGODB_POD=$(kubectl get pods --namespace "$NAMESPACE" -l app.kubernetes.io/name=mongodb -o jsonpath='{.items[0].metadata.name}')

kubectl exec --namespace "$NAMESPACE" -i "$MONGODB_POD" -- sh -c 'cat >/tmp/import.js && mongosh "$1" --quiet /tmp/import.js' sh \
  "$MONGODB_CONNECTION_STRING" < "$IMPORT_SCRIPT"
