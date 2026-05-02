# Deployment Scripts

## Architecture: Shell Scripts, Not Jobs/InitContainers

This project uses **shell scripts orchestrated by `deploy.sh`**, not Kubernetes Jobs or InitContainers.

### Why Shell Scripts?

✅ **Advantages:**
- Simpler: No extra Kubernetes resources to manage
- Faster iteration: Edit script → rerun (no image rebuilds)
- Better visibility: All logs in one terminal
- Idempotent by design: Scripts check state before acting
- Flexible: Can run independently for testing
- No cleanup needed: No completed Job pods

❌ **Don't Use:**
- Kubernetes Jobs (creates clutter, not idempotent by default)
- InitContainers (tight coupling, runs on every pod restart)

## Script Organization

### Infrastructure Initialization
- `init-postgres-schema.sh` - Creates PostgreSQL schema
- `init-qdrant-collections.sh` - Creates Qdrant vector collections
- `seed-infra-configs.sh` - Seeds MongoDB with infrastructure configs

### Application Seeding
- `seed-catalog-configs.sh` - Seeds agent and model catalog

### Deployment
- `deploy.sh` - Main orchestrator (runs all jobs in parallel with dependencies)
- `deploy-services.sh` - Deploys individual Helm charts
- `build-images.sh` - Builds Docker images

### Utilities
- `cleanup.sh` - Tears down the deployment
- `status.sh` - Shows deployment status
- `lib.sh` - Shared functions

## How It Works

`deploy.sh` runs initialization scripts as background jobs with dependency management:

```bash
# Example: Qdrant initialization
job_init_qdrant() {
  wait_for qdrant-ready  # Wait for Qdrant pod
  sh init-qdrant-collections.sh -n "$NAMESPACE"
  touch "$STATE_DIR/qdrant-collections-ready"  # Signal completion
}

# Knowledge service waits for collections
deploy_service "knowledge" "qdrant-collections-ready" "knowledge-ready"
```

## Running Scripts Independently

All init scripts can be run standalone for testing:

```bash
# Initialize Qdrant collections
sh k8s/scripts/init-qdrant-collections.sh -n agent-engine

# Initialize PostgreSQL schema
sh k8s/scripts/init-postgres-schema.sh -n agent-engine

# Seed infrastructure configs
sh k8s/scripts/seed-infra-configs.sh -e local -n agent-engine
```

## Adding New Initialization

To add a new initialization step:

1. **Create the script** (e.g., `init-redis.sh`)
   - Make it idempotent (check before acting)
   - Use `lib.sh` functions
   - Accept `-n/--namespace` flag

2. **Add job function in `deploy.sh`**
   ```bash
   job_init_redis() {
     wait_for redis-ready
     sh "$SCRIPT_DIR/init-redis.sh" -n "$NAMESPACE"
     touch "$STATE_DIR/redis-initialized"
   }
   ```

3. **Start the job**
   ```bash
   job_init_redis &
   pids="$pids $!"
   ```

4. **Add dependency** (if needed)
   ```bash
   # Make a service wait for Redis init
   deploy_service "my-service" "redis-initialized" "my-service-ready"
   ```

## DO NOT Create Kubernetes Jobs

If you see completed Job pods in the namespace:
```bash
kubectl get jobs -n agent-engine
# init-qdrant-collection   1/1     Completed
# check-qdrant-collection  1/1     Completed
```

**Delete them immediately:**
```bash
kubectl delete job init-qdrant-collection check-qdrant-collection -n agent-engine
```

These were created manually and should not exist. The shell scripts handle all initialization.
