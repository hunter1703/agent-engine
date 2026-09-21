# TODO

1. Difference between adk's Langchain4j impl and ours

## AuthFilter: Authentication Not Yet Implemented

`interfaces/rest/.../filter/AuthFilter.java` is registered at `Priorities.AUTHENTICATION` and is
meant to be the gateway's single authentication entry point, but currently only assigns/propagates
`requestId` — it does not actually authenticate anything yet. Add the real auth check here rather
than introducing a second filter, so the requestId-before-rejection ordering guarantee stays intact
without relying on cross-filter priority tie-breaking (which JAX-RS leaves implementation-defined
for equal `@Priority` values).


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





## Provisioning: Docs Out of Date

`docs/08-deployment-and-operations.md` §Provisioning still describes the earlier design. Update it for:
- the request shape (`typeVsServer` with `defaultServerId` / `clientTypeVsServerId`, not `servers.<family>`);
- `infra.default-server.<server type>` keys (e.g. `MONGO_SERVER`, `ENCRYPTION_KEY`) instead of family names;
- `infra.vector.size` no longer existing;
- server configs going through the single `/internal/infra-config` endpoint with per-type setup hooks;
- microservice servers defaulting to `<service>-default`;
- provisioning stopping at the first failed step;
- default models being per-customer (`DefaultModels` in the customer's AGENT store, set from `defaultModels` in `customers.json`), replacing the `DEFAULT_MODELS` infra config in §3.9 of `03-configuration-reference.md`, `01-system-overview.md` and §8.4 of `08-deployment-and-operations.md`.
