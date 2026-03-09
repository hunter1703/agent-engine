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
./gradlew :engine:test
./gradlew :interfaces:rest:test
./gradlew :connectors:core:test
```

## 7.3 Test Layout in Current Repo

### Unit tests

- `engine/src/test/java`
- includes service tests, model utils tests, tool tests, and validation tests

### Integration tests

- `engine/src/integrationTest/java`
- `interfaces/rest/src/integrationTest/java`

Integration tests use Quarkus test resources with Mongo/Redis test containers.

## 7.4 Running Locally (Scripted)

`deploy/deploy.sh` supports:

- `dev` mode: starts monolithic local interface (`:interfaces:local:quarkusDev`)
- `production` mode: builds/runs engine + rest as separate processes

Script options:

- `--build`
- `--clean`
- `--no-bootstrap`

`deploy/stop.sh` stops java services and dockerized infra.

## 7.5 Bootstrap Data in Dev

`interfaces:local` startup `Bootstrapper` imports configs from `configs/agents` and `configs/models`.

If IDs are missing in JSON, filenames are used.

## 7.6 Coding and Compatibility Notes

Observed repository conventions:

- enums include `UNKNOWN` and parser helpers
- validation is centralized in `ConfigValidationService` + rule validators
- service interfaces in `engine:api` are transport-agnostic and gRPC-capable
- secure persistence uses `@Secure` + custom Mongo codec convention

## 7.7 Known Inconsistency to Be Aware Of

GitHub workflow currently references `:interfaces:cli:test`, but active module in `settings.gradle` is `interfaces:local`. Treat workflow reference as stale unless module layout changes.
