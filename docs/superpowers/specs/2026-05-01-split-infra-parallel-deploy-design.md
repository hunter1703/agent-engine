# Split Infra + Maximally Parallel Deployment Design

**Date:** 2026-05-01
**Scope:** `k8s/infra/`, `k8s/mongodb/`, `k8s/postgres/`, `k8s/localstack/`, `k8s/qdrant/`,
`k8s/scripts/deploy.sh`, `k8s/scripts/deploy-infra.sh`, `k8s/scripts/seed-infra-configs.sh`,
`k8s/scripts/init-postgres-schema.sh` (new), `k8s/scripts/lib.sh`, `k8s/scripts/apply-charts.sh`,
`k8s/environments/local/`

## Context

The April 2026 spec (`2026-04-26-parallel-deploy-dag-design.md`) introduced the flag-file job
framework and `APP_SERVICES` data table. That design is already implemented and is the baseline.

This spec redesigns two things on top of it:

1. **Chart split** — the monolithic `k8s/infra/` Helm chart (MongoDB, Postgres, Localstack,
   Qdrant bundled together) is split into four standalone charts, one per service.
2. **Script split** — `seed-infra-configs.sh` currently does two unrelated things (Postgres DDL
   schema init and MongoDB config seeding). They are separated into two independent scripts so each
   can start as soon as its own dependency is satisfied.

Both changes feed into a maximally parallel deployment DAG.

---

## Dependency Graph

```
nothing → mongodb deploy
nothing → postgres deploy
nothing → localstack deploy
nothing → qdrant deploy
nothing → global-properties deploy
nothing → knowledge image build
nothing → catalog image build
nothing → agent image build
nothing → rest image build

postgres deploy            → init postgres schema
mongodb deploy             → seed infra configs

global-properties deploy, knowledge image build → knowledge deploy
global-properties deploy, catalog image build   → catalog deploy
global-properties deploy, rest image build      → rest deploy
global-properties deploy, catalog deploy, agent image build → agent deploy

mongodb deploy, rest deploy, catalog deploy → seed application configs
```

All nine root tasks (four infra deploys, global-properties, four image builds) start at T=0 with
no incoming edges. Everything else starts as soon as its direct dependencies complete.

### Flag table

| Job | Waits for flags | Produces flag |
|---|---|---|
| `job_infra_svc mongodb` | — | `mongodb-ready` |
| `job_infra_svc postgres` | — | `postgres-ready` |
| `job_infra_svc localstack` | — | `localstack-ready` |
| `job_infra_svc qdrant` | — | `qdrant-ready` |
| `job_build` | — | `builds-done` |
| `deploy_service global-properties` | — | `global-properties-ready` |
| `job_init_postgres` | `postgres-ready` | `postgres-schema-ready` |
| `job_seed_infra` | `mongodb-ready` | `infra-seeded` |
| `deploy_service knowledge` | `global-properties-ready`, `builds-done` | `knowledge-ready` |
| `deploy_service catalog` | `global-properties-ready`, `builds-done` | `catalog-ready` |
| `deploy_service rest` | `global-properties-ready`, `builds-done` | `rest-ready` |
| `deploy_service agent` | `global-properties-ready`, `catalog-ready`, `builds-done` | `agent-ready` |
| `job_seed_catalog` | `mongodb-ready`, `catalog-ready`, `rest-ready` | `catalog-seeded` |

`postgres-schema-ready` is produced but not consumed by any downstream job — Postgres DDL
completes in ~5s and agent's startup takes ~60-90s (it waits for `catalog-ready`), so agent
always observes an initialized schema in practice.

### Rationale for new edges vs. previous design

| Previous | New | Reason |
|---|---|---|
| `infra-deployed → seed_infra` | `mongodb-ready → seed_infra` | Only MongoDB is needed for mongosh seeding |
| `infra-seeded → global-properties` | no dep | global-properties is a static ConfigMap with hardcoded values |
| `infra-seeded → knowledge` | `builds-done, global-properties-ready → knowledge` | knowledge doesn't gate any other job; global-properties is all it needs to start |
| `catalog-ready, rest-ready → seed_catalog` | adds `mongodb-ready` | seed-catalog POSTs through catalog's API into MongoDB; explicit safety gate |
| `infra-deployed` (monolithic) | four individual `*-ready` flags | each infra service now has fine-grained readiness |

---

## Chart Split

### Deletion

`k8s/infra/` is deleted in its entirety. `k8s/scripts/deploy-infra.sh` is deleted (it was only
a one-line wrapper around `apply-charts.sh infra`).

### New charts

Each new chart owns only its own workload. No cross-chart references.

```
k8s/mongodb/
  Chart.yaml
  values.yaml
  templates/
    _helpers.tpl
    statefulset.yaml
    service.yaml         ← ClusterIP
    headless-service.yaml
    auth-secret.yaml
    connection-secret.yaml

k8s/postgres/
  Chart.yaml
  values.yaml
  templates/
    _helpers.tpl
    statefulset.yaml
    service.yaml
    headless-service.yaml
    auth-secret.yaml

k8s/localstack/
  Chart.yaml
  values.yaml
  templates/
    _helpers.tpl
    deployment.yaml
    service.yaml

k8s/qdrant/
  Chart.yaml
  values.yaml
  templates/
    _helpers.tpl
    deployment.yaml
    service.yaml
```

Templates are extracted verbatim from `k8s/infra/templates/` and adjusted minimally (helper
prefix changes from `agent-engine-infra` to the chart-specific name).

### Secret naming

The old names (`agent-engine-infra-mongodb-auth`, `agent-engine-infra-mongodb-connection`,
`agent-engine-infra-postgres-auth`) were derived from the Helm release name `agent-engine-infra`.
With individual releases, the release-name prefix changes.

New fixed names (hardcoded in templates, not derived from release name):

| Secret | New name |
|---|---|
| MongoDB auth | `mongodb-auth` |
| MongoDB connection string | `mongodb-connection` |
| Postgres auth | `postgres-auth` |

`seed-infra-configs.sh` variable defaults update accordingly:

```sh
MONGODB_CONNECTION_SECRET_NAME=${MONGODB_CONNECTION_SECRET_NAME:-mongodb-connection}
POSTGRES_SECRET_NAME=${POSTGRES_SECRET_NAME:-postgres-auth}
```

### Environment overlays

`k8s/environments/local/infra.yaml` is deleted and replaced with:

- `k8s/environments/local/mongodb.yaml` — persistence storageClassName, auth credentials
- `k8s/environments/local/postgres.yaml` — persistence storageClassName, credentials

localstack and qdrant have no local overrides; no overlay files needed.

### Helm release names

| Chart | Release name |
|---|---|
| `mongodb` | `agent-engine-mongodb` |
| `postgres` | `agent-engine-postgres` |
| `localstack` | `agent-engine-localstack` |
| `qdrant` | `agent-engine-qdrant` |

---

## Script Split

### `init-postgres-schema.sh` (new)

Extracts the psql DDL block from `seed-infra-configs.sh`. Accepts `-n NAMESPACE`. Creates
`event_journal` and `snapshot` tables using `CREATE TABLE IF NOT EXISTS` (idempotent).

Reads postgres credentials from the `postgres-auth` secret (same logic as today's script).

### `seed-infra-configs.sh` (trimmed)

Removes the psql block entirely. Retains all MongoDB logic unchanged. Still reads the postgres
secret to generate the SQL JDBC URL/user/password values that are written into MongoDB
`INFRA.InfraConfig` — it just no longer runs any psql commands.

---

## `deploy.sh` Changes

### `INFRA_SERVICES` table

Infra services are declared as data, mirroring `APP_SERVICES`. The same format applies:
`chart:comma-separated-deps:ready-flag`. Adding a new infra service is one new line here.

```sh
# Format: "chart:comma-separated-wait-flags:ready-flag"
# To add a new infra service, append one line here. No other changes required.
INFRA_SERVICES="
mongodb::mongodb-ready
postgres::postgres-ready
localstack::localstack-ready
qdrant::qdrant-ready
"
```

### Infra service helper

`job_infra_svc` wraps `deploy_service` with `--skip-infra` awareness. It accepts `deps` as a
parameter so infra services can declare dependencies on each other if needed.

```sh
job_infra_svc() {
  chart=$1 deps=$2 ready_flag=$3
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/$ready_flag"
    return 0
  fi
  deploy_service "$chart" "$deps" "$ready_flag"
}
```

The main orchestrator launches all infra services in a loop — identical in structure to the
`APP_SERVICES` loop:

```sh
for entry in $INFRA_SERVICES; do
  [ -n "$entry" ] || continue
  chart=$(printf '%s' "$entry" | cut -d: -f1)
  deps=$(printf '%s' "$entry"  | cut -d: -f2)
  flag=$(printf '%s' "$entry"  | cut -d: -f3)
  job_infra_svc "$chart" "$deps" "$flag" &
  pids="$pids $!"
done
```

### Postgres schema job

```sh
job_init_postgres() {
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/postgres-schema-ready"; return 0
  fi
  wait_for postgres-ready
  sh "$SCRIPT_DIR/init-postgres-schema.sh" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/postgres-schema-ready"
}
```

### Infra seeding job

```sh
job_seed_infra() {
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/infra-seeded"; return 0
  fi
  wait_for mongodb-ready
  sh "$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/infra-seeded"
}
```

### `APP_SERVICES`

```sh
APP_SERVICES="
global-properties::global-properties-ready
catalog:global-properties-ready,builds-done:catalog-ready
rest:global-properties-ready,builds-done:rest-ready
knowledge:global-properties-ready,builds-done:knowledge-ready
agent:global-properties-ready,catalog-ready,builds-done:agent-ready
"
```

`global-properties` has no deps (empty string between the first and second `:`).

### `CATALOG_DEPS`

```sh
CATALOG_DEPS="mongodb-ready,catalog-ready,rest-ready"
```

### Removed

- `job_infra` function
- `job_infra &` launch line
- `infra-deployed` flag (replaced by four individual `*-ready` flags)

---

## `lib.sh` Changes

Replace `infra` with the four new charts throughout:

```sh
ALL_CHARTS="global-properties agent catalog rest knowledge mongodb postgres localstack qdrant"

chart_release_name() {
  case "$1" in
    mongodb)    echo "agent-engine-mongodb"    ;;
    postgres)   echo "agent-engine-postgres"   ;;
    localstack) echo "agent-engine-localstack" ;;
    qdrant)     echo "agent-engine-qdrant"     ;;
    # existing cases unchanged ...
  esac
}
```

`chart_path()` already returns `$K8S_DIR/$1` generically — no change needed.

---

## `apply-charts.sh` Changes

Remove the infra-specific `--atomic` block:

```sh
# Remove this block:
if [ "$ATOMIC" = "true" ] && [ "$chart" = "infra" ]; then
  set -- "$@" --atomic
fi
```

Infra charts are now deployed via `job_infra_svc` → `deploy_service`, which calls
`apply-charts.sh` for a single chart at a time. Rollout waiting is handled by Helm's default
`--wait` behaviour (on by default) plus the flag-file readiness signal.

---

## `--skip-infra` Semantics

`--skip-infra` now pre-satisfies all infra flags via `job_infra_svc` and `job_init_postgres` /
`job_seed_infra` guards. The flag means "MongoDB, Postgres, Localstack, and Qdrant are already
running; skip their deployment and seeding entirely."

---

## Cleanup

`stop_workloads` in `deploy.sh` already names services by their Kubernetes resource name, not
their Helm release name, so no changes are needed there. `deploy-infra.sh` is removed from the
cleanup handler's kill list.

---

## Files Created

| File | Action |
|---|---|
| `k8s/mongodb/` | New chart |
| `k8s/postgres/` | New chart |
| `k8s/localstack/` | New chart |
| `k8s/qdrant/` | New chart |
| `k8s/scripts/init-postgres-schema.sh` | New script |
| `k8s/environments/local/mongodb.yaml` | New env overlay |
| `k8s/environments/local/postgres.yaml` | New env overlay |

## Files Modified

| File | Change |
|---|---|
| `k8s/scripts/deploy.sh` | New infra job helpers, updated `APP_SERVICES`, `CATALOG_DEPS` |
| `k8s/scripts/seed-infra-configs.sh` | Remove psql block; update secret name defaults |
| `k8s/scripts/lib.sh` | Replace `infra` with four new charts in `ALL_CHARTS`, `chart_release_name` |
| `k8s/scripts/apply-charts.sh` | Remove infra `--atomic` special-case |

## Files Deleted

| File | Reason |
|---|---|
| `k8s/infra/` | Replaced by four individual charts |
| `k8s/scripts/deploy-infra.sh` | Was a one-line wrapper around `apply-charts.sh infra` |
| `k8s/environments/local/infra.yaml` | Replaced by `mongodb.yaml` + `postgres.yaml` |
