# TODO

## Deferred Test Follow-ups
- Add regression tests for orchestrator mode validation matrix:
  - model requirement by mode (`TRANSFER` vs `SEQUENTIAL`/`PARALLEL`)
  - sub-agent constraints (`default` rejects `subAgentIds`, sequential/parallel require non-empty `subAgentIds`)
  - parallel policy validation paths (including quorum bounds for `stoppingPolicy=QUORUM`).
- Add integration tests for mixed context-strategy graph validation and compaction compatibility checks.
- Add integration tests for native confirmation resume adapter (non-empty text -> approval payload mapping).

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
