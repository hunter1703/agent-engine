#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
INFRA_CONFIGMAP_NAME=agent-engine-infra-config-seed
INFRA_JOB_NAME=agent-engine-infra-config-seed
CATALOG_CONFIGMAP_NAME=agent-engine-catalog-config-seed
CATALOG_JOB_NAME=agent-engine-catalog-config-seed
MONGO_IMAGE=${INFRA_CONFIG_SEED_IMAGE:-mongo:7.0}
CATALOG_SEED_IMAGE=${CATALOG_CONFIG_SEED_IMAGE:-curlimages/curl:8.12.1}
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
REST_SERVICE_NAME=${REST_SERVICE_NAME:-agent-engine-rest}
RUNTIME_SERVICE_NAME=${RUNTIME_SERVICE_NAME:-agent-engine-runtime}
RUNTIME_HEADLESS_SERVICE=${RUNTIME_HEADLESS_SERVICE:-agent-engine-runtime-internal}
RUNTIME_STATEFULSET_NAME=${RUNTIME_STATEFULSET_NAME:-agent-engine-runtime}
RUNTIME_CLUSTER_NAME=${RUNTIME_CLUSTER_NAME:-agent-engine-runtime}
REST_READY_PATH=${REST_READY_PATH:-/q/health/ready}
PEKKO_SNAPSHOT_THRESHOLD=${PEKKO_SNAPSHOT_THRESHOLD:-100}
DEFAULT_MODEL_ID=${DEFAULT_MODEL_ID:-qwen3-coder-30b}
TITLE_MODEL_ID=${TITLE_MODEL_ID:-$DEFAULT_MODEL_ID}
COMPACTION_MODEL_ID=${COMPACTION_MODEL_ID:-$DEFAULT_MODEL_ID}
EVALUATOR_MODEL_ID=${EVALUATOR_MODEL_ID:-$DEFAULT_MODEL_ID}
WAIT_TIMEOUT=${WAIT_TIMEOUT:-180s}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/seed-configs.sh
  ./k8s/scripts/seed-configs.sh -n agent-engine

Behavior:
  - Upserts configs/infra into INFRA.InfraConfig.
  - Upserts configs/models and configs/agents through the REST API.
  - Uses one-shot in-cluster jobs so the same flow works locally and in cloud clusters.
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

require_command kubectl

TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

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

CORE_GRPC_PORT=$(service_port "$CORE_SERVICE_NAME" grpc 9000)
REST_HTTP_PORT=$(service_port "$REST_SERVICE_NAME" http 8080)
RUNTIME_GRPC_PORT=$(service_port "$RUNTIME_SERVICE_NAME" grpc 9000)
RUNTIME_PEKKO_PORT=$(service_port "$RUNTIME_HEADLESS_SERVICE" pekko 2552)
RUNTIME_REPLICAS=$(statefulset_replicas "$RUNTIME_STATEFULSET_NAME" 3)
RUNTIME_POD_FQDN_TEMPLATE="\${POD_NAME}.${RUNTIME_HEADLESS_SERVICE}.\${POD_NAMESPACE}.svc.cluster.local"

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
REST_BASE_URL="http://${REST_SERVICE_NAME}.${NAMESPACE}.svc.cluster.local:${REST_HTTP_PORT}"

kubectl create configmap "$INFRA_CONFIGMAP_NAME" \
  --namespace "$NAMESPACE" \
  --from-file=default-model-configs.json="$K8S_DIR/../configs/infra/default-model-configs.json" \
  --from-file=pekko-configs.json="$K8S_DIR/../configs/infra/pekko-configs.json" \
  --from-file=sql-infra-configs.json="$K8S_DIR/../configs/infra/sql-infra-configs.json" \
  --from-file=microservice-infra-configs.json="$K8S_DIR/../configs/infra/microservice-infra-configs.json" \
  --from-file=import.js="$K8S_DIR/scripts/import-infra-configs.js" \
  --dry-run=client \
  -o yaml | kubectl apply -f - >/dev/null

find "$K8S_DIR/../configs/models" -maxdepth 1 -type f -name '*.json' -print | sort | while read -r file; do
  cp "$file" "$TMP_DIR/model__$(basename "$file")"
done

find "$K8S_DIR/../configs/agents" -maxdepth 1 -type f -name '*.json' -print | sort | while read -r file; do
  cp "$file" "$TMP_DIR/agent__$(basename "$file")"
done

cp "$K8S_DIR/scripts/seed-rest-catalog.sh" "$TMP_DIR/seed-rest-catalog.sh"

kubectl create configmap "$CATALOG_CONFIGMAP_NAME" \
  --namespace "$NAMESPACE" \
  --from-file="$TMP_DIR" \
  --dry-run=client \
  -o yaml | kubectl apply -f - >/dev/null

kubectl delete job "$INFRA_JOB_NAME" --namespace "$NAMESPACE" --ignore-not-found >/dev/null
kubectl delete job "$CATALOG_JOB_NAME" --namespace "$NAMESPACE" --ignore-not-found >/dev/null

cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: ${INFRA_JOB_NAME}
  namespace: ${NAMESPACE}
spec:
  backoffLimit: 3
  ttlSecondsAfterFinished: 300
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: seed
          image: ${MONGO_IMAGE}
          imagePullPolicy: IfNotPresent
          env:
            - name: NAMESPACE
              value: ${NAMESPACE}
            - name: CORE_SERVICE_NAME
              value: ${CORE_SERVICE_NAME}
            - name: CORE_GRPC_PORT
              value: "${CORE_GRPC_PORT}"
            - name: RUNTIME_SERVICE_NAME
              value: ${RUNTIME_SERVICE_NAME}
            - name: RUNTIME_GRPC_PORT
              value: "${RUNTIME_GRPC_PORT}"
            - name: RUNTIME_HEADLESS_SERVICE
              value: ${RUNTIME_HEADLESS_SERVICE}
            - name: RUNTIME_STATEFULSET_NAME
              value: ${RUNTIME_STATEFULSET_NAME}
            - name: RUNTIME_PEKKO_PORT
              value: "${RUNTIME_PEKKO_PORT}"
            - name: RUNTIME_CLUSTER_NAME
              value: ${RUNTIME_CLUSTER_NAME}
            - name: RUNTIME_POD_FQDN_TEMPLATE
              value: '${RUNTIME_POD_FQDN_TEMPLATE}'
            - name: RUNTIME_SEED_NODES_JSON
              value: '${RUNTIME_SEED_NODES_JSON}'
            - name: SQL_JDBC_URL
              value: ${SQL_JDBC_URL}
            - name: PEKKO_SNAPSHOT_THRESHOLD
              value: "${PEKKO_SNAPSHOT_THRESHOLD}"
            - name: TITLE_MODEL_ID
              value: ${TITLE_MODEL_ID}
            - name: COMPACTION_MODEL_ID
              value: ${COMPACTION_MODEL_ID}
            - name: EVALUATOR_MODEL_ID
              value: ${EVALUATOR_MODEL_ID}
            - name: MONGODB_CONNECTION_STRING
              valueFrom:
                secretKeyRef:
                  name: ${MONGODB_CONNECTION_SECRET_NAME}
                  key: ${MONGODB_CONNECTION_SECRET_KEY}
            - name: SQL_JDBC_USER
              valueFrom:
                secretKeyRef:
                  name: ${POSTGRES_SECRET_NAME}
                  key: ${POSTGRES_USERNAME_KEY}
            - name: SQL_JDBC_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: ${POSTGRES_SECRET_NAME}
                  key: ${POSTGRES_PASSWORD_KEY}
          command:
            - sh
            - -c
            - mongosh "$MONGODB_CONNECTION_STRING" --quiet /configs/import.js
          volumeMounts:
            - name: configs
              mountPath: /configs
              readOnly: true
      volumes:
        - name: configs
          configMap:
            name: ${INFRA_CONFIGMAP_NAME}
---
apiVersion: batch/v1
kind: Job
metadata:
  name: ${CATALOG_JOB_NAME}
  namespace: ${NAMESPACE}
spec:
  backoffLimit: 3
  ttlSecondsAfterFinished: 300
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: seed
          image: ${CATALOG_SEED_IMAGE}
          imagePullPolicy: IfNotPresent
          env:
            - name: REST_BASE_URL
              value: ${REST_BASE_URL}
            - name: READY_PATH
              value: ${REST_READY_PATH}
          command:
            - sh
            - /seed/seed-rest-catalog.sh
          volumeMounts:
            - name: seed
              mountPath: /seed
              readOnly: true
      volumes:
        - name: seed
          configMap:
            name: ${CATALOG_CONFIGMAP_NAME}
EOF

if ! kubectl wait --for=condition=complete --timeout="$WAIT_TIMEOUT" job/"$INFRA_JOB_NAME" --namespace "$NAMESPACE" >/dev/null; then
  kubectl logs job/"$INFRA_JOB_NAME" --namespace "$NAMESPACE" || true
  exit 1
fi

if ! kubectl wait --for=condition=complete --timeout="$WAIT_TIMEOUT" job/"$CATALOG_JOB_NAME" --namespace "$NAMESPACE" >/dev/null; then
  kubectl logs job/"$CATALOG_JOB_NAME" --namespace "$NAMESPACE" || true
  exit 1
fi

kubectl logs job/"$INFRA_JOB_NAME" --namespace "$NAMESPACE"
kubectl logs job/"$CATALOG_JOB_NAME" --namespace "$NAMESPACE"
