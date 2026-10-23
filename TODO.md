# TODO


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

## Access control: remove the `systemSession` cookie

`AuthFilter` treats a request carrying the literal cookie `session=systemSession` as `Caller.SYSTEM`
for the customer of its origin, and logs the cookie value. It is a stopgap so
`python/ingest_knowledge.py` and `python/mitm.py` can call the REST API. Remove it, and
the logging of cookie values with it, once those scripts log in the way
`deploy/scripts/ci/seed_app.py` does (`/v1/auth/login` with a seed user).

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

## Deploy: `deployae deploy` goes on after a failed build

When the Gradle build step fails (e.g. a compile error), `deployae deploy` still rolls out and reports
completion, with the pods left on the previous image. Stop the deploy at the first failed step.
Also, an image tag taken from the git commit is reused for a dirty working tree, so the pods are not
replaced; a tag that follows the working tree's content would avoid it.

## Models: carry thinking signatures through `LangChain4jModel`

`LangChain4jModel` maps a model turn's thought parts to `AiMessage.thinking()` and back, but drops a
part's `thoughtSignature`. That is safe for the providers it serves today (`OPEN_AI`,
`Z_AI`), whose OpenAI-compatible APIs have no signatures; Gemini goes through ADK's own adapter,
which keeps them. A provider that signs its reasoning and needs it back unchanged in tool-calling
turns (e.g. Anthropic, whose LangChain4j module keeps the signature in `AiMessage.attributes()`)
would lose it. When such a provider is added through LangChain4j, map `Part.thoughtSignature` to
and from the attributes its LangChain4j module uses.

## Time zone: re-resolve it when a paused run resumes

A user's time zone is resolved once, when a run starts (`SessionActor.startNextQueuedMessage`), and
stamped with the run's message. A run that resumes after a pause keeps that zone: tool results after
the resume still use it (`ToolTimingPlugin`) even if the user's `timeZone` changed while the run
was parked, and the model is not told, until the next run starts.

The zone may change only when a run starts or resumes, never within a segment of a run. At a resume
(`continueRun`, and recovery of a `CONTINUING` session): resolve the zone of the user the turn runs
as through the user cache (`SessionActor.timeZoneOf`); persist it in `ContinuingFact`, replacing the run's zone in the
state; record the zoned time with the stored resume events as with user messages, so `latestZone` finds it
after a rebuild; and, only when the zone changed, deliver a stored runtime message to the model
("The user's time zone is now X (it was Y)"). A resume's events are confirmation responses, which
ADK leaves out of the model's request, so that message cannot ride on them.


## Planning: tasks cannot be reordered

A plan's tasks run in the order they were created, and `add_task` only appends. A model that finds
the order wrong can only abandon the remaining tasks and add them again in the order it wants.
Needs a way to move a not-yet-started task, with the same tree validation `add_task` has.


## Knowledge: a long non-indexed item cannot be read past its first 50,000 characters

The knowledge search tool returns the first 50,000 characters of a non-indexed text item with its
`total_chars`, but takes no offset to read the rest. It needs the same `offset` / `next_offset`
paging the notebook read has.


## Tool results: file tools still use the earlier shape

`read_file`, `list_dir` and `grep_files` return `has_more` and counts next to `next_offset`. Bring
them to the shape in the tool design guidelines (`next_offset` present while more remains).


## Logging: debug messages build JSON even when debug is off

`LOG.debug("…", JsonUtils.toJson(event))` computes the JSON on every call whatever the log level.
It is in `BaseFlow` (every event, twice), `SessionActor.publishEvent` and the topology dumps around
it. `LangChain4jModel` also logs the whole converted request at INFO on every model call. Guard
these with `isDebugEnabled()` or log cheap arguments.


## Some violations are corrected after the user has seen the bad reply

The text of a reply is held back until the reply is complete while an output guardrail is enabled
or a plan has open tasks. A reply that breaks any other rule (JSON that does not match the response
format, an off-topic reply) is still streamed and stored first; the violation then reaches the model
as a notice and it answers again, so the user sees both. Such a reply should be kept out of the
user's stream, the way compaction events are (`EventUtils.markAsInternal`), while the model still
sees it.
