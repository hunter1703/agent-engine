# TODO



## AuthFilter: Authentication Not Yet Implemented

`interfaces/rest/.../filter/AuthFilter.java` is registered at `Priorities.AUTHENTICATION` and is
meant to be the gateway's single authentication entry point, but currently only assigns/propagates
`requestId` — it does not actually authenticate anything yet. Add the real auth check here rather
than introducing a second filter, so the requestId-before-rejection ordering guarantee stays intact
without relying on cross-filter priority tie-breaking (which JAX-RS leaves implementation-defined
for equal `@Priority` values).


## Context Propagation: SessionActor / Pekko Actor Boundary

`com.agentengine.util.common.context.Context` carries `requestId` across the REST edge
(`AuthFilter`) and the custom gRPC layer (`MicroServiceInvocationHandler`/`GRPCServerImpl`), so any
synchronous call chain correlates back to the originating request in logs.

`Context` is backed by a plain JDK `ScopedValue` (`current()`/`run()`/`call()`) — no OTel dependency
in `util:common` at all. `ScopedValue` only binds for the duration of one `run()`/`call()` block,
which is exactly what `GRPCServerImpl`/`MicroServiceInvocationHandler` need (they own the call
they're wrapping) but is *not* enough for `AuthFilter`, which sets `Context` up in one JAX-RS filter
method and reads it back in a separate one — no shared lambda to bind a `ScopedValue` around. That
gap is bridged by two extra pieces, matching how `ua`'s equivalent `AuthFilter` solves the identical
problem:
- `RequestContextProvider` (`@RequestScoped` CDI bean) — `AuthFilter` parks the resolved `Context`
  here; this is *not* itself ambient/`ScopedValue`-backed, just a per-request mutable slot.
- `ContextAwareInterceptor` (`@AroundInvoke` CDI interceptor, bound via the `@ContextAware`
  annotation applied to REST resource classes) — re-enters `Context` via `context.call(...)` wrapped
  around `InvocationContext.proceed()`, i.e. the resource method body. This is the one point in the
  request pipeline that *does* own the call as a single block (`proceed()` is a plain method call
  the interceptor makes itself), unlike the JAX-RS filter pair.

The gRPC hop is a bespoke proto field (`Request.context`, JSON-encoded whole `Context`, read/written
directly in `MicroServiceInvocationHandler`/`GRPCServerImpl`), not a header — a header would need a
`ServerInterceptor` plus a second Context type (`io.grpc.Context`) just to carry the value from the
interceptor into the handler method, whereas a message field is already a plain parameter on the
method that needs it. Matches `ua`'s equivalent gRPC layer exactly. The whole `Context` travels as
one JSON blob, so adding fields (e.g. a future nested `UserContext`) needs no proto changes.

This does **not** yet reach `SessionActor` (`agent/core/.../session/SessionActor.java`) or its
detached `update-title-task`/`update-memory-task` virtual-thread executors, which is why
`MicroServiceInvocationHandler` calls made from session actor processing will still show no
requestId. Wiring that in requires:

- Adding a single `Context context` field — the whole object, not individual scalars pulled out of
  it — to the relevant `SessionCommand` types (`StartCommand`, `SendMessageCommand`, etc.), so it
  survives Pekko cluster-sharding serialization across nodes. Same principle as the gRPC `Request`
  field: whichever transport carries `Context`, it carries the whole record, so a future field (e.g.
  nested `UserContext`) never means touching every transport again. Pekko already serializes commands
  via Jackson (`pekko-serialization-jackson`), and `Context` is already a plain Jackson-friendly
  record, so this is a field addition, not a new codec.
- Re-binding `Context` from that field when `SessionActor` handles the command, and again inside
  the `update-title-task`/`update-memory-task` runnables (captured at submission time).
- A product decision on what a "requestId" even means during **event replay**
  (`persistencePhase: replay-evt` in the logs) — there is no live request behind replayed events,
  so `Context` should likely stay unbound there rather than fabricate one. Don't paper over this.

Deferred separately from the REST/gRPC wiring above because it touches Pekko persistence/replay
semantics and cross-node command serialization — higher risk, needs its own change.

Also unaddressed: async continuations inside `GRPCServerImpl.executeInternal` (the
`CompletionStage.whenComplete` branch) may run on a different thread than the one `dispatch()`
bound `Context` on, so streaming/async service results can still log without a requestId even
though the initial dispatch had one.


## Chaos Testing: Open Design Questions Carried From the Original Spec

Tracked while implementing `.kiro/specs/chaos-testing/` (see the plan's Phase 1-5 breakdown):

- **PromQL queries**: `chaos/core/metrics/MetricsQueries.defaults()` ships best-effort PromQL
  strings (Micrometer `http_server_requests_seconds_*`, a guessed `mongodb_op_latency_seconds`, a
  guessed `pekko_persistence_journal_write_duration_seconds`). These need validating against
  whatever exporters actually run in the cluster (mongodb-exporter naming, Pekko persistence
  metrics naming) — queries are configurable via the `MetricsQueries` record specifically so this
  doesn't require a code change, just a config update.
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




