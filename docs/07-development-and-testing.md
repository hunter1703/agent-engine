# 7. Development and Testing

## 7.1 Toolchain and Build Conventions

Configured via `buildSrc` conventions plugin:

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
./gradlew :runtime:test
./gradlew :core:test
./gradlew :interfaces:rest:test
./gradlew :connectors:core:test
```

## 7.3 Test Layout in Current Repo

### Unit tests

- `runtime/src/test/java`
- `core/src/test/java`
- `interfaces/rest/src/test/java`

### Integration tests

- `interfaces/rest/src/integrationTest/java`

Integration tests use Quarkus test resources with Mongo/Redis test containers.

## 7.4 Deployment Workflow

This repository now treats Kubernetes as the primary deployment path.

Use:

- `./k8s/scripts/deploy.sh` to install the standard release set
- `./k8s/scripts/deploy.sh runtime core rest` to deploy a subset while preserving dependency order
- `./k8s/scripts/cleanup.sh` to remove the standard release set

`deploy.sh` builds the required service images automatically. `k8s/scripts/build-images.sh` remains available for manual or registry-push workflows, and container images are still built from `docker/Dockerfile`.

## 7.5 Bootstrap Data in Dev

`interfaces:local` startup `Bootstrapper` imports configs from `configs/agents` and `configs/models`.

If IDs are missing in JSON, filenames are used.

## 7.6 Coding and Compatibility Notes

Observed repository conventions:

- enums include `UNKNOWN` and parser helpers
- validation is centralized in `ConfigValidationService` + rule validators
- service interfaces in `core:api` and `runtime:api` are transport-agnostic and gRPC-capable
- secure persistence uses `@Secure` + custom Mongo codec convention

## 7.7 Known Inconsistency to Be Aware Of

GitHub workflow currently references `:interfaces:cli:test`, but active module in `settings.gradle` is `interfaces:local`. Treat workflow reference as stale unless module layout changes.
