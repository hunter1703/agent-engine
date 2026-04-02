# Kubernetes Layout

This directory now ships as an operator-friendly Helm layout with:

- chart-per-component packaging
- a single built-in production overlay under `k8s/environments/prod/`
- versioned infra config payloads under `configs/infra/`
- repeatable entrypoint scripts under `k8s/scripts/`
- safer defaults for probes, security context, PDBs, rollout settings, and layered global/local application config
- externalized runtime config via mounted `application.properties`

## Structure

```text
k8s/
  core/                # Helm chart for the core gRPC service
  global-properties/   # Shared global properties ConfigMap chart
  infra/               # Optional local/dev MongoDB and PostgreSQL
  rest/                # REST gateway chart, optional ingress
  runtime/             # Runtime StatefulSet and headless service
  environments/
    prod/              # The default and only built-in overlay
  scripts/
    apply-charts.sh    # Internal Helm chart applier used by stage scripts
    deploy-infra.sh    # Stage 1: deploy MongoDB/PostgreSQL infra
    deploy-services.sh # Stage 3: deploy global-properties, runtime, core, and rest
    build-images.sh    # Optional manual image build helper
    deploy.sh          # Main staged deployment entrypoint
    seed-configs.sh    # Full sync (infra + catalog)
    seed-infra-configs.sh   # Stage 2: seed infra config
    seed-catalog-configs.sh # Stage 4: seed model/agent catalog
    cleanup.sh         # Release cleanup
    lint.sh            # Helm lint with environment overlays
    template.sh        # Render manifests locally
    status.sh          # Quick operational status
```

## Deployment Flow

Default deployment uses the staged `prod` overlay flow:

```bash
./k8s/scripts/deploy.sh
```

`deploy.sh` orchestrates:

1. infra deployment
2. infra config sync
3. service deployment
4. catalog sync

Use `--skip-build` when you want to deploy prebuilt or registry-hosted images.

Alternative namespace:

```bash
./k8s/scripts/deploy.sh -n agent-engine-prod
```

Run individual stages directly when needed:

```bash
./k8s/scripts/deploy-infra.sh
./k8s/scripts/deploy-services.sh
```

Deploy a specific image tag:

```bash
./k8s/scripts/deploy.sh --image-tag 2026-03-26
```

Validate rendered output before applying:

```bash
./k8s/scripts/lint.sh
./k8s/scripts/template.sh > /tmp/agent-engine.yaml
```

Check current release state:

```bash
./k8s/scripts/status.sh -n agent-engine-prod
```

Remove the default application stack:

```bash
./k8s/scripts/cleanup.sh -n agent-engine-prod
```

## Environment Overlays

The built-in production overlay is:

- `k8s/environments/prod/infra.yaml`
- `k8s/environments/prod/global-properties.yaml`
- `k8s/environments/prod/runtime.yaml`
- `k8s/environments/prod/core.yaml`
- `k8s/environments/prod/rest.yaml`

It assumes external MongoDB/PostgreSQL and disables public ingress until a real host/TLS override is supplied.

You can stack additional overrides on top:

```bash
./k8s/scripts/deploy.sh -f /path/to/company-overrides.yaml --set rest.ingress.hosts[0].host=api.example.com
```

## Infra Config Seeding

- `./k8s/scripts/deploy.sh` runs config sync by default.
- Use `--skip-sync-config` when you want rollout without config publication:

```bash
./k8s/scripts/deploy.sh --skip-sync-config
```

- Run sync separately when you want rollout and config publication to be independent:

```bash
./k8s/scripts/seed-configs.sh
```

- `seed-infra-configs.sh` writes infra runtime config before app rollout.
- `seed-catalog-configs.sh` writes model/agent catalog after REST is available.
- The seed step upserts:
  - `configs/infra/` into `INFRA.InfraConfig`
  - `configs/models/` through the model REST upsert API
  - `configs/agents/` through the agent REST upsert API
- The managed infra config payload files are:
  - `default-model-configs.json`
  - `pekko-configs.json`
  - `sql-infra-configs.json`
  - `microservice-infra-configs.json`
The seeding job uses cluster-local Mongo access, so the exact same flow works for Docker Desktop Kubernetes and cloud clusters.

## Images

The standard operator flow is still just:

```bash
./k8s/scripts/deploy.sh
```

`build-images.sh` remains available when you want to prebuild or push images explicitly:

```bash
./k8s/scripts/build-images.sh
TAG=2026-03-26 REGISTRY_PREFIX=ghcr.io/example PUSH=true ./k8s/scripts/build-images.sh
```

For Docker Desktop Kubernetes, local Docker images tagged as `agent-engine/runtime:latest`, `agent-engine/core:latest`, and `agent-engine/rest:latest` are used directly by the cluster unless you override `--image-tag`.

## Production Notes

- The built-in overlay is production-oriented and deploys application workloads only by default.
- Runtime, core, and REST charts support image pull secrets, topology spread, service accounts, pod labels/annotations, and extra env/config injection.
- Each service mounts a single external `/config/application.properties` file from a ConfigMap and sets `QUARKUS_CONFIG_LOCATIONS=file:/config/application.properties`.
- Shared static settings are mounted from `/config/global.properties`, and service-specific overrides are mounted from `/config/local.properties`, while non-secret runtime config lives in `/config/application.properties`.
- Runtime pods receive `POD_NAME` and `POD_NAMESPACE` through the Kubernetes downward API, and `ActorSystemProvider` resolves those placeholders when building the Pekko config at startup.
- Seed nodes are only the bootstrap set for cluster formation. By default the seed step writes the first three StatefulSet ordinals, so scaling runtime replicas up does not require changing the seed list.
- If you need to seed different model IDs, SQL settings, service names, or seed-node fanout, set environment variables such as `DEFAULT_MODEL_ID`, `TITLE_MODEL_ID`, `COMPACTION_MODEL_ID`, `EVALUATOR_MODEL_ID`, `SQL_JDBC_URL`, `CORE_SERVICE_NAME`, `RUNTIME_SERVICE_NAME`, or `RUNTIME_SEED_NODE_COUNT` before running the seed step.

## Secret Strategy

Default production behavior is external or pre-created credentials and endpoints.

If you want local/dev in-cluster databases, explicitly deploy the `infra` chart.

If you want external or pre-created credentials:

1. Set `global-properties.globalProperties.infra.mongodb.uri` to the correct MongoDB URI (and keep `infra.mongodb.database` / `app.mongodb.database` aligned with your deployment).
2. Keep `runtime.applicationConfig.globalConfigMapName`, `core.applicationConfig.globalConfigMapName`, and `rest.applicationConfig.globalConfigMapName` pointed to the shared ConfigMap containing `global.properties`.
3. Set `SQL_JDBC_URL`, `POSTGRES_SECRET_NAME`, and related env overrides when running config sync if PostgreSQL is external.
4. When using the optional `infra` chart, set `infra.mongodb.connectionString`, `infra.mongodb.auth.existingSecret`, `infra.postgres.jdbcUrl`, or `infra.postgres.existingSecret` as appropriate.

## Default Assumptions

- namespace default: `agent-engine`
- REST/internal HTTP port: `8080`
- gRPC port: `9000`
- Pekko port: `2552`
- core service id: `agent`
- runtime service id: `runtime`

At runtime, Quarkus loads non-secret app config from `/config/application.properties`, with system properties and environment variables taking precedence over file values.
