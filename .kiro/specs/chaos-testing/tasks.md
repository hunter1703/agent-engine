# Implementation Plan: Chaos Engineering Testing

## Overview

This plan implements chaos engineering capabilities for agent-engine, answering: **Is the system
fault-tolerant, fault-recoverable, and does it protect user experience under failure?**

The implementation uses Chaos Mesh for infrastructure-level faults, Toxiproxy and WireMock for
dependency-level faults (databases, LLM providers, connectors), and custom Java code for
Pekko actor-level faults and application-level consistency validation.

**Key ordering principle**: Metrics collection and success criteria evaluation (Tasks 4–5) are
implemented before any fault injectors. This means every injector can be validated end-to-end
immediately after it is built, rather than accumulating untestable code.

## Tasks

- [ ] 1. Set up chaos module structure, register in settings.gradle, and define core enums
  - Add `include 'chaos:api'`, `include 'chaos:core'`, `include 'chaos'` to `settings.gradle`
  - Create `chaos/api/` and `chaos/core/` module directories with Gradle build files
  - Define `FaultType` enum with all fault types: POD_KILL, NETWORK_PARTITION, NETWORK_LATENCY,
    NETWORK_PACKET_LOSS, CLUSTER_PARTITION, CPU_STRESS, MEMORY_STRESS, DISK_STRESS,
    DATABASE_FAILURE, EVENT_JOURNAL_FAILURE, SNAPSHOT_STORE_FAILURE, QDRANT_FAILURE,
    LLM_PROVIDER_UNAVAILABLE, LLM_PROVIDER_LATENCY, CONNECTOR_FAILURE, MESSAGE_DELAY, MESSAGE_DROP
  - Define `BlastRadiusScope` enum: SINGLE_POD, SERVICE, NAMESPACE, CLUSTER
  - Define `ExperimentStatus` enum: SCHEDULED, RUNNING, PASSED, FAILED, ABORTED
  - Define `CriterionType` enum: MAX_ERROR_RATE, MAX_LATENCY_P99, MIN_SUCCESS_RATE,
    MAX_RECOVERY_TIME, ZERO_DATA_LOSS
  - All enums must include `UNKNOWN` and `valueOfOrDefault` per project conventions
  - _Requirements: 1.3, 1.4_

- [ ] 2. Implement experiment definition and configuration models
  - [ ] 2.1 Implement `FaultParameters` sealed interface hierarchy in `chaos/api`
    - Sealed interface with subtypes: `PodKillParameters`, `NetworkLatencyParameters`,
      `NetworkPartitionParameters`, `PacketLossParameters`, `CpuStressParameters`,
      `MemoryStressParameters`, `DiskStressParameters`, `DatabaseFailureParameters`,
      `QdrantFailureParameters`, `LlmProviderLatencyParameters`, `MessageDelayParameters`,
      `MessageDropParameters`
    - Each subtype is a record with only the fields relevant to that fault type
    - Register a Jackson polymorphic deserializer using `"parametersType"` discriminator field
    - _Requirements: 1.3_

  - [ ] 2.2 Create `TargetSelector` record
    - Fields: namespace, service, podLabels, entityId (`Optional<String>` for Pekko actor faults)
    - _Requirements: 1.2, 7.1_

  - [ ] 2.3 Create `BlastRadius` record
    - Fields: scope (`BlastRadiusScope`), maxPods, maxPercentage
    - _Requirements: 14.2, 14.3_

  - [ ] 2.4 Create `SuccessCriterion` record
    - Fields: type (`CriterionType`), threshold, description
    - _Requirements: 9.2_

  - [ ] 2.5 Create `ExperimentDefinition` record
    - Fields: name, description, target, faultType, parameters (`FaultParameters`), duration,
      blastRadius, successCriteria, observationWindow, recoveryWindow,
      schedule (`Optional<String>`), labels, dryRun, approved
    - _Requirements: 1.1, 1.2, 1.5, 18.4_

- [ ] 3. Implement metrics and results models
  - [ ] 3.1 Create `SteadyStateMetrics` record
    - Fields: successRate, p50/p95/p99 latency (`Duration`), errorRate, activeSessions,
      eventJournalLag (`Duration`), mongoLatency (`Duration`), podRestarts, timestamp
    - _Requirements: 2.2, 8.3_

  - [ ] 3.2 Create `FaultEvent` record
    - Fields: faultId, faultType, targetSelector, startTime, endTime, outcome
    - _Requirements: 3.5, 8.1_

  - [ ] 3.3 Create `EvaluationResult` record
    - Fields: passed, failures (`List<CriterionFailure>`), baseline, duringFault, postRecovery
    - _Requirements: 9.1, 9.4, 9.5_

  - [ ] 3.4 Create `ExperimentResult` record
    - Fields: experimentId, experimentName, startTime, endTime, status, baseline,
      duringFaultMetrics, postRecovery, evaluation, faultEvents, recoveryTime,
      abortReason (`Optional<String>`)
    - _Requirements: 8.6, 9.6, 19.2_

- [ ] 4. Implement metrics collection and steady state analysis
  - [ ] 4.1 Create `MetricsCollector` in `chaos/core/metrics`
    - Query Prometheus Java client for: request success rate, latency percentiles (p50/p95/p99),
      error rate, pod restart count
    - Query runtime service health endpoint for: active session count
    - Query Prometheus for pekko-persistence metrics for: event journal lag
    - Query Prometheus for MongoDB exporter metrics for: MongoDB operation latency
    - _Requirements: 2.2, 8.3_

  - [ ] 4.2 Implement baseline collection
    - Collect metrics over a configurable observation window (default 60s) before fault injection
    - Abort the experiment and log failure reason if baseline collection fails
    - _Requirements: 2.1, 2.3, 2.4, 2.5_

  - [ ] 4.3 Implement runtime metrics polling
    - Poll metrics at configurable intervals during the experiment (default 10s)
    - Collect post-recovery snapshot after the recovery window
    - _Requirements: 8.2, 8.6_

  - [ ] 4.4 Create `SteadyStateAnalyzer`
    - Calculate Recovery_Time: duration from fault removal to when metrics return within
      configured deviation thresholds of baseline
    - Compare post-recovery metrics to baseline for deviation percentage
    - _Requirements: 9.3, 19.3_

  - [ ]* 4.5 Write unit tests for MetricsCollector and SteadyStateAnalyzer
    - Test recovery time calculation with synthetic metric timeseries
    - Test deviation percentage calculation
    - _Requirements: 2.2, 19.3_

- [ ] 5. Implement success criteria evaluation and experiment reporting
  - [ ] 5.1 Create `SuccessCriteriaEvaluator` in `chaos/core/evaluation`
    - Implement `MAX_ERROR_RATE`: post-recovery errorRate ≤ threshold
    - Implement `MAX_LATENCY_P99`: post-recovery p99Latency ≤ Duration.ofMillis(threshold)
    - Implement `MIN_SUCCESS_RATE`: post-recovery successRate ≥ threshold
    - Implement `MAX_RECOVERY_TIME`: recoveryTime ≤ Duration.ofSeconds(threshold)
    - Implement `ZERO_DATA_LOSS`: delegate to `EventJournalValidator` sequence-gap check
    - Mark experiment PASSED only when all criteria pass; record per-criterion failures
    - _Requirements: 9.1, 9.2, 9.4, 9.5_

  - [ ] 5.2 Create `ExperimentReportGenerator` in `chaos/core/reporting`
    - Produce an `ExperimentReport` containing all `SteadyStateMetrics` snapshots, fault events,
      success criteria results, and recovery time
    - Support export as: JSON (default), Markdown, HTML
    - _Requirements: 9.6, 19.1, 19.2, 19.4_

  - [ ]* 5.3 Write unit tests for SuccessCriteriaEvaluator
    - Test each criterion type with passing and failing metric values
    - Test that a single criterion failure marks the experiment FAILED
    - _Requirements: 9.4, 9.5_

- [ ] 6. Checkpoint — ensure all tests pass
  - Verify unit tests for enums, models, metrics, and success criteria pass
  - Ask the user if questions arise

- [ ] 7. Implement ChaosService interface and ChaosEngine orchestration
  - [ ] 7.1 Create `ChaosService` interface in `chaos/api`
    - Methods: `executeExperiment`, `scheduleExperiment`, `emergencyStop`,
      `getExperimentHistory` — all returning `CompletionStage`
    - _Requirements: 1.1, 13.1, 15.1_

  - [ ] 7.2 Implement experiment validation
    - Validate all required fields, reject unsupported fault types
    - Enforce production safety checks (approvalFlag required when env is production)
    - Support dry-run mode: validate + resolve targets + preview baseline without injecting
    - _Requirements: 1.2, 1.6, 18.4, 18.5_

  - [ ] 7.3 Implement `BlastRadiusEnforcer`
    - Pre-flight: resolve pod selectors via Kubernetes API, count matching pods
    - Enforce maxPercentage and maxPods limits; abort with a structured error if exceeded
    - Validate SINGLE_POD affects exactly one pod; SERVICE affects only specified service pods
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.6_

  - [ ] 7.4 Create `ChaosEngine` implementation in `chaos/core/engine`
    - Orchestrate full experiment lifecycle:
      1. validate + blast-radius check
      2. collect baseline (Task 4.2)
      3. inject fault via `FaultInjector`
      4. poll metrics until duration expires (Task 4.3)
      5. remove fault
      6. wait recovery window
      7. collect post-recovery snapshot
      8. evaluate success criteria (Task 5.1)
      9. generate report (Task 5.2)
    - Manage `RUNNING` / `ABORTED` / `PASSED` / `FAILED` transitions
    - _Requirements: 1.1, 8.1, 8.4, 8.5, 8.6_

  - [ ]* 7.5 Write unit tests for ChaosEngine with mocked injectors
    - Test lifecycle transitions (start → inject → remove → evaluate)
    - Test abort on blast radius violation
    - Test abort on baseline collection failure
    - _Requirements: 1.6, 2.5, 14.6_

- [ ] 8. Implement ChaosMeshFaultInjector
  - [ ] 8.1 Create `ChaosMeshFaultInjector` implementing `FaultInjector`
    - Add Fabric8 Kubernetes client as dependency of `chaos:core`
    - Use `GenericKubernetesResource` API to create and delete Chaos Mesh CRs
    - Support SINGLE_POD and SERVICE blast radius when constructing CRD selectors
    - _Requirements: 3.1, 4.1, 5.1_

  - [ ] 8.2 Implement POD_KILL fault (`PodChaos` CR with action `pod-kill`)
    - Respect `BlastRadius.maxPods` by setting Chaos Mesh `mode: fixed` with count
    - Record pod termination events with timestamps
    - _Requirements: 3.1, 3.2, 3.5_

  - [ ] 8.3 Implement NETWORK_PARTITION, NETWORK_LATENCY, NETWORK_PACKET_LOSS faults
    - All map to `NetworkChaos` CR; differ only in `action` and spec fields
    - NETWORK_PARTITION: action `partition`, direction `both`, target service
    - NETWORK_LATENCY: action `delay`, latency from `NetworkLatencyParameters`
    - NETWORK_PACKET_LOSS: action `loss`, percent from `PacketLossParameters`
    - _Requirements: 4.1, 4.2, 4.3_

  - [ ] 8.4 Implement CLUSTER_PARTITION fault
    - `NetworkChaos` CR targeting Pekko Artery TCP port (2552) between pod groups
    - Validate recovery by confirming cluster membership is restored after fault removal
    - _Requirements: 22.1_

  - [ ] 8.5 Implement CPU_STRESS, MEMORY_STRESS faults (`StressChaos` CR)
    - CPU_STRESS: `stressors.cpu.workers` and `load` from `CpuStressParameters`
    - MEMORY_STRESS: `stressors.memory.size` from `MemoryStressParameters`
    - _Requirements: 5.1, 5.2_

  - [ ] 8.6 Implement DISK_STRESS fault (`IOChaos` CR with action `mixed`)
    - Configure from `DiskStressParameters.diskIORate`
    - _Requirements: 5.3_

  - [ ] 8.7 Implement fault removal
    - Delete the Chaos Mesh CR by name; handle 404 gracefully (already removed)
    - _Requirements: 8.4, 15.1, 15.4_

  - [ ]* 8.8 Write integration tests for ChaosMeshFaultInjector using Fabric8 mock server
    - Verify correct CR structure for each fault type (POD_KILL, NETWORK_LATENCY, STRESS)
    - Verify fault removal deletes the CR by name
    - Verify blast radius is enforced in the CR spec
    - _Requirements: 3.1, 4.1, 5.1_

- [ ] 9. Implement PekkoFaultInjector (BehaviorInterceptor)
  - [ ] 9.1 Create `ChaosMailboxConfig` record
    - Fields: dropPercentage (`double`), delayDuration (`Optional<Duration>`),
      delayPercentage (`double`), active (`boolean`)
    - Implement `shouldDrop(SessionCommand)` and `shouldDelay(SessionCommand)` with
      thread-safe random sampling
    - _Requirements: 7.1, 7.2_

  - [ ] 9.2 Create `MessageFaultInterceptor extends BehaviorInterceptor<SessionCommand, SessionCommand>`
    - Override `aroundReceive`: drop (return `Behaviors.same()`), delay (re-schedule via
      `ctx.asClassic().system().scheduler().scheduleOnce()`), or pass through
    - Override `aroundSignal`: always pass through — never intercept lifecycle signals
    - _Requirements: 7.1, 7.2, 7.3_

  - [ ] 9.3 Create `PekkoFaultInjector` implementing `FaultInjector`
    - Maintain a thread-safe registry (`ConcurrentHashMap<String, ChaosMailboxConfig>`) keyed
      by entity ID (or `"*"` for all sessions)
    - `injectFault`: insert config into registry; return entity ID as faultId
    - `removeFault`: remove or deactivate config from registry
    - Integrate with `SessionActorFactory`: if registry contains a config for the entity ID,
      wrap the behavior with `Behaviors.intercept(() -> new MessageFaultInterceptor(config), inner)`
    - _Requirements: 7.1, 7.2_

  - [ ] 9.4 Write integration tests using Pekko `ActorTestKit`
    - Use in-memory journal (`pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"`)
    - Test MESSAGE_DELAY: send N commands, verify all are processed in order after delay
    - Test MESSAGE_DROP: verify dropped commands trigger supervision recovery
    - Test event sourcing consistency: sequence numbers are gap-free after delays/drops
    - _Requirements: 7.3, 7.4, 7.5_

- [ ] 10. Implement DatabaseFaultInjector and LlmProviderFaultInjector
  - [ ] 10.1 Create `DatabaseFaultInjector` implementing `FaultInjector`
    - Manage Toxiproxy client (`eu.rekawek.toxiproxy:toxiproxy-java`) targeting named proxies:
      `postgresql` (covers Event_Journal and Snapshot_Store), `mongodb`, `qdrant`
    - DATABASE_FAILURE: add `timeout` toxic with timeout=0 (connection hang)
    - EVENT_JOURNAL_FAILURE: add `timeout` toxic on `postgresql` proxy upstream
    - SNAPSHOT_STORE_FAILURE: add `latency` toxic on `postgresql` proxy (high latency, not full block)
    - QDRANT_FAILURE: add `timeout` toxic (full block) or `latency` toxic (from
      `QdrantFailureParameters.latency`) on the `qdrant` proxy
    - Return toxic name as faultId; removal deletes the toxic by name
    - In integration tests use `ToxiproxyContainer` from Testcontainers
    - _Requirements: 6.1, 6.2, 23.1, 24.1, 26.1_

  - [ ] 10.2 Implement recovery validation for database faults
    - After fault removal, verify reconnection within 30 seconds by polling health endpoint
    - _Requirements: 6.5, 23.4_

  - [ ] 10.3 Create `LlmProviderFaultInjector` implementing `FaultInjector`
    - LLM_PROVIDER_UNAVAILABLE: configure WireMock stub returning HTTP 503 for the LLM endpoint
    - LLM_PROVIDER_LATENCY: configure WireMock stub with fixed delay from `LlmProviderLatencyParameters`
    - CONNECTOR_FAILURE: configure WireMock stub returning HTTP 500 or connection reset for the
      target connector endpoint URL
    - Return stub mapping ID as faultId; removal deletes the stub
    - In staging: manage a Toxiproxy proxy entry for the LLM provider and connector base URLs
    - _Requirements: 21.1, 21.2, 25.1_

  - [ ]* 10.4 Write integration tests for database and LLM fault injection
    - EVENT_JOURNAL_FAILURE: SessionActor rejects new commands; recovers after toxic removed
    - SNAPSHOT_STORE_FAILURE: actor continues with in-memory state; snapshot writes fail quietly
    - QDRANT_FAILURE: MemoryService returns degraded/empty results; session run completes
      without failure; success rate stays above MIN_SUCCESS_RATE
    - LLM_PROVIDER_UNAVAILABLE: session transitions to error state without losing committed events
    - CONNECTOR_FAILURE: tool error returned to agent, session state intact
    - _Requirements: 23.2, 23.3, 24.2, 24.3, 26.2, 26.3, 26.4, 21.3, 21.4, 25.2, 25.3_

- [ ] 11. Checkpoint — ensure all tests pass
  - Verify injector unit and integration tests pass
  - Ask the user if questions arise

- [ ] 12. Implement session recovery and event journal consistency validation
  - [ ] 12.1 Create `SessionConsistencyValidator` in `chaos/core/validation`
    - Before fault injection: snapshot pre-fault state (active session IDs, last event
      sequence numbers, session statuses) via runtime service admin endpoint
    - After recovery: re-snapshot and compare; report any missing sessions or sequence regressions
    - _Requirements: 10.4, 10.5, 10.6_

  - [ ] 12.2 Create `EventJournalValidator`
    - Query the PostgreSQL Event_Journal directly (read-only JDBC query on `event_journal` table)
    - Verify per-persistence-ID sequence numbers are monotonically increasing with no gaps
    - Verify snapshot sequence numbers are ≤ the latest journal sequence for each ID
    - Mark experiment FAILED if gaps or regressions are detected
    - _Requirements: 11.2, 11.3, 11.5, 11.6_

  - [ ] 12.3 Implement round-trip idempotence check
    - Replay event stream for a sampled session from sequence 0 using the same
      `SessionActorState.applyEvent()` logic
    - Compare replayed state to the live snapshot; fail if they diverge
    - _Requirements: 11.5_

  - [ ]* 12.4 Write integration tests for validators
    - Test that a manually introduced sequence gap is detected by `EventJournalValidator`
    - Test that consistent pre/post state passes `SessionConsistencyValidator`
    - _Requirements: 10.5, 11.2_

- [ ] 13. Implement graceful degradation validation
  - [ ] 13.1 Create `DegradationValidator`
    - During fault injection, compare real-time success rate to baseline
    - Validate that success rate stays above the configured minimum threshold (default 50%)
    - Validate that the runtime returns 503/504 rather than hanging indefinitely
    - _Requirements: 12.1, 12.2, 12.5, 12.6_

  - [ ] 13.2 Validate backpressure behaviour
    - Under CPU_STRESS and MEMORY_STRESS, confirm the runtime does not OOM-crash and continues
      returning responses (even degraded ones)
    - Confirm no unrecoverable state after resource stress is removed
    - _Requirements: 12.3, 12.4_

- [ ] 14. Implement Pekko cluster split-brain validation (Requirement 22)
  - [ ] 14.1 Create `ClusterPartitionValidator`
    - After CLUSTER_PARTITION fault injection: poll Pekko Management HTTP API for cluster
      membership state; verify unreachable members are detected within the
      `acceptable-heartbeat-pause` window (15s per config)
    - Verify the SBR `keep-majority` strategy downs minority partition nodes
    - _Requirements: 22.2, 22.3_

  - [ ] 14.2 Validate post-partition majority behaviour
    - Confirm the majority partition continues serving requests after minority is downed
    - Verify no `Session_Actor` instances are running in an isolated state (check via admin
      endpoint that session IDs are only reachable from majority shard region)
    - _Requirements: 22.4, 22.6_

  - [ ] 14.3 Validate cluster rejoin after partition healed
    - After fault removal: downed nodes are restarted by Kubernetes; verify they rejoin the
      cluster as fresh members with empty shard state
    - _Requirements: 22.5_

- [ ] 15. Checkpoint — ensure all tests pass
  - Verify validation tests pass; ask the user if questions arise

- [ ] 16. Implement experiment scheduling and automation
  - [ ] 16.1 Create `ExperimentScheduler` in `chaos/core/engine`
    - Parse cron expressions using `quartz-scheduler` or `java.util.concurrent.ScheduledExecutorService`
    - Support interval-based scheduling (every N minutes/hours) and one-time execution
    - Prevent overlapping executions on the same target via a per-target lock
    - _Requirements: 13.1, 13.2, 13.3_

  - [ ] 16.2 Implement experiment execution history
    - Persist `ExperimentResult` to MongoDB_Store (`chaos_experiments` collection)
    - Support retrieval by target selector, time range, and status
    - _Requirements: 13.5_

  - [ ] 16.3 Implement failure notifications
    - Send Slack webhook notification on FAILED or ABORTED experiment
    - Send email via configured SMTP on experiment failure
    - Support generic webhook for custom integrations
    - Support pausing and resuming scheduled experiments via a flag in the persisted config
    - _Requirements: 13.4, 13.6_

- [ ] 17. Implement emergency stop mechanism
  - [ ] 17.1 Implement `emergencyStop(String experimentId)` on `ChaosEngine`
    - Immediately call `removeFault` on all active `FaultInjector` instances for the experiment
    - Mark experiment as ABORTED; record stop reason and triggering user
    - If fault removal fails: log the manual remediation command and send high-priority alert
    - _Requirements: 15.1, 15.3, 15.4, 15.5, 15.6_

  - [ ] 17.2 Add automatic stop triggers
    - Stop automatically if experiment exceeds a configured maximum duration (safety ceiling)
    - Stop automatically if a critical health check endpoint returns non-200 during the experiment
    - _Requirements: 15.4_

- [ ] 18. Implement observability integration
  - [ ] 18.1 Implement Kubernetes event emission
    - Emit `ChaosExperimentStarted`, `FaultInjected`, `FaultRemoved`, `ChaosExperimentCompleted`
      events using Fabric8 `CoreV1Api.createNamespacedEvent`
    - Annotate target pods with experiment labels during fault injection
    - _Requirements: 16.1, 16.2_

  - [ ] 18.2 Implement Prometheus metrics export
    - Expose `/chaos/metrics` endpoint with `chaos_experiments_total`,
      `chaos_experiments_recovery_time_seconds`, `chaos_faults_active`, and criterion counters
    - Use Micrometer (already in Quarkus) for all counters and gauges
    - _Requirements: 16.3_

  - [ ] 18.3 Implement structured logging
    - Log experiment lifecycle events at INFO with structured fields: experiment_id,
      experiment_name, target_service, fault_type, blast_radius_scope
    - Log failures at ERROR; log safety violations at WARN
    - _Requirements: 16.4_

  - [ ] 18.4 Implement trace correlation
    - Propagate trace IDs from experiment execution into fault injection calls and validation runs
    - Include trace ID in structured log fields and Kubernetes event annotations
    - _Requirements: 16.6_

- [ ] 19. Implement configuration management
  - [ ] 19.1 Create JSON configuration parser
    - Load experiment definitions from `configs/chaos/experiments/<env>/` directory
    - Validate all required fields on load; fail fast with descriptive error on invalid config
    - Support environment variable overrides for blast radius limits and safety flags
    - _Requirements: 1.1, 18.1, 18.2_

  - [ ] 19.2 Create global chaos engine configuration
    - Parse `configs/infra/chaos-config.json` into a strongly-typed config record
    - Include Toxiproxy proxy map, Chaos Mesh namespace, Prometheus URL, production safety checks,
      notification settings
    - _Requirements: 18.2, 18.3_

  - [ ] 19.3 Implement dry-run mode
    - When `dryRun = true`: resolve pod selectors, calculate affected pods, collect baseline
      preview, log what would happen — without creating any Chaos Mesh resources
    - _Requirements: 18.5_

  - [ ] 19.4 Implement configuration versioning
    - Store a SHA-256 hash of each experiment definition when it is loaded
    - Include the definition hash in `ExperimentResult` for change tracking
    - _Requirements: 18.6_

- [ ] 20. Implement multi-agent orchestration chaos testing (Requirement 17)
  - [ ] 20.1 Implement orchestrator fault injection
    - Target a child `Session_Actor` by entity ID using `PekkoFaultInjector` while the
      parent orchestrator session is running
    - Verify the parent `Session_Actor` receives child failure propagation
    - _Requirements: 17.1, 17.2, 17.3_

  - [ ] 20.2 Implement SEQUENTIAL orchestration failure validation
    - Inject a fault causing the first child session to fail
    - Validate the orchestrator stops launching further child sessions
    - Verify orchestrator session state shows the failure was recorded
    - _Requirements: 17.4, 17.6_

  - [ ] 20.3 Implement PARALLEL orchestration partial failure validation
    - Inject faults into a subset of parallel child sessions
    - Validate the orchestrator continues to completion with the surviving branches
    - Verify the final orchestrator state reflects both successes and failures
    - _Requirements: 17.5, 17.6_

  - [ ]* 20.4 Write integration tests for orchestrator chaos
    - Use `ActorTestKit` with in-memory journal to test both SEQUENTIAL and PARALLEL scenarios
    - _Requirements: 17.4, 17.5_

- [ ] 21. Implement tool execution resilience testing (Requirement 20)
  - [ ] 21.1 Inject connector failures mid-tool-execution
    - Use `LlmProviderFaultInjector` (WireMock) to make a connector endpoint fail while a
      tool call is in-flight
    - Validate `Connector_Service` throws a typed exception; `Session_Actor` converts it to a
      structured tool error result
    - _Requirements: 20.1, 20.2, 20.3_

  - [ ] 21.2 Validate PARALLEL and SEQUENTIAL tool execution failure behaviour
    - PARALLEL: one tool fails, others complete; validate partial failure does not corrupt state
    - SEQUENTIAL: first tool fails; validate execution stops and session state is intact
    - _Requirements: 20.4, 20.5, 20.6_

- [ ] 22. Implement reporting and analytics (Requirement 19)
  - [ ] 22.1 Complete `ExperimentReportGenerator`
    - Generate full reports: baseline metrics, per-interval during-fault snapshots, post-recovery
      metrics, success criteria results, fault events, recovery time, pod events
    - Export to JSON, Markdown, and HTML formats
    - Support writing to S3 or file system based on configuration
    - _Requirements: 19.1, 19.2, 19.4, 16.5_

  - [ ] 22.2 Implement analytics tracking
    - Calculate experiment success rate trend over the last N runs (configurable)
    - Detect degrading experiments (success rate declining across runs) and send alerts
    - Expose experiment history summary via `GET /chaos/experiments/history`
    - _Requirements: 19.5, 19.6_

- [ ] 23. Checkpoint — ensure all tests pass
  - Full test suite; ask the user if questions arise

- [ ] 24. Wire chaos module into application and expose REST endpoints
  - [ ] 24.1 Register `ChaosService` as a Quarkus CDI `@Singleton` in `chaos:core`
    - Inject Fabric8 `KubernetesClient`, Toxiproxy client, WireMock server reference,
      `MetricsCollector`, `SuccessCriteriaEvaluator`, `ExperimentReportGenerator`
    - Configure Kubernetes client with the chaos controller `ServiceAccount` credentials
    - _Requirements: 1.1_

  - [ ] 24.2 Create REST endpoints in `interfaces:rest`
    - `POST   /chaos/experiments`             — execute experiment
    - `POST   /chaos/experiments/schedule`    — schedule recurring experiment
    - `POST   /chaos/experiments/{id}/stop`   — emergency stop
    - `GET    /chaos/experiments/history`     — experiment history with optional filters
    - `POST   /chaos/experiments/dry-run`     — dry-run validation
    - Document all endpoints with MicroProfile OpenAPI annotations
    - _Requirements: 15.2, 13.1_

  - [ ] 24.3 Create embedded startup configuration
    - When running with `chaos` Quarkus profile: start Toxiproxy sidecar if not already running,
      verify Chaos Mesh CRD availability via Kubernetes API on startup
    - _Requirements: 18.2_

- [ ] 25. Create deployment configuration and example experiments
  - [ ] 25.1 Create Kubernetes RBAC configuration
    - `ClusterRole` with permissions for Chaos Mesh CRDs (create/delete/get/list/watch),
      pods (get/list for blast radius), events (create/patch)
    - `ServiceAccount` and `ClusterRoleBinding` for the chaos controller
    - _Requirements: 14.1_

  - [ ] 25.2 Create chaos controller Kubernetes manifests
    - `Deployment` with resource limits (256Mi/100m → 512Mi/500m), chaos `ServiceAccount`,
      environment variables for Prometheus URL, Toxiproxy host, Chaos Mesh namespace
    - _Requirements: 18.2_

  - [ ] 25.3 Create example experiment configurations in `configs/chaos/experiments/staging/`
    - `runtime-pod-kill-recovery.json` — POD_KILL with ZERO_DATA_LOSS + MAX_RECOVERY_TIME criteria
    - `event-journal-failure.json` — EVENT_JOURNAL_FAILURE with ZERO_DATA_LOSS criterion
    - `network-partition-cluster.json` — CLUSTER_PARTITION testing split-brain resolver
    - `llm-provider-unavailable.json` — LLM_PROVIDER_UNAVAILABLE with MIN_SUCCESS_RATE criterion
    - `message-delay-actor.json` — MESSAGE_DELAY targeting a specific session entity ID
    - `connector-failure-tool.json` — CONNECTOR_FAILURE with MIN_SUCCESS_RATE criterion
    - `qdrant-failure.json` — QDRANT_FAILURE with MIN_SUCCESS_RATE criterion (memory search must
      degrade gracefully, not fail sessions)
    - _Requirements: 1.1, 18.1, 26.1_

- [ ] 26. Final checkpoint — ensure all tests pass and documentation is complete
  - Full test suite; verify all acceptance criteria are addressed; ask the user if questions arise

## Notes

- Tasks marked `*` are optional integration tests and can be skipped to reach a faster MVP; the
  corresponding unit tests in the same task are mandatory
- Each task references requirements for traceability
- Integration tests use Testcontainers (PostgreSQL, MongoDB, Toxiproxy, WireMock); Kind cluster is
  reserved for the `e2eTest` source set gated behind `-Pe2e`
- All enums must include `UNKNOWN` and `valueOfOrDefault` per project conventions
- Use Java 25 features (records, sealed interfaces, virtual threads, `CompletionStage`) throughout
- Chaos Mesh must be installed in the target cluster; verify availability on startup
- The `parametersType` discriminator field in experiment JSON maps to the sealed subtype name
- `Optional<String>` rather than nullable `String` for record fields that may be absent
