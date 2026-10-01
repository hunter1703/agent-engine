# TODO

## CORS origins need real per-customer domain patterns once a domain is chosen

`interfaces/rest/src/main/resources/application.properties`'s `quarkus.http.cors.origins` is still
just the localhost dev entries. Once a real domain is chosen for customer subdomains (e.g.
`*.complianceos.in`) and a `.test`-based local/POC domain is set up to mirror it (see
`AuthFilter`'s Origin-based tenant resolution), add both as origin patterns here —
Quarkus's CORS config supports regex origins (`/pattern/` syntax) for this. This is separate from
`AuthFilter`'s own Origin check, which validates against actual registered customers
in the database; the CORS config only controls whether the browser lets the frontend read the
response at all.

## Access control: no authenticated surface for seeding, users, roles or sharing

`deploy/scripts/ci/seed_app.py` seeds connections, models and agents through the REST API without
logging in, which the REST `AuthFilter` now rejects. There is also no REST endpoint for creating
users, assigning global roles or sharing an asset, so `AclService.updateSharing` is only
reachable from agent tools. Until one exists, nothing shares tenant-wide connections such as
`default_brave_connection` (used by `WebSearchTool`) with the `Agent` principal, so agents can't read
them. Needed: an admin identity for seeding, and user/role/sharing endpoints.

## Login: no rate limiting, and the tenant comes from the Origin header

`POST /v1/auth/login` has no rate limit or lockout, so passwords can be guessed at will; limit
attempts per username and per client address. `AuthFilter` picks the customer from the
`Origin` header, which browsers leave off same-origin GETs and non-browser clients (the CLI) never
send; resolve it from a host the ingress validates (`Host`/`X-Forwarded-Host`) instead.

## Access control: new users start with no role

Creating most assets — an agent, a connection, knowledge (including an attached file, indexed as
the session) — takes `CREATE` on every asset of its class, held by the user and so by everything
acting for them. Nothing maps a role to a user when the user is created, so a new user can do
nothing until an admin grants one. Decide the default roles a user gets, and assign them when the
user is created.

## Access control: the gRPC transport trusts the caller's context

Every service accepts the `Context` a caller serializes into a gRPC request — including the system
user id — and enforces permissions against it. Any pod that can reach a service can therefore act as
the system of any customer. Needs mutual TLS or a network policy restricting who can call which
service, plus a signed or service-issued identity for system calls.

## Access control: child-session grants are never revoked

`AbstractAgentTool.issueGrants` maps roles to a child session's principal for every spawn and
message, and nothing removes them when the child session ends. The role mappings and grant tokens on
a widely shared notebook or knowledge item grow without bound. Revoke a session's mappings when it
completes or is deleted.

## Access control: leftover access lists of deleted children

Deleting an asset — by id, by query, or as a child of a deleted parent — tells tenancy to forget
its access list on a best-effort basis, so a failed call leaves one behind. Top-level assets are
safe — creating one with a reused id
replaces whatever access list is left — but a referenced child (a note) is created without a fresh
access list, so leftover note-level mappings come back when the note is re-created and shared again.
Add a periodic sweep in tenancy that forgets access lists whose assets no longer exist.

## Access control: Qdrant access-list writes are read-then-write

`AbstractPermissionedRepository.applyAcls` stores an access list with a conditional `updateOne`
(only while the stored `acl.version` is older). `QdrantEntityStore.updateOne` reads the matching
points and then writes them, since Qdrant has no conditional writes, so two pushes to one point at
the same moment can land out of order. `QdrantEntityStore.replace` checks the expected version
the same way, by reading it first, so a share that lands between that check and the write is
overwritten by the replace. Only memories are both stored in Qdrant and shared individually, so
this is rare. Fix by re-reading after writing and repushing when the stored version is not the
newest.

## Access control: review findings still to be discussed

Raised in the review of the identity/RBAC work and parked until the access-list code has been read
through. Not yet agreed on as problems or as fixes:

- **Two concurrent `save()`s creating the same client-chosen id race.** Both see no stored entity;
  the second upsert replaces the first's document, and both creators end up mapped as owners.

## Repositories: a replace reads only the base contextual fields of the stored entity

`AbstractRepository.readStored` reads the stored entity with `CONTEXTUAL_FIELDS` only, so an
entity's `copyContextualFieldsFrom` cannot compare anything else. `Role.copyContextualFieldsFrom`
keeps the stored status when the permissions are unchanged (so a rename does not process the role
again), but the stored role arrives without `assetClassVsPermissions` or `status`: a rename is
processed again anyway, and a role with no permissions gets a null status copied over. The same
blocks carrying a `VectorEntity`'s vectors over when its text is unchanged. Options: read the whole
stored entity on a replace (Qdrant would also need vectors returned on that read), or a repository
hook naming the extra fields to read. To decide once the repository refactor is reviewed.

## Knowledge: an overtaken indexing run can leave its chunks behind

`KnowledgeServiceImpl.runIndexing` versions each status write on the one before it, so a run
overtaken by a re-upload drops its result. But by then it may already have inserted chunks: if the
newer run deletes the knowledge's chunks before the older one finishes inserting, the older chunks
stay alongside the newer ones and are searched as current. Chunks carry only the knowledge id;
tagging them with the run (or knowledge version) that wrote them, and searching and deleting by it,
would keep runs apart.

## Agents surface everything shared with them

Sharing is authorized on the shared asset alone, so anyone who may share an asset may share it with
any principal, including an agent or session they cannot edit. Agents then surface what they can
reach on their own — every reachable knowledge item in their reminders and searches, every
reachable notebook in their notebook summary — so whatever is shared with `Agent/X` reaches every
user running X, including content meant to steer it. Decide what an agent pulls into its context
on its own (e.g. only what its config or its session attaches) rather than everything it can
reach.

## Parallel orchestrator sub-agents act as the orchestrator's session

In PARALLEL mode, `OrchestratorAgentFactory` builds an orchestrator's `subAgentIds` into its own
ADK agent tree, so they run inside the orchestrator's runner and session rather than in sessions of
their own. Building them reads their configs as the orchestrator — and, for a sub-agent that is
itself an orchestrator, its sub-agents too, which are shared with that sub-agent, not with the
orchestrator — and their tool calls run as the orchestrator's session principal, reaching what was
shared with the orchestrator instead of what was shared with each sub-agent. Decide how a parallel
sub-agent acts (its own child session, as MANAGER-mode sub-agents get, or the transferred-agent model)
and make the build and the runtime context follow it.

## Agent schedules: their scheduler jobs can fall out of step

`RuntimeServiceImpl` saves an `AgentSchedule` and then writes its scheduler job (same id) as two
separate calls, so a failed job write leaves a schedule that never fires, or fires its previous
version. That wants the agent service to reconcile schedules with jobs.

`AgentScheduleCleanupListener` removes the schedules (and their jobs) of a deleted agent only when the
delete names the agent's id (`EntityChange.Ids`). A delete of agents by filter publishes an
`EntityChange.Matching`, which it ignores, so those agents' schedules and jobs are left behind and
keep firing on missing agents. Nothing deletes agents by filter today. Handle `Matching` too, e.g.
by removing the schedules whose agent no longer exists.

## Deploy: tenancy needs more than one replica in production

Tenancy now serves every permission cache miss, every sharing change and every create of a
top-level asset (a synchronous access-list call). The local and socialmedia tiers run it as a single
`Recreate` replica like every other service; a production tier needs at least two replicas with a
rolling update, or every tenancy restart stops all creates.

## Product-specific roles belong in each product, not in agent-engine

Agent-engine seeds only its generic standard roles (tenancy's `roles.json` resource: reader, editor,
manager, creator, owner, agent) for every customer. Which further standard roles exist (e.g. a compliance
product's "Mine Owner"/"Auditor") is product-specific and belongs in that product's own
provisioning code, calling `RoleRepository`/`UserRepository` after a customer is provisioned.


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

## Database: Permission Check Folded Into Writes

`AbstractPermissionedRepository.updateIgnoringVersion(id, Update)` and `deleteByIdIgnoringVersion` read the entity's owner and grants
before the write, one extra round trip for callers without the permission on every asset. Folding
the permission filter into the write (Mongo: `_id` + owner/grants filter, treat zero matches as denied) needs id-filter
support in `QdrantEntityStore`'s generic filter translation, and Qdrant deletes return no match count.
Add both, then change the internal `updateInternal(id, ...)`/`deleteByIdInternal(id)` to take the
permission filter.

## Database: Decrypted Connection Caching

`ConnectionServiceImpl.getDecryptedConnection` reads and decrypts the connection on every connector call.
A cache keyed by connection id would skip the caller's read permission check and keep decrypted secrets
on the heap; it needs to be keyed per principal and invalidated by the CONNECTIONS broadcast before it is
worth adding.

## Database: Unindexed Query Paths

`Knowledge` and `AgentSession` list/count queries have no dedicated indexes. Add them once the real
filters and sorts used in production are known.
