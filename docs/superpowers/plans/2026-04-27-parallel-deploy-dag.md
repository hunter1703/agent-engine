# Parallel Deploy DAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `deploy.sh` to run independent deployment stages concurrently using a flag-file DAG, make services and catalog dependencies fully declarative, and update `cleanup.sh` to fully reset deployment state.

**Architecture:** `deploy.sh` launches build and infra jobs in parallel from t=0. App services are declared as data entries (chart + wait-flags + ready-flag) driven by a single generic `deploy_service` function. Catalog seeding dependencies are also declarative via `CATALOG_DEPS`. Flag files in a fixed `STATE_DIR` coordinate ordering. `cleanup.sh` kills any running deploy process and removes state before tearing down resources.

**Tech Stack:** POSIX sh, kubectl, helm, docker

---

## File Map

| File | Change |
|---|---|
| `k8s/scripts/apply-charts.sh` | Add `IMAGES_PREBUILT` env var to skip rebuild when images already built |
| `k8s/scripts/deploy.sh` | Full rewrite — sequential phases → parallel DAG orchestrator |
| `k8s/scripts/cleanup.sh` | Add process + state cleanup before helm uninstalls |

No other files change.

---

### Task 1: Add `IMAGES_PREBUILT` support to `apply-charts.sh`

**Files:**
- Modify: `k8s/scripts/apply-charts.sh:89-110`

`deploy.sh` will build all images upfront in `job_build`. When `deploy_service` later calls `deploy-services.sh` per chart, `apply-charts.sh` must not rebuild images. A new `IMAGES_PREBUILT=true` env var skips the image build but still sets `ROLLOUT_REVISION` so the pod annotation is applied correctly.

- [ ] **Step 1: Locate `build_selected_images` in `apply-charts.sh`**

Open `k8s/scripts/apply-charts.sh`. Find `build_selected_images()` starting around line 89. The function currently has two early-return checks at the top: one for `DRY_RUN=true` and one that collects components.

- [ ] **Step 2: Add `IMAGES_PREBUILT` early-return after the `DRY_RUN` check**

Replace:
```sh
build_selected_images() {
  if [ "$DRY_RUN" = "true" ]; then
    return 0
  fi

  selected_components=""
```

With:
```sh
build_selected_images() {
  if [ "$DRY_RUN" = "true" ]; then
    return 0
  fi

  if [ "${IMAGES_PREBUILT:-}" = "true" ]; then
    ROLLOUT_REVISION=$(date +%s)
    return 0
  fi

  selected_components=""
```

- [ ] **Step 3: Verify syntax**

```bash
sh -n k8s/scripts/apply-charts.sh
```

Expected: no output (no syntax errors).

- [ ] **Step 4: Commit**

```bash
git add k8s/scripts/apply-charts.sh
git commit -m "feat(deploy): skip image rebuild when IMAGES_PREBUILT=true"
```

---

### Task 2: Rewrite `deploy.sh` — preamble, STATE_DIR, and helpers

**Files:**
- Modify: `k8s/scripts/deploy.sh`

Keep everything before the first phase block unchanged (color constants, print helpers, `stop_workloads`, variable defaults, `usage`, `parse_args`, `helm_flags`). Add `STATE_DIR` constant, update `stop_workloads` to remove state on interrupt, then add synchronization helpers.

- [ ] **Step 1: Add `RED` color constant and `STATE_DIR` after the existing color block**

After `RESET='\033[0m'`, add:
```sh
RED='\033[0;31m'
```

After the existing variable defaults block (after `LOCAL_PORT=${LOCAL_PORT:-8080}`), add:
```sh
STATE_DIR=/tmp/agent-engine-deploy-state
```

- [ ] **Step 2: Update `stop_workloads` to clean STATE_DIR on interrupt**

In `stop_workloads()`, add `rm -rf "$STATE_DIR" 2>/dev/null || true` as the first line after the opening printf:

```sh
stop_workloads() {
  printf "\n${BOLD}${YELLOW}⚠ Shutdown signal received. Deleting workloads (preserving volumes)...${RESET}\n"
  rm -rf "$STATE_DIR" 2>/dev/null || true

  printf "${CYAN}  → Stopping port-forward...${RESET}\n"
  pkill -f "kubectl.*port-forward.*:${LOCAL_PORT}" 2>/dev/null || true
  # ... rest of function unchanged ...
```

- [ ] **Step 3: Add `wait_for` and `fail` helpers after the `helm_flags` function**

```sh
# Polls for each named flag file. Exits immediately if the 'failed' sentinel is present.
wait_for() {
  for flag; do
    while [ ! -f "$STATE_DIR/$flag" ]; do
      [ -f "$STATE_DIR/failed" ] && exit 1
      sleep 1
    done
  done
}

fail() {
  touch "$STATE_DIR/failed"
  exit 1
}
```

- [ ] **Step 4: Verify syntax**

```bash
sh -n k8s/scripts/deploy.sh
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add k8s/scripts/deploy.sh
git commit -m "feat(deploy): add STATE_DIR, wait_for helper, and fail sentinel"
```

---

### Task 3: Add infrastructure job functions to `deploy.sh`

**Files:**
- Modify: `k8s/scripts/deploy.sh`

Add `job_build`, `job_infra`, and `job_seed_infra` after the helpers from Task 2. Each function handles its own skip logic for `--skip-infra` and `--dry-run`, then signals completion by touching a flag file.

- [ ] **Step 1: Add `job_build`**

```sh
job_build() {
  if [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/builds-done"
    return 0
  fi
  print_phase "Building Docker images"
  require_command docker
  TAG=$IMAGE_TAG "$SCRIPT_DIR/build-images.sh" runtime core rest || fail
  touch "$STATE_DIR/builds-done"
}
```

- [ ] **Step 2: Add `job_infra`**

```sh
job_infra() {
  if [ "$SKIP_INFRA" = "true" ]; then
    touch "$STATE_DIR/infra-deployed"
    return 0
  fi
  print_phase "Deploying infrastructure workloads"
  # shellcheck disable=SC2046
  sh "$SCRIPT_DIR/deploy-infra.sh" $(helm_flags) || fail
  touch "$STATE_DIR/infra-deployed"
}
```

- [ ] **Step 3: Add `job_seed_infra`**

```sh
job_seed_infra() {
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/infra-seeded"
    return 0
  fi
  wait_for infra-deployed
  print_phase "Seeding infrastructure configuration"
  sh "$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/infra-seeded"
}
```

- [ ] **Step 4: Verify syntax**

```bash
sh -n k8s/scripts/deploy.sh
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add k8s/scripts/deploy.sh
git commit -m "feat(deploy): add job_build, job_infra, job_seed_infra functions"
```

---

### Task 4: Add `deploy_service`, `APP_SERVICES`, `CATALOG_DEPS`, and `job_seed_catalog` to `deploy.sh`

**Files:**
- Modify: `k8s/scripts/deploy.sh`

Add the generic service deployer and both declarative data structures. New services are added by inserting one line in `APP_SERVICES`. If a service must be ready before catalog seeding, its ready-flag is appended to `CATALOG_DEPS`.

- [ ] **Step 1: Add `deploy_service` generic function**

```sh
# Deploys a single chart after waiting for its declared dependencies.
# Usage: deploy_service <chart> <comma-separated-wait-flags> <ready-flag>
deploy_service() {
  chart=$1 deps=$2 ready_flag=$3
  # shellcheck disable=SC2046
  wait_for $(printf '%s' "$deps" | tr ',' ' ')
  print_phase "Deploying $chart"
  IMAGES_PREBUILT=true sh "$SCRIPT_DIR/deploy-services.sh" $(helm_flags) "$chart" || fail
  touch "$STATE_DIR/$ready_flag"
}
```

- [ ] **Step 2: Declare `APP_SERVICES` and `CATALOG_DEPS`**

```sh
# Declare app services: "chart:comma-separated-wait-flags:ready-flag"
# To add a new service, append one line here. No other changes required.
APP_SERVICES="
core:builds-done,infra-seeded:core-ready
rest:builds-done,infra-seeded:rest-ready
runtime:core-ready:runtime-ready
"

# Ready-flags that must all be set before catalog seeding begins.
# Append a service's ready-flag here if seed-catalog calls through it.
CATALOG_DEPS="core-ready,rest-ready"
```

- [ ] **Step 3: Add `job_seed_catalog`**

```sh
job_seed_catalog() {
  if [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/catalog-seeded"
    return 0
  fi
  # shellcheck disable=SC2046
  wait_for $(printf '%s' "$CATALOG_DEPS" | tr ',' ' ')
  print_phase "Seeding application catalog"
  sh "$SCRIPT_DIR/seed-catalog-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/catalog-seeded"
}
```

- [ ] **Step 4: Verify syntax**

```bash
sh -n k8s/scripts/deploy.sh
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add k8s/scripts/deploy.sh
git commit -m "feat(deploy): add deploy_service, APP_SERVICES, CATALOG_DEPS, job_seed_catalog"
```

---

### Task 5: Add the main orchestrator to `deploy.sh`

**Files:**
- Modify: `k8s/scripts/deploy.sh`

Replace the old sequential phase blocks (everything from `parse_args "$@"` to the end of the file) with the parallel orchestrator. Port-forward runs in the main process after all background jobs complete — it cannot be a background job since it loops indefinitely.

- [ ] **Step 1: Remove the old sequential phase blocks**

Delete everything from `parse_args "$@"` to the end of the file. This removes Phase 1 through Phase 6.

- [ ] **Step 2: Add the main orchestrator**

```sh
parse_args "$@"

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR"
trap 'rm -rf "$STATE_DIR"' EXIT

pids=""

job_build &
pids="$pids $!"

job_infra &
pids="$pids $!"

job_seed_infra &
pids="$pids $!"

for entry in $APP_SERVICES; do
  [ -n "$entry" ] || continue
  chart=$(printf '%s' "$entry" | cut -d: -f1)
  deps=$(printf '%s' "$entry"  | cut -d: -f2)
  flag=$(printf '%s' "$entry"  | cut -d: -f3)
  deploy_service "$chart" "$deps" "$flag" &
  pids="$pids $!"
done

job_seed_catalog &
pids="$pids $!"

failed_pids=""
for pid in $pids; do
  wait "$pid" || failed_pids="$failed_pids $pid"
done

if [ -n "${failed_pids# }" ]; then
  printf "\n${BOLD}${RED}✗ Deployment failed. Check output above for details.${RESET}\n" >&2
  exit 1
fi

print_phase "Deployment complete — application is ready"

if [ "$ENVIRONMENT" = "local" ] && [ "$DRY_RUN" != "true" ]; then
  print_step "Clearing existing port-forwards on port ${LOCAL_PORT}"
  pkill -f "kubectl.*port-forward.*:${LOCAL_PORT}" 2>/dev/null || true
  sleep 1
  print_step "Starting port-forward agent-engine-rest:8080 → localhost:${LOCAL_PORT}"
  print_info "REST API available at http://localhost:${LOCAL_PORT}"
  print_note "Press Ctrl+C to stop."
  while true; do
    kubectl port-forward -n "$NAMESPACE" svc/agent-engine-rest "${LOCAL_PORT}:8080" || true
    print_warn "Port-forward dropped, restarting..."
    sleep 2
  done
fi
```

- [ ] **Step 3: Verify syntax**

```bash
sh -n k8s/scripts/deploy.sh
```

Expected: no output.

- [ ] **Step 4: Smoke test with `--dry-run`**

```bash
./k8s/scripts/deploy.sh --dry-run
```

Expected: prints phase headers for build (skipped), infra, services, catalog (skipped). Exits 0. No port-forward starts.

- [ ] **Step 5: Smoke test with `--skip-infra --dry-run`**

```bash
./k8s/scripts/deploy.sh --skip-infra --dry-run
```

Expected: prints phase headers, infra and seeding phases skipped. Exits 0.

- [ ] **Step 6: Commit**

```bash
git add k8s/scripts/deploy.sh
git commit -m "feat(deploy): parallel DAG orchestrator with declarative services"
```

---

### Task 6: Update `cleanup.sh`

**Files:**
- Modify: `k8s/scripts/cleanup.sh`

Add three cleanup actions immediately after `require_command helm` and before the helm uninstall loop: kill any running deploy.sh, kill any port-forwards, remove STATE_DIR.

- [ ] **Step 1: Locate the insertion point in `cleanup.sh`**

Open `k8s/scripts/cleanup.sh`. Find `require_command helm` (around line 70). The helm uninstall loop follows immediately after.

- [ ] **Step 2: Insert process and state cleanup**

After `require_command helm`, add:

```sh
# Stop any in-progress deployment before tearing down resources.
pkill -f "k8s/scripts/deploy.sh" 2>/dev/null || true
pkill -f "kubectl.*port-forward.*agent-engine" 2>/dev/null || true
rm -rf /tmp/agent-engine-deploy-state
```

- [ ] **Step 3: Verify syntax**

```bash
sh -n k8s/scripts/cleanup.sh
```

Expected: no output.

- [ ] **Step 4: Verify help still prints correctly**

```bash
./k8s/scripts/cleanup.sh --help
```

Expected: usage text prints and exits 0.

- [ ] **Step 5: Commit**

```bash
git add k8s/scripts/cleanup.sh
git commit -m "feat(cleanup): kill deploy processes and remove state before teardown"
```
