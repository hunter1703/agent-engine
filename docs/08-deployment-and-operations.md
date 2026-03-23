# 8. Deployment and Operations

## 8.1 Deployment Model

Deployment is Kubernetes-native.

The standard stack is installed with:

```bash
./k8s/scripts/deploy.sh
```

`deploy.sh` is the single deployment command for the application workloads. It builds the service images it needs and deploys the Helm charts in dependency order.

Config publication is explicit:

```bash
./k8s/scripts/seed-configs.sh
./k8s/scripts/deploy.sh --sync-config
```

The default deployment profile is the built-in production overlay. An alternate namespace can be supplied when needed:

```bash
./k8s/scripts/deploy.sh -n agent-engine-prod
```

and removed with:

```bash
./k8s/scripts/cleanup.sh
```

The deploy order is:

1. `global-properties`
2. `infra`
3. `runtime`
4. `core`
5. `rest`

With no chart arguments, the default deployment target is `runtime`, `core`, and `rest`. `infra` is optional and intended primarily for local/dev installs.

The detailed chart and operator workflow is documented in [`k8s/README.md`](/Users/rhp/Projects/agent-engine/k8s/README.md), including:

- the default `prod` overlay
- the single-command deploy flow and optional `--skip-build`
- `lint.sh`, `template.sh`, and `status.sh`
- `seed-configs.sh`, `configs/infra/`, `configs/models/`, and `configs/agents/`
- mounted `/config/application.properties`
- the split between `PekkoConfig` and `SQLInfraConfig`
- runtime placeholder resolution for `POD_NAME` and `POD_NAMESPACE`
- secret-backed bootstrap and production hardening expectations

## 8.2 Container Artifacts

`docker/Dockerfile` builds service images in JVM mode:

- base image: `eclipse-temurin:25-jre-alpine`
- exposes `8080`, `9000`, and `2552`
- copies plugin directory to `/deployments/plugins`
- sets `PLUGIN_DIR=/deployments/plugins`

Build examples:

```bash
docker build --build-arg SERVICE_MODULE=runtime -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=core -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=interfaces/rest -f docker/Dockerfile .
```

## 8.3 Mongo and Infra Defaults

The shared bootstrap property is:

- `mongodb.connection.string`
- `MONGODB_CONNECTION_STRING`

In Kubernetes, the Mongo connection string is expected to come from `MONGODB_CONNECTION_STRING`, while non-secret runtime settings are expected to come from an externally mounted `application.properties`.

Repositories default to database names:

- `AGENT_ENGINE`
- `INFRA`

## 8.4 Encryption for Secure Fields

Secure persistence model:

- annotate string fields/getters with `@Secure`
- Mongo codec convention attaches `SecureStringCodec`
- values encrypted/decrypted through `EncryptionService`

Encryption algorithm:

- AES/GCM/NoPadding
- 256-bit key (base64 in infra config)
- stored with prefix `enc::`

Infra source for key:

- `INFRA.InfraConfig` with `type=encryption`

Helper script:

```bash
./scripts/generate-encryption-key.sh [mongodb-uri]
```

This script generates and upserts encryption key material.

## 8.5 Session and Runtime Operational Behavior

- sessions are persisted and can be resumed by session ID
- runtime caches session runtimes and model clients with idle eviction
- deleting a session invalidates runtime cache entry through `SessionDeletedEvent`

## 8.6 API Health and Bootstrap Expectations

Bootstrap or config sync callers can wait for the REST OpenAPI endpoint:

- `GET /q/openapi`

After availability, bootstrap sync script can upsert configs.

## 8.7 Logging and Threads

- virtual threads are used across execution paths (Quarkus and explicit executors)
- gRPC server uses virtual-thread executor with prefix `agent-grpc-vt-`

## 8.8 Operational Risk Areas

1. Plugin jars are loaded dynamically from filesystem; keep plugin supply chain controlled.
2. Shell tool (`run_cmd`) is high risk even with `rm` block; guardrail/tool exposure should be tightly scoped.
3. gRPC service method dispatch is name-based; interface and method naming stability matters for distributed deployments.
