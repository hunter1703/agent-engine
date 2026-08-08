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
```

An alternate namespace can be supplied when needed:

```bash
./k8s/scripts/deploy.sh -n agent-engine-prod
```

and removed with:

```bash
./k8s/scripts/cleanup.sh
```

The five application services are `agent`, `catalog`, `knowledge`, `connectors`, and `rest`. They are deployed after shared configuration (`global-properties`) and their infrastructure dependencies (MongoDB, Qdrant, and others under `k8s/`). With no chart arguments, `deploy.sh` targets the full app set.

The detailed chart and operator workflow is documented in [`k8s/README.md`](../k8s/README.md), including:

- the default overlay and single-command deploy flow (with optional `--skip-build`)
- `lint.sh`, `template.sh`, and `status.sh`
- `seed-configs.sh`, `configs/infra/`, `configs/models/`, and `configs/agents/`
- the mounted `/config/application.properties`
- runtime placeholder resolution for `POD_NAME` and `POD_NAMESPACE`
- secret-backed bootstrap and production hardening expectations

## 8.2 Container Artifacts

`docker/Dockerfile` builds service images in JVM mode:

- base image: `eclipse-temurin:25-jre-alpine`
- exposes `8080`, `9000`, and `2552` (HTTP, gRPC, and Pekko remoting respectively — 2552 only
  matters for `agent`, the only service using Pekko clustering)
- `ENTRYPOINT` runs `java ... -jar /deployments/quarkus-run.jar` by absolute path — no `WORKDIR`
  needed, since nothing in the app reads relative to the process working directory anymore
- `configs/models`, `configs/agents`, and `configs/infra` are **not** copied into the image — they're
  read from the local checkout by `seed-catalog-configs.sh`/`seed-infra-configs.sh` at deploy time
  and pushed via the REST API/MongoDB directly, never read by the running container. Community
  expert agent configs are bundled as a classpath resource instead
  (`agent/core/src/main/resources/agents/community/experts/`, loaded via
  `ResourceUtils.listResourceNames`), so they ship with the jar rather than needing a filesystem copy.

Build examples:

```bash
docker build --build-arg SERVICE_MODULE=agent/core       -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=catalog/core     -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=knowledge/core   -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=connectors/core  -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=interfaces/rest  -f docker/Dockerfile .
```

## 8.3 Mongo and Infra Defaults

The shared connection property is `mongodb.connection.string` / `MONGODB_CONNECTION_STRING`.

In Kubernetes, the Mongo connection string comes from `MONGODB_CONNECTION_STRING`, while non-secret runtime settings come from an externally mounted `application.properties`.

Repositories default to the database names `AGENT_ENGINE` and `INFRA`.

## 8.4 Encryption for Secure Fields

Secure persistence model:

- annotate string fields/getters with `@Secure`
- the Mongo codec convention attaches `SecureStringCodec`
- values are encrypted/decrypted through `EncryptionService`

Encryption details:

- AES/GCM/NoPadding, 256-bit key (base64 in infra config)
- ciphertext stored with the prefix `enc::`
- key source: `INFRA.InfraConfig` with `type=encryption`

Helper script:

```bash
./scripts/generate-encryption-key.sh [mongodb-uri]
```

This generates and upserts encryption key material. Model `apiKey` and other secret fields rely on this — never commit plaintext secrets to `configs/`.

## 8.5 Session and Runtime Operational Behavior

- sessions are event-sourced through cluster-sharded `SessionActor`s and can be resumed by session ID
- model clients are pooled in the `model-provider` `RefCountedCache` with 15-minute idle eviction and auto-close on eviction
- deleting a session is propagated through `SessionDeletedEvent`

## 8.6 API Health and Bootstrap Expectations

Config-seed callers wait for the REST OpenAPI endpoint before upserting configs:

- `GET /q/openapi`

## 8.7 Logging and Threads

- virtual threads are used across execution paths (Quarkus and explicit executors)
- the gRPC server uses a virtual-thread executor with the prefix `agent-grpc-vt-`

## 8.8 Operational Risk Areas

1. Plugin jars are loaded dynamically from the filesystem; keep the plugin supply chain controlled.
2. The shell tool (`run_cmd`) is high risk even with the `rm` block; scope guardrail/tool exposure tightly.
3. gRPC dispatch is name-based; interface and method naming stability matters for distributed deployments.
