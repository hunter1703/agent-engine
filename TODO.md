# TODO
separate utils:ms:client and utils:ms:server
optimize or rewrite the [GRPCServerImpl.java](util%2Fms%2Fsrc%2Fmain%2Fjava%2Fcom%2Fagentengine%2Futil%2Fms%2FGRPCServerImpl.java)
add MANAGER orchestrator


## Deferred Test Follow-ups
- Add regression tests for orchestrator mode validation matrix:
  - model requirement by mode (`TRANSFER` vs `SEQUENTIAL`/`PARALLEL`)
  - sub-agent constraints (`default` rejects `subAgentIds`, sequential/parallel require non-empty `subAgentIds`)
  - parallel policy validation paths (including quorum bounds for `stoppingPolicy=QUORUM`).
- Add integration tests for mixed context-strategy graph validation and compaction compatibility checks.
- Add integration tests for native confirmation resume adapter (non-empty text -> approval payload mapping).


## Session Actor — Deferred Correctness Gaps

- **`resumeChildCompletion` result injection**: When a parked session resumes after child completion,
  `resumeChildCompletion` should inject the child result as a function-response event into session
  history before re-running, so the ADK runner can continue from the `await_agent` call site.
  Without this, the ADK re-execution may not correctly deliver the child result to the LLM.

## Session Actor Rebuild — Deferred Correctness Gaps

- **ClusterSingleton for projection**: `SessionHistoryProjection` starts on every node. Wrap with
  `ClusterSingleton` or guard with Pekko cluster-aware startup to prevent duplicate projection
  runners.
- **CDI bean wiring for projection**: `SessionHistoryProjection`, `SessionHistoryProjectionHandler`,
  and `DefaultSessionHistory` are not yet wired as CDI beans (no `@ApplicationScoped`/`@Singleton`
  on the projection class with injected `MongoCollection`). Wire via Quarkus CDI and confirm startup.
- **Child actor dispatch**: `SessionActor` logs spawned children but does not yet dispatch
  `SpawnChild` / `SendChildTask` / `AwaitChildRun` through a real `ChildRegistry`. Implement
  `ChildRegistry` dispatch and cross-shard child lookup.
- **`SessionTopology` assembly**: `DefaultAgentRunner` receives a `SessionTopology` but the
  factory that constructs it from `AgentDefinition` + loaded tools has not been wired. Implement
  `SessionTopologyFactory`.
- **Recovery cleanup on restart**: After an actor recovers from a crash mid-run, any in-flight
  `RUNNING` execution state should be transitioned to `FAILED` at startup. Add a `RecoveryCleanup`
  guard in `SessionActor.forState`.
- **`atLeastOnce` idempotency**: `SessionHistoryProjectionHandler` uses `atLeastOnceAsync`, so
  `writeTurnEvents` may be called more than once for the same sequence. Add sequence-based
  upsert (`$setOnInsert`) to the MongoDB write to make it idempotent.
- **Projection offset store**: The JDBC offset store used by `SessionHistoryProjection` requires a
  `pekko_projection_offset_store` table. Add the schema migration / DDL init.
- **`SessionHistory` in `SessionServiceImpl`**: `SessionServiceImpl` now injects `SessionHistory`
  but the injection point is not guarded against `null` events from projections that haven't caught
  up. Add a documented eventual-consistency note and consider a fallback read from the actor.

## Deferred Agent-Engine Work For Agent-Console UX Parity
- Move Builder Contract generation from runtime warm-cache to dedicated build-time artifact generation task and wire it into module resource packaging.
- Extend access-policy enforcement from top-level fields to full JSON-pointer nested enforcement (arrays/objects) for strict mode-safe updates.
- Expand `/schemas/agent` layout contract for complete wizard coverage of orchestrator/runtime/session/guardrail rule editing with stronger cross-field validations.
- Add typed guardrail-rule UI support contract in schema (`rules.items` discriminator mapping for rule subtype-specific fields and defaults).
- Add session pause summary in session DTO payload:
  - `pause: { paused, reason, prompt, options, requestedAt }`
  - keep existing event-history fetch options unchanged.
- Add connector/connection management APIs (deferred for later phase):
  - catalog asset types for connector and connection
  - connection upsert/delete endpoints
  - `POST /schemas` support for `assetType=connection_inputs`.
- Add integration tests for:
  - schema contract shape (`/schemas/agent`, `connection_inputs`)
  - catalog endpoints for connector/connection
  - session pause summary serialization and resume event streaming.
