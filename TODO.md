# TODO

## Deferred Production Follow-ups
- Resolve guardrail plugin wiring mismatch in execution startup (`AgentExecutionServiceImpl` calls `GuardrailPlugin.build(...)` but current plugin API does not expose that method), then restore green `compileJava`.
- Add app-level/user-facing diagnostics endpoint or startup report for context-strategy graph compatibility failures (mixed compaction vs non-compaction, compaction setting mismatches).

## Deferred Test Follow-ups
- Add regression tests for orchestrator mode validation matrix:
  - model requirement by mode (`TRANSFER` vs `SEQUENTIAL`/`PARALLEL`)
  - sub-agent constraints (`default` rejects `subAgentIds`, sequential/parallel require non-empty `subAgentIds`)
  - parallel policy validation paths (including quorum bounds for `stoppingPolicy=QUORUM`).
- Add integration tests for mixed context-strategy graph validation and compaction compatibility checks.
- Add integration tests for native confirmation resume adapter (non-empty text -> approval payload mapping).
