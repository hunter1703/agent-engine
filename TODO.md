# TODO



## Access control: no authenticated surface for seeding, users, roles or sharing

`deploy/scripts/ci/seed_app.py` seeds connections, models and agents through the REST API without
logging in, which the REST `AuthFilter` now rejects. There is also no REST endpoint for creating
users, assigning global roles or sharing an asset, so `AclService.updateSharing` is only
reachable from agent tools. Until one exists, nothing shares tenant-wide connections such as
`default_brave_connection` (used by `WebSearchTool`) with the `Agent` principal, so agents can't read
them. Needed: an admin identity for seeding, and user/role/sharing endpoints.


## Provisioning: Docs Out of Date

`docs/08-deployment-and-operations.md` §Provisioning still describes the earlier design. Update it for:
- the request shape (`typeVsServer` with `defaultServerId` / `clientTypeVsServerId`, not `servers.<family>`);
- `infra.default-server.<server type>` keys (e.g. `MONGO_SERVER`, `ENCRYPTION_KEY`) instead of family names;
- `infra.vector.size` no longer existing;
- server configs going through the single `/internal/infra-config` endpoint with per-type setup hooks;
- microservice servers defaulting to `<service>-default`;
- provisioning stopping at the first failed step;
- default models being per-customer (`DefaultModels` in the customer's AGENT store, set from `defaultModels` in `customers.json`), replacing the `DEFAULT_MODELS` infra config in §3.9 of `03-configuration-reference.md`, `01-system-overview.md` and §8.4 of `08-deployment-and-operations.md`.


## Chaos Testing: Open Design Questions Carried From the Original Spec

Tracked while implementing `.kiro/specs/chaos-testing/` (see the plan's Phase 1-5 breakdown):

- **PromQL queries**: `chaos/core/metrics/MetricsQueries.defaults()` ships best-effort PromQL
  strings (Micrometer `http_server_requests_seconds_*`, a guessed `mongodb_op_latency_seconds`, a
  guessed `pekko_persistence_journal_write_duration_seconds`). These need validating against
  whatever exporters actually run in the cluster (mongodb-exporter naming, Pekko persistence
  metrics naming) — queries are configurable via the `MetricsQueries` record specifically so this
  doesn't require a id change, just a config update.
- **`EventJournalValidator` replay hook**: the round-trip idempotence check (Task 12.3) needs a
  `SessionActorState.applyEvent()`-equivalent entry point to replay an event stream outside the
  actor. Confirm this method exists or add it when implementing Phase 3 validation.
- **`OrchestrationMode` structure**: Tasks 17 and 20 (multi-agent orchestration chaos, tool
  execution resilience) reference `OrchestrationMode.SEQUENTIAL`/`PARALLEL` — confirm the actual
  orchestrator agent's mode enum/structure in `agent:infra` before wiring these tests.
- **No circuit breaker around LLM/connector calls**: `DefaultConnectorExecutor` only does
  retry-with-backoff (`connectors/core/src/main/java/com/agentengine/connectors/core/runtime/DefaultConnectorExecutor.java`).
  Chaos experiments against `LLM_PROVIDER_UNAVAILABLE`/`CONNECTOR_FAILURE` will likely show retries
  compounding latency under sustained outage rather than failing fast. Per plan scope this is
  observed and reported, not fixed, in the chaos-testing work — revisit if experiment results show
  it's a real production risk.


## Image Tools: Shadows/Highlights Local Adaptation

Lightroom's PV2012 Shadows and Highlights sliders use edge-aware local tone mapping (confirmed by
Adobe's Eric Chan: they use edge-detection algorithms that took months to optimize to near-real-time).
Our implementation uses the darktable shadhi.c algorithm (GPL): Gaussian base layer extraction +
inverted overlay blend weighted by luminance zone masks. This matches the visual behaviour of
darktable's shadows-and-highlights module. A guided filter (He et al. 2013) would reduce halos
further but is not yet implemented.

## Image Tools: 100MP Tiling Scalability for JPEG Decoding

`ImageUtils.processTiled` uses `ImageReader.setSourceRegion` to read tiles. For JPEG, this does
not perform partial decoding — the full image is decoded and the region is copied out. At 100MP
with 256×256 tiles (~1,560 tiles) and virtual thread concurrency, this means up to 1,560
simultaneous full-image decodes of the same file. Results are correct but CPU and memory usage
are extreme. Fix: decode once into a shared `BufferedImage`, distribute tiles from the in-memory
buffer. Trades memory for CPU. Track as a known limitation until 100MP use cases are confirmed.


## Database: Migrate from Qdrant to Vespa

We will eventually migrate to Vespa which supports server-side joins and native conditional updates. This migration will resolve two fundamental limitations in our current Qdrant implementation:
1. `findChunks` without IDs currently puts every readable knowledge ID into one unbounded Qdrant filter, which will break at scale.
2. Qdrant lacks conditional writes, meaning access-list updates and entity saves are implemented as read-then-write operations, creating a race condition where concurrent saves can overwrite new access lists.

## Move to soft deletions instead of hard deletions

Eventually, we should move from hard-deleting entities from the database to soft deletions (e.g., setting a `deleted = true` flag or a `deletedAt` timestamp).

## Optimize AgentSchedule reconciliation when soft deletions are introduced

Once we move to soft deletions, we can eliminate the expensive Pass 2 (orphaned job scanning) in `ReconcileAgentSchedulesJob`. Since deleting a schedule will simply update its soft-delete timestamp, those deleted schedules will be caught by Pass 1 (fetching schedules updated since `lastExecutionTime`). Pass 1 can then explicitly cancel the jobs for any schedule marked as deleted, making the entire reconciliation process perfectly bounded by the `lastExecutionTime`.

## Access control: the gRPC transport trusts the caller's context

Install a service mesh like Linkerd (mTLS) and apply network policies to disallow arbitrary pods from making unverified gRPC calls to internal services.

## Parallel orchestrator sub-agents act as the orchestrator's session

In PARALLEL mode, `OrchestratorAgentFactory` builds an orchestrator's `subAgentIds` into its own
ADK agent tree, so they run inside the orchestrator's runner and session rather than in sessions of
their own. Building them reads their configs as the orchestrator — and, for a sub-agent that is
itself an orchestrator, its sub-agents too, which are shared with that sub-agent, not with the
orchestrator — and their tool calls run as the orchestrator's session principal, reaching what was
shared with the orchestrator instead of what was shared with each sub-agent. Decide how a parallel
sub-agent acts (its own child session, as MANAGER-mode sub-agents get, or the transferred-agent model)
and make the build and the runtime context follow it.
