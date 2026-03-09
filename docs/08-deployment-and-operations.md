# 8. Deployment and Operations

## 8.1 Deployment Modes

### Dev Mode (`deploy/deploy.sh dev`)

- starts MongoDB container (`deploy/docker-compose.yaml`)
- optionally bootstraps models/agents
- runs `:interfaces:local:quarkusDev`

### Production Mode (`deploy/deploy.sh production`)

- optionally builds quarkus applications (`engine`, `interfaces:rest`)
- starts engine jar on:
  - HTTP: `18081`
  - gRPC: `19000`
- starts rest jar on:
  - HTTP: `18080`
  - gRPC target host/port from system props

## 8.2 Container Artifacts

`deploy/Dockerfile` builds image for REST app in JVM mode:

- base image: `eclipse-temurin:25-jre-alpine`
- exposes `18080`
- copies plugin directory to `/deployments/plugins`
- sets `PLUGIN_DIR=/deployments/plugins`

## 8.3 Mongo and Infra Defaults

`deploy/docker-compose.yaml`:

- MongoDB service exposed as `localhost:27018` -> container `27017`

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
scripts/generate-encryption-key.sh [mongodb-uri]
```

This script generates and upserts encryption key material.

## 8.5 Session and Runtime Operational Behavior

- sessions are persisted and can be resumed by session ID
- runtime caches session runtimes and model clients with idle eviction
- deleting a session invalidates runtime cache entry through `SessionDeletedEvent`

## 8.6 API Health and Bootstrap Expectations

Deployment bootstrap waits for REST OpenAPI endpoint:

- `GET /q/openapi`

After availability, bootstrap sync script can upsert configs.

## 8.7 Logging and Threads

- REST configured with file JSON logging support (`interfaces/rest/application.properties`)
- virtual threads are used across execution paths (Quarkus and explicit executors)
- gRPC server uses virtual-thread executor with prefix `agent-grpc-vt-`

## 8.8 Operational Risk Areas

1. Plugin jars are loaded dynamically from filesystem; keep plugin supply chain controlled.
2. Shell tool (`run_cmd`) is high risk even with `rm` block; guardrail/tool exposure should be tightly scoped.
3. Guardrails in `OPTIMISTIC` mode can allow brief window before async output decision resolves.
4. gRPC service method dispatch is name-based; interface and method naming stability matters for distributed deployments.
