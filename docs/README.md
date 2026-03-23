# Agent Engine Documentation (From Scratch)

This documentation set is a complete rewrite based on the current repository implementation.
It is intentionally independent from the existing `README.md` and `docs/` content.

## Document Map

1. [`01-system-overview.md`](./01-system-overview.md)
2. [`02-runtime-architecture.md`](./02-runtime-architecture.md)
3. [`03-configuration-reference.md`](./03-configuration-reference.md)
4. [`04-rest-and-grpc-apis.md`](./04-rest-and-grpc-apis.md)
5. [`05-tooling-and-plugins.md`](./05-tooling-and-plugins.md)
6. [`06-connectors-framework.md`](./06-connectors-framework.md)
7. [`07-development-and-testing.md`](./07-development-and-testing.md)
8. [`08-deployment-and-operations.md`](./08-deployment-and-operations.md)
9. [`09-practical-recipes.md`](./09-practical-recipes.md)
10. [`10-protocol-and-guarantees.md`](./10-protocol-and-guarantees.md)
11. [`11-builder-contract-for-frontend.md`](./11-builder-contract-for-frontend.md)

## Scope Notes

- Repository root: `agent-engine`
- Java toolchain: Java 25 with preview features
- Framework baseline: Quarkus 3.31 + Google ADK + LangChain4j
- Persistence baseline: MongoDB (default local binding on `localhost:27018` in this repo)

## Important Reality Checks

- The Gradle module currently included is `interfaces:local` (not `interfaces:cli`).
- Some CI and historical docs references still mention `interfaces:cli`; treat those as stale.
- Runtime-facing APIs and behaviors below are derived from current source under:
  - `runtime/`
  - `runtime/api/`
  - `runtime/actor/`
  - `core/`
  - `core/api/`
  - `interfaces/rest/`
  - `connectors/core/`
