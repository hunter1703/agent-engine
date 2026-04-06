# TODO
optimize or rewrite the [GRPCServerImpl.java](util%2Fms%2Fsrc%2Fmain%2Fjava%2Fcom%2Fagentengine%2Futil%2Fms%2FGRPCServerImpl.java)
add MANAGER orchestrator
production grade connectors


## Deferred Improvements
- Mark continuation intermediate answers as internal in BaseFlow so they don't surface to the user during plan-loop retries.

## Deferred Test Follow-ups
- Add regression tests for orchestrator mode validation matrix:
  - model requirement by mode (`TRANSFER` vs `SEQUENTIAL`/`PARALLEL`)
  - sub-agent constraints (`default` rejects `subAgentIds`, sequential/parallel require non-empty `subAgentIds`)
  - parallel policy validation paths (including quorum bounds for `stoppingPolicy=QUORUM`).
- Add integration tests for mixed context-strategy graph validation and compaction compatibility checks.
- Add integration tests for native confirmation resume adapter (non-empty text -> approval payload mapping).
- Add deployed end-to-end regression coverage for pause/resume SSE:
  - `POST /v1/agent/events` must emit a pausing tool call and return a resumable `threadId`/`toolCallId`
  - `POST /v1/agent/session/resume/events` must resume the same run and terminate without hanging
  - include a negative case for unknown `confirmationId` so the endpoint fails fast instead of leaving the stream open.
- Add a deterministic integration test that starts a session during node churn and asserts no event loss at the REST stream.
- Add regression test for BroadcasterEntity recovery with ADK confirmation payloads (`ToolConfirmationDeserializer` fix).


## Deferred Production Issues From E2E Testing

- **Add detection and eviction for wedged REST stream workers**: Because a blocked SSE request can leave a REST pod
  apparently `Running` while it is unhealthy for streaming traffic, add a custom readiness check that fails fast
  when the REST node cannot serve SSE safely, rather than relying on manual pod restarts. The preStop drain hook
  reduces the symptom window during rollouts but does not guard against wedged workers in stable deployments.
- **Rebuilt `core`/`runtime` images can crash on startup with a Quarkus/Vert.x linkage error**: During follow-up
  deployment of the serializer fix, new `agent-engine-core` and `agent-engine-runtime` pods failed immediately with
  `NoSuchMethodError: io.vertx.core.metrics.MetricsOptions.setFactory(io.vertx.core.spi.VertxMetricsFactory)` from
  Quarkus OpenTelemetry startup. This is a separate platform/build issue, not the original SSE bug, and it blocks
  validating new runtime/core images in-cluster. Investigate dependency convergence and image packaging before relying
  on freshly rebuilt `core`/`runtime` artifacts for production-like testing.



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
