# TODO

## No Tier Overlay Provides `replicas`/`resources`/`pdb.enabled`/`persistence.size` for Ad-Hoc/Local Runs (ACTION NEEDED)

There is deliberately no "local" tier — `tier` is only meaningful for real, named deployment
profiles (currently just `prod`); running any chart without `-t prod` renders with `tier` unset
(fine — `app.kubernetes.io/instance` just falls back to the plain service name). But
`replicas`/`resources`/`pdb.enabled` (app charts), `resources` (`mongodb`/`postgres`/
`localstack`/`qdrant`), and `persistence.size` (`mongodb`/`postgres`/`qdrant`) are still wired
through Helm's `required` with no base-chart default — by design, so nothing can silently deploy
with guessed sizing or skip persistence entirely — which means an untiered/local render still
fails on those specific fields with no `-t prod`. This is expected under the current design
(there's no fallback sizing profile to fall back to), not a bug, but worth deciding: either accept
that any real `helm install`/`template` always needs `-t prod` (or a future named tier) even for
ad-hoc local testing, or add plain (non-`required`) small defaults for these specific fields
directly in each chart's own base `values.yaml` so an untiered render is usable out of the box.

## MongoDB and Postgres Run With No Authentication at All (ACTION NEEDED before any non-trial deploy)

Both `mongodb` and `postgres` had their whole auth mechanism removed (no more `auth:` block, no
Secret, no `requireExistingSecret`/`existingSecret` escape hatch): Mongo runs unauthenticated
(no `MONGO_INITDB_ROOT_*` env vars set), Postgres runs with `POSTGRES_HOST_AUTH_METHOD=trust`
(connects as the default `postgres` superuser, password ignored). `configs/infra/sql-infra-configs.json`
now sets `jdbcUser: "postgres"`, `jdbcPassword: ""` to match. This was a deliberate simplification
on the reasoning that both are ClusterIP-only, in-cluster (`infra` namespace), never
externally reachable — so username/password auth was pure duplication of network-level isolation
with no defense-in-depth benefit, at the cost of two Secrets, an `authSecretName` helper each, and
a prod-only `requireExistingSecret` toggle nothing else in the repo replicated (`localstack`/`qdrant`
never had auth either). It also incidentally fixed a real pre-existing bug: `infra.mongodb.uri` in
`k8s/global-properties/tiers/prod/values.yaml` never had credentials embedded, so the app would have
failed to authenticate against a real (auth-enabled) prod Mongo before this change.

Mitigated (not eliminated) by adding a `NetworkPolicy` per infra chart (`k8s/{mongodb,postgres,
localstack,qdrant}/templates/networkpolicy.yaml`, via new `agent-engine.base-infra.appNamespace`
helper): each restricts ingress to its own port(s), only from the `agent-engine` app namespace
(matched via the auto-populated `kubernetes.io/metadata.name` namespace label). Everything else
in-cluster is denied by default once a policy selects a pod.

**Caveat (ACTION NEEDED to actually get protection, not just the appearance of it)**: NetworkPolicy
objects are accepted by the API server regardless of whether anything enforces them — enforcement
is entirely up to the CNI plugin. `k8s/README.md` documents Docker Desktop Kubernetes as this repo's
actual deploy target, and **Docker Desktop's built-in CNI does not enforce NetworkPolicy** unless a
policy-capable CNI (e.g. Calico) is separately installed. So on the environment this repo is
actually deployed to today, these policies are currently accepted but not enforced — verify
enforcement (`kubectl exec` into an unrelated pod and confirm `mongodb`/`postgres`/`localstack`/
`qdrant` become unreachable) before treating this as a real mitigation rather than documentation of
intent. Will enforce correctly on most managed cloud Kubernetes (GKE, EKS with a CNI add-on, AKS
with Azure/Calico networking) without further changes.

Also unverified: mongodb/postgres's `tcpSocket`-based liveness/startup probes are kubelet-initiated
TCP connections to the pod IP — most CNI policy implementations exempt kubelet-origin traffic from
pod-selector ingress rules, but this isn't guaranteed by the NetworkPolicy spec itself. If probes
start failing after enforcement is confirmed working, this is the first thing to check.

## LocalStack Endpoint Uses `localhost`, Won't Work From Inside a Pod

`configs/infra/cloudstorage-infra-configs.json`'s `endpointUrl: http://localhost:4566` resolves to
the calling pod itself when read from inside `catalog`/`connectors`/etc., not the LocalStack
service — `localhost` only ever worked for non-k8s local JVM dev. Pre-existing, unrelated to this
session's namespace split; cloudstorage likely doesn't currently work when deployed to k8s at all.

## `rest` Prod CORS Origin Is Set to localhost (ACTION NEEDED before a real prod deploy)

`k8s/rest/tiers/prod/values.yaml`'s `quarkus.http.cors.origins` is `http://localhost:3000` — set that
way deliberately so `apply-charts.sh -t prod` works against `agent-console` (`npm run dev`) on a
local Docker Desktop cluster, since that's the only environment this gets deployed to today. It
used to be a `REPLACE_WITH_PROD_DOMAIN` placeholder forcing this to be revisited; now that the
placeholder is gone, nothing will flag it automatically. Before ever deploying to a real production
domain, this must be changed to that domain's real `https://` origin.

## Verify `knowledge`'s Prod Sizing

`k8s/knowledge/tiers/prod/values.yaml` was added with `replicas: 2`, `pdb.minAvailable: 1`, and
500m/1Gi requests-limits, modeled directly on `catalog`/`connectors` (same shape: Deployment,
http+grpc, thin API layer with no heavy in-process workload). Worth a real review against actual
knowledge-service load/traffic once it's observable, rather than assumed correct forever.

## Rotate the Brave API Key That Was Previously Committed in Plaintext (ACTION NEEDED)

`brave_web_search.json` used to contain a live-looking Brave Search API key in plaintext
(`auth.apiKeyTemplate: "BSA87VeS50GKnb4pp_PecDqzdPcLIN0"`). The config has been rewritten to reference
`{{ env.BRAVE_API_KEY }}` instead, but the old value is still recoverable from git history. Rotate the
key if it was ever live, and set `BRAVE_API_KEY` in the deployment environment instead.

## DuckDuckGo Connector: No Response-Mapping Layer, `executeDuckDuckGoLookup` Is Dead Code

The pre-rewrite connector schema had a `responseMapping` step that transformed DuckDuckGo's raw
Instant Answer API fields (`AbstractText`, `AbstractURL`, `AbstractSource`, `Heading`, `RelatedTopics`)
into the shape `WebSearchTool.executeDuckDuckGoLookup` expects (lowercase `abstract`, `url`, etc.). The
new `HttpConnectorExecutor` has no equivalent — it returns the parsed response body as-is. This isn't
breaking anything today because `executeDuckDuckGoLookup`
(`agent/infra/src/main/java/com/agentengine/agent/infra/tools/web/WebSearchTool.java`) is unreachable —
`execute()` only calls `executeBraveSearch`. If DuckDuckGo fallback is ever wired back in, either add a
response-mapping capability to the connector layer, or update `executeDuckDuckGoLookup` to read
DuckDuckGo's actual raw field names.

## Connector Rewrite: Orphaned Tests Reference Deleted Classes (found during code review)

`connectors/core/src/test/java/com/agentengine/connectors/core/template/DefaultTemplateResolverTest.java`,
`GroovySandboxEvaluatorTest.java`, and `connectors/core/src/test/java/com/agentengine/connectors/core/validation/DefaultConnectorConfigValidatorTest.java`
were left behind when the connector rewrite (`24ad9490`) deleted
`DefaultTemplateResolver`/`GroovySandboxEvaluator`/`DefaultConnectorConfigValidator` and the whole
`config`/`pagination`/`template`/`validation` packages under `connectors/core`. These tests no longer
compile and block `./gradlew :connectors:core:compileTestJava` / `test`. Not fixed here (tests were
explicitly out of scope for this pass) — either delete them or rewrite them against the new
`HttpConnectorSpec`/`TemplatedHttpConnectorSpec`/`GroovyTemplateProcessor` design.

## util:pekko Test Suite Does Not Compile (pre-existing, found while adding chaos testing)

`ActorSystemProviderTest`, `SingleChannelTest`, and `BroadcasterStateTest` reference APIs that no
longer exist: `PekkoConfig.setHostname`/`setPort`, a 4-arg `ActorSystemProvider.buildConfig`,
`PekkoEventChannel`'s old constructor/`entity()` shape, `EventSubscription.cancel()`, and
`BroadcasterState.withSubscription`/`withoutSubscription`/`subscriptions()`. `PekkoConfig.java` was
last modified 2026-04-15, after these tests (2026-04-03) — the tests were not updated when the
config/channel APIs were refactored. This blocks `./gradlew :util:pekko:test` entirely (main source
compiles fine; only `compileTestJava` fails), which in turn blocks running the new
`MessageFaultInterceptorTest` added for chaos testing via Gradle. Not fixed here — out of scope for
the chaos-testing work — but worth a follow-up pass to update or remove the stale tests.

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

## Vector Store: Multi-Vector Search with RRF Fusion

`QdrantVectorStore.findBySemanticQueryInternal` currently handles only a single `SEMANTIC_SEARCH`
filter. Qdrant's Query API (v1.10+, available in client 1.13.0) supports multi-vector fusion via
`QueryPoints` with `PrefetchQuery` entries and `Fusion.RRF` — all in a single round trip.

When multiple `SEMANTIC_SEARCH` clauses are present in a query (e.g. `textVector` + `imageVector`),
each should become a `PrefetchQuery` with its own vector and `using` field, and the top-level
`Query` should set `fusion = RRF`. Single-vector queries can also migrate to `queryAsync` from
`searchAsync` for API consistency.

Replace `SearchPoints` + `searchAsync` with `QueryPoints` + `queryAsync` throughout
`findBySemanticQueryInternal`.



The Qdrant `knowledge` collection must be created at deployment time with the correct vector
dimension matching the configured embedding model (e.g. 768 for `nomic-embed-text`). The
application no longer calls `ensureCollection` at runtime — it assumes the collection exists.

Add a Helm chart init-container or a `k8s/infra/qdrant/init-collection.sh` script that calls
the Qdrant REST API to create the collection before the knowledge service starts:

```bash
curl -X PUT http://qdrant:6333/collections/knowledge \
  -H 'Content-Type: application/json' \
  -d '{"vectors": {"size": 768, "distance": "Cosine"}}'
```

The dimension should be parameterised via a Helm value so it can be changed without modifying
the script.

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



## SessionActor: Pause Propagation to Parent — No Proactive Retry on Pure Message Loss (DEFERRED, partially fixed)

### Status: PARTIALLY FIXED — child-side proactive retry still deferred

### Background
When a child session pauses waiting for a human confirmation, it tells its parent via
`PauseChildCommand` so the parent can later route the human's answer down to the right child. This
is a routing-table update (`pauseState.pendingConfirmationIdVsChildSessionId`), **not** a change to
the parent's own `SessionState` — parent and child run independently; a child pausing never
suspends or blocks the parent's own execution (`SessionActorState.childPaused()` passes
`sessionState` through unchanged, unlike `selfPaused()` which explicitly sets `SessionState.PAUSED`
for the actor's own pause).

### Fixed
`SessionActor.childPaused()` used to only persist the routing entry for
`TRIGGERED_RUN, RESUMING, PAUSED, RUNNING`, silently dropping it (while still replying
`Done.done()`, a successful-looking ack) whenever it arrived while the parent was `IDLE`. Since
`SessionState` has exactly five values and the other four were already matched, that `default`
branch could only ever mean `IDLE` — and `childPaused()` never actually depended on the parent's
own state to decide whether to record the entry. Simplified to always persist: an `IDLE` parent
(its own work already done) can still legitimately have a child waiting on a human, and the
external status flipping back to `PAUSED` in that case is the *correct* signal — the overall
interaction isn't really done while a child is still waiting on a human answer.

This closes the most likely real-world trigger: `onRecoveryCompleted`'s `case PAUSED` branch
already re-sends `PauseChildCommand` for everything in `pendingExternalSelfConfirmationIds` when
the child actor restarts (pods restart routinely in k8s), and that retry now succeeds even if the
parent has gone `IDLE` in the meantime.

### Still Open
`propagateSelfPauseToParent` still does a single `ask` with no retry of its own — on timeout it
just `LOG.error`s. If the message is genuinely lost (not just delayed) and the child actor never
happens to restart afterward, there's still no path to re-send it: the only retry is the
opportunistic one tied to child restart, not a proactive schedule. Needs the child to keep retrying
on its own timer until it gets confirmation the parent durably persisted the entry (not just that
the `ask` succeeded) — non-trivial to add cleanly, and now that the parent-side drop is fixed, this
requires two unlikely events at once (pure message loss + the child never restarting for any other
reason for the lifetime of the pause), so the remaining practical risk is low.

### Affected Files
- `agent/core/src/main/java/com/agentengine/agent/core/session/SessionActor.java` —
  `propagateSelfPauseToParent()` (still needs the retry timer), `childPaused()` (fixed)
- `agent/core/src/main/java/com/agentengine/agent/core/session/state/SessionActorState.java` —
  `childPaused()`, `getPausedChild()`
- `agent/core/src/main/java/com/agentengine/agent/core/session/state/PauseState.java` —
  `pendingConfirmationIdVsChildSessionId`
