# Parallel Deploy DAG Design

**Date:** 2026-04-26  
**Scope:** `k8s/scripts/deploy.sh`, `k8s/scripts/cleanup.sh`

## Problem

The current `deploy.sh` executes six phases strictly sequentially. The longest bottleneck — Gradle + Docker builds — blocks all infrastructure work, even though builds and infra deployment are completely independent. Total wall-clock time is the sum of all phases.

## Goal

Reduce deployment time by executing stages concurrently wherever their dependencies allow, while preserving correctness and keeping the implementation in POSIX sh.

## Dependency Graph

```
t=0:  [job_build]                [job_infra]
           |                          |
      builds-done              infra-deployed
           |                          |
           |                   [job_seed_infra]
           |                          |
           |                    infra-seeded
           |                          |
           +──────────────────────────+
           |                          |
     [job_deploy_core]        [job_deploy_rest]
           |                          |
       core-ready                rest-ready
           |                          |
    [job_deploy_runtime]              |
                                      +──── core-ready + rest-ready
                                      |
                               [job_seed_catalog]
                                      |
                               catalog-seeded
                                      |
                               [job_port_forward]
```

### Dependency Table

| Job | Waits for flags |
|---|---|
| `job_build` | — |
| `job_infra` | — |
| `job_seed_infra` | `infra-deployed` |
| `deploy_service core` | `builds-done`, `infra-seeded` |
| `deploy_service rest` | `builds-done`, `infra-seeded` |
| `deploy_service runtime` | `core-ready` |
| `job_seed_catalog` | `core-ready`, `rest-ready` |
| `job_port_forward` | `catalog-seeded` |

### Dependency Rationale

- **runtime waits for core-ready**: Pekko actor recovery reads session state from core's session service on startup. If core is not yet answering, recovery fails.
- **seed_catalog waits for core-ready + rest-ready**: The seeder POSTs to the REST API, which proxies calls to core. Rest's readiness probe is independent of core (by design), so both must be explicitly confirmed healthy.
- **job_infra and job_build are independent**: Infra uses public images deployed via Helm; there are no infra Docker images to build.

## Synchronization Mechanism

### State Directory

A fixed, well-known path replaces `mktemp -d` so that cleanup.sh can always find and remove it:

```sh
STATE_DIR=/tmp/agent-engine-deploy-state
```

On each deploy start, `STATE_DIR` is wiped and recreated fresh:

```sh
rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR"
trap 'rm -rf "$STATE_DIR"' EXIT
```

### Flag Files

Each job signals completion by writing a flag file to `STATE_DIR`:

```sh
touch "$STATE_DIR/builds-done"
```

### `wait_for` Helper

Polls for one or more flags, aborting immediately if any job has written `failed`:

```sh
wait_for() {
  for flag; do
    while [ ! -f "$STATE_DIR/$flag" ]; do
      [ -f "$STATE_DIR/failed" ] && exit 1
      sleep 1
    done
  done
}
```

## Modular Service Declarations

Both service deployment and catalog seeding dependencies are declared as data. No job functions change when services are added.

### APP_SERVICES

A single generic `deploy_service` function handles any service. Each service is one entry:

```sh
# Format: "chart:comma-separated-wait-flags:ready-flag"
APP_SERVICES="
core:builds-done,infra-seeded:core-ready
rest:builds-done,infra-seeded:rest-ready
runtime:core-ready:runtime-ready
"
```

The generic function:

```sh
deploy_service() {
  chart=$1 deps=$2 ready_flag=$3
  # shellcheck disable=SC2046
  wait_for $(printf '%s' "$deps" | tr ',' ' ')
  IMAGES_PREBUILT=true sh "$SCRIPT_DIR/deploy-services.sh" $(helm_flags) "$chart" \
    || { touch "$STATE_DIR/failed"; exit 1; }
  touch "$STATE_DIR/$ready_flag"
}
```

The main orchestrator launches each service in a loop:

```sh
for entry in $APP_SERVICES; do
  [ -n "$entry" ] || continue
  chart=$(printf '%s' "$entry" | cut -d: -f1)
  deps=$(printf '%s' "$entry"  | cut -d: -f2)
  flag=$(printf '%s' "$entry"  | cut -d: -f3)
  deploy_service "$chart" "$deps" "$flag" &
  pids="$pids $!"
done
```

### CATALOG_DEPS

Catalog seeding dependencies are also declared as data, not hardcoded inside `job_seed_catalog`:

```sh
# Ready-flags that must be set before catalog seeding begins.
# Add a service's ready-flag here if seed-catalog calls through it.
CATALOG_DEPS="core-ready,rest-ready"
```

`job_seed_catalog` waits generically:

```sh
job_seed_catalog() {
  # shellcheck disable=SC2046
  wait_for $(printf '%s' "$CATALOG_DEPS" | tr ',' ' ')
  ...
}
```

**Adding a new service** requires:
- One new line in `APP_SERVICES`
- If the service must be ready before seeding: one new flag appended to `CATALOG_DEPS`

No new functions, no new wiring.

## Error Handling

Every job wraps its work so that any failure writes the `failed` flag and exits:

```sh
job_foo() {
  <work> || { touch "$STATE_DIR/failed"; exit 1; }
}
```

The main process launches all jobs with `&`, collects their PIDs, and `wait`s on each. If any PID returns non-zero, the script exits 1 after all jobs have settled. The `failed` flag causes all `wait_for` loops to unblock and exit immediately, preventing jobs from hanging after a sibling failure.

## Flag Handling for `--skip-infra` and `--dry-run`

Both flags require pre-writing certain flags so downstream jobs unblock without running the skipped stages.

**`--skip-infra`**: Pre-write `infra-deployed` and `infra-seeded` before launching jobs. `job_infra` and `job_seed_infra` are not started.

**`--dry-run`**: Pre-write `builds-done`. Pass `--dry-run` through to Helm jobs. Skip `job_seed_infra`, `job_seed_catalog`, and `job_port_forward`.

## Scope of Changes

### `deploy.sh`

- Rewritten: sequential phase blocks replaced with background job functions + flag polling.
- Existing sub-scripts (`build-images.sh`, `deploy-infra.sh`, `deploy-services.sh`, `seed-infra-configs.sh`, `seed-catalog-configs.sh`) are called unchanged from within job functions.
- Ctrl+C handler (`stop_workloads`) preserved as-is.
- `--skip-infra`, `--dry-run`, `--no-atomic`, `--lint`, `--timeout`, `-e`, `-n`, `-f`, `--set`, `--image-tag` flags all preserved.

### `cleanup.sh`

Three additions, in this order before helm uninstalls:

1. Kill any running `deploy.sh` process:
   ```sh
   pkill -f "k8s/scripts/deploy.sh" 2>/dev/null || true
   ```

2. Kill any active `kubectl port-forward` processes:
   ```sh
   pkill -f "kubectl.*port-forward.*agent-engine" 2>/dev/null || true
   ```

3. Remove deploy state dir:
   ```sh
   rm -rf /tmp/agent-engine-deploy-state
   ```

### No changes to

- `build-images.sh`
- `deploy-infra.sh`
- `deploy-services.sh`
- `seed-infra-configs.sh`
- `seed-catalog-configs.sh`
- `apply-charts.sh`
- All Helm charts
- All Quarkus health configurations (already correct — each service's readiness probe checks only its own layer)
