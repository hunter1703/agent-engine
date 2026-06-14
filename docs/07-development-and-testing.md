# 7. Development and Testing

## 7.1 Toolchain and Build Conventions

Configured via the `buildSrc` conventions plugin:

- Java toolchain: 25
- preview features enabled for compile/test/javaexec
- Spotless formatting enabled
- Jandex indexing integrated into jar/quarkus tasks
- integration-test source set/task configured (`src/integrationTest/java`)

## 7.2 Gradle Commands

Common commands:

```bash
./gradlew clean build
./gradlew test
./gradlew integrationTest
```

Targeted module examples:

```bash
./gradlew :agent:core:test
./gradlew :catalog:test
./gradlew :interfaces:rest:test
./gradlew :connectors:core:test
./gradlew :knowledge:core:test
```

## 7.3 Test Layout

### Unit tests

- `agent/core/src/test/java`
- `catalog/src/test/java`
- `interfaces/rest/src/test/java`

### Integration tests

- `interfaces/rest/src/integrationTest/java`

Integration tests use Quarkus test resources with MongoDB and Redis Testcontainers.

## 7.4 Deployment Workflow

Kubernetes is the primary deployment path:

- `./k8s/scripts/deploy.sh` installs the standard release set
- `./k8s/scripts/deploy.sh agent catalog rest` deploys a subset while preserving dependency order
- `./k8s/scripts/cleanup.sh` removes the standard release set

`deploy.sh` builds the required service images automatically. `k8s/scripts/build-images.sh` remains available for manual or registry-push workflows, and container images are built from `docker/Dockerfile`.

## 7.5 Seeding Config Data

Configs are seeded through the REST API by the deployment scripts, not imported in-process:

```bash
./k8s/scripts/seed-configs.sh
```

This upserts `configs/infra` into `INFRA.InfraConfig`, and `configs/models` and `configs/agents` through the REST API. The asset ID is the JSON filename without its extension.

## 7.6 Coding and Compatibility Notes

Repository conventions:

- enums include `UNKNOWN` and `valueOfOrDefault` parser helpers
- validation is centralized in `ConfigValidationService` + rule validators
- service interfaces in `catalog:api` and `agent:api` are transport-agnostic and gRPC-capable
- secure persistence uses `@Secure` + a custom Mongo codec convention

## 7.7 Module Wiring Note

Only `interfaces:rest` is wired as an interface module in `settings.gradle`. An `interfaces:local` directory exists on disk but is not included in the build; historical CI or docs references to `interfaces:cli` are stale.
