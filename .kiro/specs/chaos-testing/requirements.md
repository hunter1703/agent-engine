# Requirements Document: Chaos Engineering Testing

## Introduction

This document defines requirements for chaos engineering testing capabilities in the agent-engine
project. The system introduces controlled failures to validate resilience, error handling, and
recovery mechanisms across the distributed Java 25/Quarkus runtime built on LangChain4j, Pekko
actors, and Kubernetes infrastructure.

The central question these tests answer is: **Is agent-engine robust?** Specifically — is it
fault-tolerant (does it continue operating in the presence of failures?), fault-recoverable (does
it return to normal after failures are removed?), and does it protect user experience (does it
degrade minimally when it cannot avoid degrading)?

Chaos testing will target critical failure modes including pod crashes, network partitions,
resource exhaustion, database failures, LLM provider outages, connector failures, and actor
message delivery issues.

## Glossary

- **Chaos_Engine**: The orchestration component that schedules, executes, and monitors chaos experiments
- **Fault_Injector**: Component that introduces specific failure conditions into target system components
- **Chaos_Mesh**: CNCF chaos engineering platform that injects infrastructure faults via Kubernetes CRDs
- **Toxiproxy**: Network proxy for simulating connection-level database faults in test environments
- **Experiment**: A defined chaos test scenario with target, fault type, duration, and success criteria
- **Blast_Radius**: The scope of components affected by a chaos experiment (single pod, service, cluster)
- **Steady_State**: The baseline system behavior metrics collected before and after experiments
- **Recovery_Time**: Duration from fault injection to system returning to steady state
- **Session_Actor**: Pekko `EventSourcedBehavior` managing individual agent session state with event sourcing; implemented as `SessionActor extends ShardedEntity`
- **Shard_Region**: Pekko Cluster Sharding region that manages `Session_Actor` lifecycle — restarts actors after pod death using `remember-entities` backed by the `Event_Journal`
- **Event_Journal**: PostgreSQL-backed event store for Pekko persistence, backed by pekko-persistence-jdbc (`jdbc-journal` plugin)
- **Snapshot_Store**: PostgreSQL-backed snapshot store for Pekko persistence (`jdbc-snapshot-store` plugin)
- **MongoDB_Store**: Persistence layer for agent session metadata, agent configs, and infra configuration (does **not** store the event journal)
- **Qdrant_Store**: Vector database backing `MemoryService` (`util:vectordb`) — stores memory embeddings and knowledge chunks for semantic search
- **LLM_Provider**: External AI model API (e.g., Anthropic, OpenAI, local llama.cpp) called synchronously during agent runs
- **Connector_Service**: Outbound HTTP transport layer (`connectors:core`) that executes tool calls against external APIs
- **Runtime_Service**: The deployable runtime artifact hosting the Pekko cluster, agent execution engine, and REST gateway
- **Kubernetes_Cluster**: Container orchestration platform hosting the Runtime_Service pods

## Requirements

### Requirement 1: Chaos Experiment Definition

**User Story:** As a platform engineer, I want to define chaos experiments declaratively, so that I
can version control and reproduce failure scenarios.

#### Acceptance Criteria

1. THE Chaos_Engine SHALL parse experiment definitions from JSON configuration files
2. WHEN an experiment definition is loaded, THE Chaos_Engine SHALL validate all required fields
   (name, target, faultType, duration, successCriteria)
3. THE Chaos_Engine SHALL support fault types: POD_KILL, NETWORK_PARTITION, NETWORK_LATENCY,
   NETWORK_PACKET_LOSS, CPU_STRESS, MEMORY_STRESS, DISK_STRESS, DATABASE_FAILURE,
   EVENT_JOURNAL_FAILURE, SNAPSHOT_STORE_FAILURE, LLM_PROVIDER_UNAVAILABLE, LLM_PROVIDER_LATENCY,
   CONNECTOR_FAILURE, MESSAGE_DELAY, MESSAGE_DROP, CLUSTER_PARTITION, QDRANT_FAILURE
4. THE Chaos_Engine SHALL support blast radius scopes: SINGLE_POD, SERVICE, NAMESPACE, CLUSTER
5. WHERE an experiment specifies a schedule, THE Chaos_Engine SHALL execute the experiment at the
   configured interval
6. THE Chaos_Engine SHALL reject experiment definitions with invalid target selectors or
   unsupported fault types

### Requirement 2: Steady State Baseline Collection

**User Story:** As a platform engineer, I want to establish steady state baselines before
experiments, so that I can measure deviation and recovery.

#### Acceptance Criteria

1. WHEN an experiment starts, THE Chaos_Engine SHALL collect steady state metrics for the target
   components
2. THE Chaos_Engine SHALL measure baseline metrics: request success rate, p50/p95/p99 latency,
   error rate, active sessions, event sourcing lag, Event_Journal operation latency, MongoDB_Store
   operation latency, Qdrant_Store operation latency
3. THE Chaos_Engine SHALL store baseline measurements with experiment metadata
4. THE Chaos_Engine SHALL collect steady state metrics for a configurable observation window
   (default 60 seconds)
5. IF baseline collection fails, THEN THE Chaos_Engine SHALL abort the experiment and log the
   failure reason

### Requirement 3: Fault Injection for Pod Failures

**User Story:** As a platform engineer, I want to kill pods randomly, so that I can validate
Kubernetes restart policies and session recovery.

#### Acceptance Criteria

1. WHEN a POD_KILL fault is injected, THE Fault_Injector SHALL terminate target pods matching the
   selector via a Chaos_Mesh `PodChaos` resource
2. THE Fault_Injector SHALL respect blast radius limits when selecting pods to terminate
3. WHEN a Runtime_Service pod is killed, THE Shard_Region SHALL detect the lost members and
   re-activate `Session_Actor` instances on surviving pods using the `remember-entities` event
   store backed by the Event_Journal
4. WHEN a pod is killed, THE Kubernetes_Cluster SHALL restart it within 30 seconds per the
   deployment restart policy
5. THE Fault_Injector SHALL record pod termination events with timestamps and target identifiers

### Requirement 4: Fault Injection for Network Failures

**User Story:** As a platform engineer, I want to inject network failures, so that I can validate
timeout handling and retry logic.

#### Acceptance Criteria

1. WHEN a NETWORK_PARTITION fault is injected, THE Fault_Injector SHALL block network traffic
   between specified services via a Chaos_Mesh `NetworkChaos` resource
2. WHEN a NETWORK_LATENCY fault is injected, THE Fault_Injector SHALL add configurable delay
   (milliseconds) to network packets via a Chaos_Mesh `NetworkChaos` resource
3. WHEN a NETWORK_PACKET_LOSS fault is injected, THE Fault_Injector SHALL drop a configurable
   percentage of network packets via a Chaos_Mesh `NetworkChaos` resource
4. WHEN network faults are active, THE Runtime_Service SHALL timeout requests within configured
   limits and return error responses
5. WHEN network faults are removed, THE Runtime_Service SHALL resume normal operation within
   10 seconds

### Requirement 5: Fault Injection for Resource Exhaustion

**User Story:** As a platform engineer, I want to exhaust system resources, so that I can validate
resource limits and backpressure mechanisms.

#### Acceptance Criteria

1. WHEN a CPU_STRESS fault is injected, THE Fault_Injector SHALL consume CPU cycles up to the
   configured percentage via a Chaos_Mesh `StressChaos` resource
2. WHEN a MEMORY_STRESS fault is injected, THE Fault_Injector SHALL allocate memory up to the
   configured limit via a Chaos_Mesh `StressChaos` resource
3. WHEN a DISK_STRESS fault is injected, THE Fault_Injector SHALL perform intensive disk I/O
   operations via a Chaos_Mesh `IOChaos` resource
4. WHEN resource stress is active, THE Runtime_Service SHALL continue processing requests with
   degraded performance
5. WHEN resource stress exceeds pod limits, THE Kubernetes_Cluster SHALL throttle or evict pods
   according to QoS class

### Requirement 6: Fault Injection for MongoDB Metadata Store Failures

**User Story:** As a platform engineer, I want to simulate MongoDB failures, so that I can validate
connection handling and graceful degradation when session metadata is unavailable.

#### Acceptance Criteria

1. WHEN a DATABASE_FAILURE fault is injected, THE Fault_Injector SHALL block connections to
   MongoDB_Store via Toxiproxy in test environments or a Chaos_Mesh `NetworkChaos` resource in
   staging/production
2. THE Fault_Injector SHALL support partial database failures affecting specific collections or
   operations
3. WHEN MongoDB_Store is unreachable, THE Runtime_Service SHALL return error responses with
   appropriate HTTP status codes (503, 504) for operations requiring session metadata
4. WHEN MongoDB_Store is unreachable, active `Session_Actor` instances SHALL continue processing
   already-started runs because session state is held in the actor and Event_Journal; only
   metadata writes (session creation, title updates) SHALL fail
5. WHEN database connectivity is restored, THE Runtime_Service SHALL reconnect within 30 seconds

### Requirement 7: Fault Injection for Actor Message Delivery Issues

**User Story:** As a platform engineer, I want to delay or drop actor messages, so that I can
validate event sourcing resilience and sharding supervision strategies.

#### Acceptance Criteria

1. WHEN a MESSAGE_DELAY fault is injected, THE Fault_Injector SHALL delay `Session_Actor`
   command delivery by a configurable duration using a Pekko `BehaviorInterceptor` wrapping the
   actor behavior
2. WHEN a MESSAGE_DROP fault is injected, THE Fault_Injector SHALL drop a configurable percentage
   of `Session_Actor` commands using a Pekko `BehaviorInterceptor`
3. WHEN messages are delayed, THE Session_Actor SHALL process events in order after the delay
   expires, preserving event sourcing consistency
4. WHEN messages are dropped, THE Shard_Region SHALL detect the timeout and apply the configured
   restart supervision strategy
5. THE Session_Actor SHALL maintain Event_Journal consistency despite message delivery issues

### Requirement 8: Experiment Execution and Monitoring

**User Story:** As a platform engineer, I want to execute experiments with real-time monitoring,
so that I can observe system behavior during failures.

#### Acceptance Criteria

1. WHEN an experiment starts, THE Chaos_Engine SHALL inject the configured fault into target
   components
2. WHILE an experiment is running, THE Chaos_Engine SHALL collect metrics at configurable
   intervals (default 10 seconds)
3. THE Chaos_Engine SHALL monitor metrics: request success rate, error rate, latency percentiles,
   active sessions, pod restarts, event sourcing lag
4. WHEN the experiment duration expires, THE Chaos_Engine SHALL remove the injected fault by
   deleting the corresponding Chaos_Mesh resource or Toxiproxy configuration
5. THE Chaos_Engine SHALL wait for a configurable recovery window (default 60 seconds) after
   fault removal
6. THE Chaos_Engine SHALL collect post-experiment steady state metrics after the recovery window

### Requirement 9: Experiment Success Criteria Evaluation

**User Story:** As a platform engineer, I want experiments to evaluate success criteria
automatically, so that I can detect regressions in resilience.

#### Acceptance Criteria

1. WHEN an experiment completes, THE Chaos_Engine SHALL evaluate configured success criteria
   against collected metrics
2. THE Chaos_Engine SHALL support success criteria types: MAX_ERROR_RATE, MAX_LATENCY_P99,
   MIN_SUCCESS_RATE, MAX_RECOVERY_TIME, ZERO_DATA_LOSS
3. THE Chaos_Engine SHALL compare post-recovery metrics to baseline metrics for deviation
   thresholds
4. IF all success criteria pass, THEN THE Chaos_Engine SHALL mark the experiment as PASSED
5. IF any success criterion fails, THEN THE Chaos_Engine SHALL mark the experiment as FAILED and
   record which criteria failed
6. THE Chaos_Engine SHALL generate experiment reports with baseline, during-fault, and
   post-recovery metrics

### Requirement 10: Session Recovery Validation

**User Story:** As a platform engineer, I want to validate session recovery after failures, so
that I can ensure no user interactions are lost.

#### Acceptance Criteria

1. WHEN a pod hosting a `Session_Actor` is terminated, THE Shard_Region SHALL re-activate the
   actor on a surviving cluster member via the `remember-entities` mechanism
2. THE Session_Actor SHALL replay all committed events from the Event_Journal to restore session
   state
3. WHEN session recovery completes, THE Session_Actor SHALL resume processing from the last
   committed event sequence number
4. THE Chaos_Engine SHALL verify session state consistency by comparing pre-failure and
   post-recovery session data
5. THE Chaos_Engine SHALL validate that no committed events are lost during recovery
   (round-trip property)
6. IF session recovery fails or state is inconsistent, THEN THE Chaos_Engine SHALL mark the
   experiment as FAILED

### Requirement 11: Event Journal Consistency Validation

**User Story:** As a platform engineer, I want to validate Event_Journal consistency during
failures, so that I can ensure data integrity in the PostgreSQL persistence layer.

#### Acceptance Criteria

1. WHEN faults are injected, THE Chaos_Engine SHALL track all session events written to the
   Event_Journal (PostgreSQL)
2. THE Chaos_Engine SHALL verify that event sequence numbers are monotonically increasing without
   gaps for each persistence ID
3. THE Chaos_Engine SHALL verify that all events can be replayed to reconstruct session state
4. WHEN the Event_Journal is unavailable, THE Session_Actor SHALL reject new commands until
   persistence is confirmed; it SHALL NOT acknowledge an event until it is durable
5. THE Chaos_Engine SHALL validate that replaying events produces identical session state
   (idempotence property)
6. IF event sequence gaps or replay inconsistencies are detected, THEN THE Chaos_Engine SHALL
   mark the experiment as FAILED

### Requirement 12: Graceful Degradation Validation

**User Story:** As a platform engineer, I want to validate graceful degradation under load, so
that I can ensure the system remains partially functional during failures.

#### Acceptance Criteria

1. WHEN faults are injected, THE Runtime_Service SHALL continue processing requests with reduced
   capacity
2. THE Runtime_Service SHALL return error responses with appropriate HTTP status codes
   (503 Service Unavailable, 504 Gateway Timeout) rather than hanging or crashing
3. THE Runtime_Service SHALL not crash or enter an unrecoverable state during fault injection
4. WHEN resource limits are reached, THE Runtime_Service SHALL apply backpressure to prevent
   cascading failures
5. THE Chaos_Engine SHALL measure degradation by comparing success rates during fault injection
   to baseline
6. THE Chaos_Engine SHALL validate that success rate remains above a configurable threshold
   (default 50%) during faults

### Requirement 13: Experiment Scheduling and Automation

**User Story:** As a platform engineer, I want to schedule chaos experiments automatically, so
that I can continuously validate resilience.

#### Acceptance Criteria

1. WHERE an experiment specifies a cron schedule, THE Chaos_Engine SHALL execute the experiment
   at scheduled times
2. THE Chaos_Engine SHALL support schedule expressions: cron syntax, interval (every N
   minutes/hours), one-time execution
3. THE Chaos_Engine SHALL prevent overlapping experiment executions on the same target
4. WHEN a scheduled experiment fails, THE Chaos_Engine SHALL send notifications to configured
   channels (Slack, email, webhook)
5. THE Chaos_Engine SHALL maintain experiment execution history with timestamps, results, and
   metrics
6. THE Chaos_Engine SHALL support pausing and resuming scheduled experiments

### Requirement 14: Blast Radius Control and Safety

**User Story:** As a platform engineer, I want to limit experiment blast radius, so that I can
prevent widespread outages during testing.

#### Acceptance Criteria

1. WHEN an experiment is executed, THE Chaos_Engine SHALL enforce the configured blast radius
   limit
2. THE Chaos_Engine SHALL prevent experiments from affecting more than the specified percentage of
   pods (default 25%)
3. WHERE an experiment targets SINGLE_POD, THE Fault_Injector SHALL affect exactly one pod
4. WHERE an experiment targets SERVICE, THE Fault_Injector SHALL affect only pods in the
   specified service
5. THE Chaos_Engine SHALL validate that production namespaces have blast radius limits configured
6. IF an experiment would exceed blast radius limits, THEN THE Chaos_Engine SHALL abort the
   experiment and log a safety violation

### Requirement 15: Experiment Rollback and Emergency Stop

**User Story:** As a platform engineer, I want to stop experiments immediately, so that I can
prevent damage during unexpected failures.

#### Acceptance Criteria

1. WHEN an emergency stop is triggered, THE Chaos_Engine SHALL immediately remove all active
   faults by deleting Chaos_Mesh resources and resetting Toxiproxy configurations
2. THE Chaos_Engine SHALL support emergency stop via API endpoint and CLI command
3. WHEN an experiment is stopped early, THE Chaos_Engine SHALL mark the experiment as ABORTED
4. THE Chaos_Engine SHALL restore target components to steady state within 30 seconds of
   emergency stop
5. THE Chaos_Engine SHALL log emergency stop events with triggering user and reason
6. IF fault removal fails during emergency stop, THEN THE Chaos_Engine SHALL escalate to manual
   intervention and send alerts

### Requirement 16: Integration with Kubernetes Observability

**User Story:** As a platform engineer, I want chaos experiments to integrate with existing
observability tools, so that I can correlate failures with metrics and logs.

#### Acceptance Criteria

1. WHEN an experiment starts, THE Chaos_Engine SHALL emit Kubernetes events with experiment
   metadata
2. THE Chaos_Engine SHALL annotate target pods with experiment labels (experiment-id, fault-type,
   start-time)
3. THE Chaos_Engine SHALL expose experiment metrics via Prometheus endpoints
4. THE Chaos_Engine SHALL emit structured logs with experiment lifecycle events (start,
   fault-injected, fault-removed, completed)
5. THE Chaos_Engine SHALL support exporting experiment results to external systems (S3,
   Elasticsearch, Grafana)
6. THE Chaos_Engine SHALL correlate experiment events with application logs and metrics using
   trace IDs

### Requirement 17: Chaos Testing for Multi-Agent Orchestration

**User Story:** As a platform engineer, I want to test orchestrator resilience, so that I can
validate that multi-agent workflows handle failures correctly.

#### Acceptance Criteria

1. WHEN an orchestrator agent is executing, THE Chaos_Engine SHALL inject faults into child agent
   sessions
2. THE Chaos_Engine SHALL validate that orchestrator agents detect child session failures
3. WHEN a child session fails, THE Runtime_Service SHALL propagate failure information to the
   orchestrator `Session_Actor`
4. THE Chaos_Engine SHALL validate that agents using `OrchestrationMode.SEQUENTIAL` stop
   execution after child failures
5. THE Chaos_Engine SHALL validate that agents using `OrchestrationMode.PARALLEL` continue with
   successful branches after partial failures
6. THE Chaos_Engine SHALL verify that orchestrator session state remains consistent after child
   session failures

### Requirement 18: Chaos Testing Configuration Management

**User Story:** As a platform engineer, I want to manage chaos configurations per environment, so
that I can run different experiments in dev, staging, and production.

#### Acceptance Criteria

1. THE Chaos_Engine SHALL load experiment configurations from environment-specific directories
2. THE Chaos_Engine SHALL support configuration overrides via environment variables
3. WHERE an environment is marked as production, THE Chaos_Engine SHALL enforce stricter blast
   radius limits
4. THE Chaos_Engine SHALL validate that production experiments have explicit approval flags
5. THE Chaos_Engine SHALL support dry-run mode that validates experiments without injecting faults
6. THE Chaos_Engine SHALL version experiment configurations and track changes over time

### Requirement 19: Chaos Testing Reporting and Analytics

**User Story:** As a platform engineer, I want detailed experiment reports, so that I can analyze
trends and improve system resilience over time.

#### Acceptance Criteria

1. WHEN an experiment completes, THE Chaos_Engine SHALL generate a report with baseline, fault,
   and recovery metrics
2. THE Chaos_Engine SHALL include in reports: experiment metadata, success criteria results,
   metric timeseries, pod events, error logs
3. THE Chaos_Engine SHALL calculate Recovery_Time as the duration from fault removal to steady
   state restoration
4. THE Chaos_Engine SHALL support exporting reports in JSON, HTML, and Markdown formats
5. THE Chaos_Engine SHALL maintain a dashboard showing experiment success rates over time
6. THE Chaos_Engine SHALL identify experiments with degrading success rates and send alerts

### Requirement 20: Chaos Testing for Tool Execution Resilience

**User Story:** As a platform engineer, I want to test tool execution under failures, so that I
can validate timeout handling and error propagation.

#### Acceptance Criteria

1. WHEN tool execution is in progress, THE Chaos_Engine SHALL inject faults affecting
   Connector_Service dependencies
2. THE Chaos_Engine SHALL validate that tool timeouts are enforced during network latency faults
3. WHEN a tool execution fails, THE Runtime_Service SHALL return structured error responses to
   the agent without corrupting session state
4. THE Chaos_Engine SHALL validate that `OrchestrationMode.PARALLEL` tool execution handles
   partial failures correctly
5. THE Chaos_Engine SHALL validate that `OrchestrationMode.SEQUENTIAL` tool execution stops
   after the first failure
6. THE Chaos_Engine SHALL verify that tool execution state is not corrupted after failures

### Requirement 21: LLM Provider Failure Testing

**User Story:** As a platform engineer, I want to simulate LLM provider outages and latency, so
that I can validate that agent sessions degrade gracefully when the AI model is unavailable.

#### Acceptance Criteria

1. WHEN an LLM_PROVIDER_UNAVAILABLE fault is injected, THE Fault_Injector SHALL make the
   LLM_Provider endpoint return connection errors or 5xx responses using Toxiproxy or WireMock
2. WHEN an LLM_PROVIDER_LATENCY fault is injected, THE Fault_Injector SHALL add configurable
   delay to LLM_Provider responses to simulate a slow model
3. WHEN the LLM_Provider is unavailable, THE Session_Actor SHALL fail the current run step with
   a structured error and transition the session to a recoverable error state
4. THE Chaos_Engine SHALL validate that the user receives an actionable error response rather
   than a hung request when the LLM_Provider is unreachable
5. THE Session_Actor SHALL not lose committed session history when an LLM_Provider fault occurs
   mid-run
6. WHEN the LLM_Provider recovers, THE Session_Actor SHALL be able to accept new messages and
   resume operation without restart

### Requirement 22: Pekko Cluster Partition and Split-Brain Testing

**User Story:** As a platform engineer, I want to simulate network partitions between cluster
members, so that I can validate that the split-brain resolver correctly handles cluster
partitions.

#### Acceptance Criteria

1. WHEN a CLUSTER_PARTITION fault is injected, THE Fault_Injector SHALL block Pekko cluster
   gossip and Artery TCP communication between specified pod groups using Chaos_Mesh
   `NetworkChaos`
2. WHEN a network partition occurs, THE Shard_Region SHALL detect the unreachable members via the
   phi-accrual failure detector within the configured `acceptable-heartbeat-pause` window (15s)
3. WHEN the minority partition is detected, THE split-brain resolver (`keep-majority` strategy)
   SHALL down the minority partition nodes
4. THE Chaos_Engine SHALL validate that the majority partition continues processing requests
   after the minority nodes are downed
5. WHEN the network partition is healed, THE Chaos_Engine SHALL validate that previously downed
   nodes can rejoin the cluster as fresh members
6. THE Chaos_Engine SHALL verify that no `Session_Actor` instances from the downed minority are
   still processing commands in an isolated state (split-brain sessions)

### Requirement 23: Event Journal (PostgreSQL) Failure Testing

**User Story:** As a platform engineer, I want to simulate PostgreSQL failures, so that I can
validate that event-sourced actors handle journal unavailability without data loss.

#### Acceptance Criteria

1. WHEN an EVENT_JOURNAL_FAILURE fault is injected, THE Fault_Injector SHALL block or slow
   PostgreSQL connections using Toxiproxy in test environments
2. WHEN the Event_Journal is unreachable, THE Session_Actor SHALL reject new commands with a
   persistence failure; it SHALL NOT acknowledge events until they are durable
3. THE Chaos_Engine SHALL validate that no event is acknowledged to the caller before it is
   successfully written to the Event_Journal
4. WHEN the Event_Journal becomes available again, THE Session_Actor SHALL automatically retry
   pending persistence operations and resume normal processing
5. THE Chaos_Engine SHALL verify that event sequence numbers contain no gaps after recovery
6. WHEN the Event_Journal is slow (high latency), THE Chaos_Engine SHALL measure the resulting
   increase in end-to-end session response time and validate it remains within configured bounds

### Requirement 24: Snapshot Store Failure Testing

**User Story:** As a platform engineer, I want to validate behavior when the snapshot store is
unavailable, so that I can ensure recovery correctness when snapshots cannot be written or read.

#### Acceptance Criteria

1. WHEN a SNAPSHOT_STORE_FAILURE fault is injected, THE Fault_Injector SHALL block PostgreSQL
   connections on the `jdbc-snapshot-store` path using Toxiproxy
2. WHEN the Snapshot_Store is unavailable, active `Session_Actor` instances SHALL continue
   processing commands using in-memory state; only snapshot writes SHALL fail
3. THE Chaos_Engine SHALL verify that a `Session_Actor` starting fresh during a
   Snapshot_Store fault can still recover by replaying events from the Event_Journal
4. THE Chaos_Engine SHALL validate that recovery time increases predictably with event journal
   depth when no snapshot is available
5. WHEN the Snapshot_Store recovers, THE Session_Actor SHALL resume writing snapshots at the
   configured threshold without manual intervention

### Requirement 25: Connector Service Failure Testing

**User Story:** As a platform engineer, I want to simulate outbound HTTP failures from the
Connector_Service, so that I can validate that tool execution errors are propagated correctly and
do not crash sessions.

#### Acceptance Criteria

1. WHEN a CONNECTOR_FAILURE fault is injected, THE Fault_Injector SHALL make the target
   connector endpoint return connection errors, timeouts, or 5xx responses using Toxiproxy or
   WireMock
2. WHEN a connector call fails, THE Connector_Service SHALL throw a typed exception that the
   `Session_Actor` can catch and convert to a structured tool error
3. THE Chaos_Engine SHALL validate that the `Session_Actor` reports the tool failure to the LLM
   as a tool error result rather than crashing the session
4. THE Chaos_Engine SHALL validate that partial connector failures (some tools succeed, some
   fail) do not corrupt session state
5. WHEN the connector recovers, THE Session_Actor SHALL be able to retry or issue new tool calls
   in subsequent turns without restart

### Requirement 26: Qdrant Vector Memory Store Failure Testing

**User Story:** As a platform engineer, I want to simulate Qdrant_Store failures, so that I can
validate that memory search degrades gracefully instead of failing agent runs when semantic
recall is unavailable.

#### Acceptance Criteria

1. WHEN a QDRANT_FAILURE fault is injected, THE Fault_Injector SHALL block or delay connections to
   Qdrant_Store via Toxiproxy in test environments or a Chaos_Mesh `NetworkChaos` resource in
   staging/production
2. WHEN Qdrant_Store is unreachable, `MemoryService` SHALL return an empty or degraded memory
   result rather than throwing an unhandled exception to the calling `Session_Actor`
3. WHEN Qdrant_Store is unreachable, THE Chaos_Engine SHALL validate that in-flight agent runs
   continue and complete using conversational context alone, with reduced recall quality but no
   session failure
4. THE Chaos_Engine SHALL validate that success rate during a QDRANT_FAILURE experiment stays
   above the configured `MIN_SUCCESS_RATE` threshold, reflecting graceful degradation rather than
   hard failure
5. WHEN Qdrant_Store connectivity is restored, THE Runtime_Service SHALL resume semantic memory
   search within 30 seconds without requiring a `Session_Actor` restart
