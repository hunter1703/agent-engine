#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
POSTGRES_SECRET_NAME=${POSTGRES_SECRET_NAME:-postgres-auth}
POSTGRES_USERNAME_KEY=${POSTGRES_USERNAME_KEY:-username}
POSTGRES_PASSWORD_KEY=${POSTGRES_PASSWORD_KEY:-password}
POSTGRES_DATABASE=${POSTGRES_DATABASE:-agent_engine_events}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/init-postgres-schema.sh
  ./k8s/scripts/init-postgres-schema.sh -n agent-engine

Behavior:
  - Creates the event_journal and snapshot tables required by Pekko Persistence JDBC.
  - Idempotent: uses CREATE TABLE IF NOT EXISTS.
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

decode_base64() {
  if printf 'dGVzdA==' | base64 -d >/dev/null 2>&1; then
    base64 -d
  else
    base64 -D
  fi
}

json_secret_value() {
  secret_name=$1
  secret_key=$2
  kubectl get secret "$secret_name" --namespace "$NAMESPACE" -o "jsonpath={.data['$secret_key']}" | decode_base64
}

POSTGRES_POD=$(kubectl get pods --namespace "$NAMESPACE" -l app.kubernetes.io/name=postgres -o jsonpath='{.items[0].metadata.name}')
POSTGRES_USER=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_USERNAME_KEY")
POSTGRES_PASSWORD=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_PASSWORD_KEY")

kubectl exec --namespace "$NAMESPACE" -i "$POSTGRES_POD" -- \
  env PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DATABASE" -q <<'EOSQL'
CREATE TABLE IF NOT EXISTS event_journal (
  ordering        BIGSERIAL,
  persistence_id  VARCHAR(255) NOT NULL,
  sequence_number BIGINT       NOT NULL,
  deleted         BOOLEAN      DEFAULT FALSE NOT NULL,
  writer          VARCHAR(255) NOT NULL,
  write_timestamp BIGINT       NOT NULL,
  adapter_manifest VARCHAR(255),
  event_ser_id    INTEGER      NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,
  event_payload   BYTEA        NOT NULL,
  meta_ser_id     INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload    BYTEA,
  PRIMARY KEY (persistence_id, sequence_number)
);
CREATE UNIQUE INDEX IF NOT EXISTS event_journal_ordering_idx ON event_journal (ordering);

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id  VARCHAR(255) NOT NULL,
  sequence_number BIGINT       NOT NULL,
  created         BIGINT       NOT NULL,
  snapshot_ser_id INTEGER      NOT NULL,
  snapshot_ser_manifest VARCHAR(255) NOT NULL,
  snapshot_payload BYTEA       NOT NULL,
  meta_ser_id     INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload    BYTEA,
  PRIMARY KEY (persistence_id, sequence_number)
);
EOSQL
echo "PostgreSQL Pekko schema initialized"
