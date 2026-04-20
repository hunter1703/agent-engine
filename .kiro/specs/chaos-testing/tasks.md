# Implementation Plan: Chaos Engineering Testing

## Overview

This plan implements chaos engineering capabilities for the agent-engine platform, enabling controlled fault injection to validate resilience across the distributed Java 25/Quarkus runtime. The implementation follows the existing module architecture with a new `chaos/` module containing API contracts and core implementations.

## Tasks

- [ ] 1. Set up chaos module structure and core data models
  - Create `chaos/api/` and `chaos/core/` module directories with Gradle build files
  - Define `FaultType` enum with all fault types (POD_KILL, NETWORK_PARTITION, NETWORK_LATENCY, NETWORK_PACKET_LOSS, CPU_STRESS, MEMORY_STRESS, DISK_STRESS, DATABASE_FAILURE, MESSAGE_DELAY, MESSAGE_DROP)
  - Define `BlastRadiusScope` enum (SINGLE_POD, SERVICE, NAMESPACE, CLUSTER)
  - Define `ExperimentStatus` enum (SCHEDULED, RUNNING, PASSED, FAILED, ABORTED)
  - Define `CriterionType` enum for success criteria types
  - All enums must include UNKNOWN and valueOfOrDefault parser per project conventions
  - _Requirements: 1.3, 1.4, 14.1_

- [ ] 2. Implement experiment definition and configuration models
  - [ ] 2.1 Create ExperimentDefinition record in chaos/api
    - Include name, description, target, faultType, parameters, duration, blastRadius, successCriteria, observationWindow, recoveryWindow, schedule, labels
    - _Requirements: 1.1, 1.2, 1.5_
  
  - [ ] 2.2 Create TargetSelector record
    - Include namespace, service, podLabels, actorPath fields
    - _Requirements: 1.2, 3.2_
  
  - [ ] 2.3 Create FaultParameters record
    - Include fields for network faults (latency, packetLossPercentage, blockedServices)
    - Include fields for resource faults (cpuPercentage, memoryLimit, diskIORate)
    - Include fields for message faults (messageDelay, messageDropPercentage)
    - _Requirements: 4.2, 4.3, 5.1, 5.2, 7.1, 7.2_
  
  - [ ] 2.4 Create BlastRadius record
    - Include scope, maxPods, maxPercentage fields
    - _Requirements: 1.4, 14.2, 14.3_
  
  - [ ] 2.5 Create SuccessCriterion record
    - Include type, threshold, description fields
    - _Requirements: 9.2_

- [ ] 3. Implement metrics and results models
  - [ ] 3.1 Create SteadyStateMetrics record
    - Include successRate, p50/p95/p99 latency, errorRate, activeSessions, eventSourcingLag, mongoLatency, podRestarts, timestamp
    - _Requirements: 2.2, 8.3_
  
  - [ ] 3.2 Create ExperimentResult record
    - Include experimentId, experimentName, startTime, endTime, status, baseline, duringFaultMetrics, postRecovery, evaluation, faultEvents, recoveryTime, abortReason
    - _Requirements: 8.6, 9.6, 19.2_
  
  - [ ] 3.3 Create EvaluationResult record
    - Include passed, failures list, baseline, duringFault, postRecovery metrics
    - _Requirements: 9.1, 9.4, 9.5_

- [ ] 4. Implement ChaosService interface and core engine
  - [ ] 4.1 Create ChaosService interface in chaos/api
    - Define executeExperiment, scheduleExperiment, emergencyStop, getExperimentHistory methods
    - All methods return CompletionStage for async execution
    - _Requirements: 1.1, 13.1, 15.1_
  
  - [ ] 4.2 Create ChaosEngine implementation in chaos/core/engine
    - Implement experiment lifecycle orchestration
    - Parse and validate experiment definitions from JSON
    - Coordinate baseline collection, fault injection, monitoring, recovery, evaluation
    - _Requirements: 1.1, 1.2, 1.6, 8.1, 8.4_
  
  - [ ] 4.3 Implement experiment validation logic
    - Validate required fields (name, target, faultType, duration, successCriteria)
    - Reject invalid target selectors or unsupported fault types
    - Validate blast radius constraints before execution
    - _Requirements: 1.2, 1.6, 14.4, 14.6_

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement FaultInjector interface and Kubernetes fault injection
  - [ ] 6.1 Create FaultInjector interface in chaos/core/injection
    - Define injectFault, removeFault, supports methods
    - Return CompletionStage for async fault operations
    - _Requirements: 3.1, 4.1, 5.1_
  
  - [ ] 6.2 Create KubernetesFaultInjector implementation
    - Integrate Fabric8 Kubernetes Java client
    - Implement POD_KILL fault (delete pods matching selector)
    - Respect blast radius limits when selecting pods
    - Record pod termination events with timestamps
    - _Requirements: 3.1, 3.2, 3.5_
  
  - [ ] 6.3 Implement network fault injection
    - Implement NETWORK_PARTITION using Kubernetes network policies
    - Implement NETWORK_LATENCY using sidecar proxy with tc
    - Implement NETWORK_PACKET_LOSS using sidecar proxy with packet drop rules
    - Apply and remove network policies cleanly
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6_
  
  - [ ] 6.4 Implement resource stress fault injection
    - Implement CPU_STRESS using stress-ng sidecar
    - Implement MEMORY_STRESS using stress-ng sidecar
    - Implement DISK_STRESS using stress-ng sidecar with I/O operations
    - Deploy and remove sidecar containers with resource limits
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  
  - [ ]* 6.5 Write integration tests for Kubernetes fault injection
    - Test pod kill and recovery using Testcontainers with Kind cluster
    - Test network policy application and removal
    - Test resource stress sidecar deployment
    - _Requirements: 3.1, 4.1, 5.1_

- [ ] 7. Implement Pekko actor fault injection
  - [ ] 7.1 Create PekkoFaultInjector implementation
    - Implement custom mailbox extending UnboundedMailbox
    - Intercept enqueue() to apply message delays or drops
    - Support MESSAGE_DELAY fault with configurable duration
    - Support MESSAGE_DROP fault with configurable percentage
    - Register custom mailbox via Pekko configuration
    - _Requirements: 7.1, 7.2, 7.3, 7.4_
  
  - [ ] 7.2 Implement message fault configuration
    - Create ChaosConfig for mailbox behavior
    - Implement shouldDropMessage and shouldDelayMessage logic
    - Schedule delayed message delivery using Pekko scheduler
    - _Requirements: 7.1, 7.2_
  
  - [ ]* 7.3 Write integration tests for Pekko fault injection
    - Test message delay preserves event ordering
    - Test message drop triggers supervision strategies
    - Test event sourcing consistency with message faults
    - _Requirements: 7.4, 7.5, 7.6_

- [ ] 8. Implement database fault injection
  - [ ] 8.1 Create DatabaseFaultInjector implementation
    - Implement DATABASE_FAILURE using Kubernetes network policies to block MongoDB
    - Support partial database failures for specific collections
    - Monitor connection pool metrics during fault
    - _Requirements: 6.1, 6.2_
  
  - [ ] 8.2 Implement fault removal and recovery validation
    - Remove network policies to restore connectivity
    - Verify reconnection within 30 seconds
    - _Requirements: 6.5_
  
  - [ ]* 8.3 Write integration tests for database fault injection
    - Test MongoDB connectivity blocking using Testcontainers
    - Test graceful failure without crashes
    - Test automatic reconnection after fault removal
    - _Requirements: 6.3, 6.4, 6.5_

- [ ] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Implement metrics collection and steady state analysis
  - [ ] 10.1 Create MetricsCollector in chaos/core/metrics
    - Integrate with Prometheus Java client
    - Collect request success rate, latency percentiles (p50, p95, p99), error rate
    - Collect active sessions from runtime service
    - Collect event sourcing lag from Pekko persistence metrics
    - Collect MongoDB operation latency
    - Collect pod restart count from Kubernetes API
    - _Requirements: 2.2, 8.3_
  
  - [ ] 10.2 Implement baseline collection
    - Collect steady state metrics before experiment starts
    - Use configurable observation window (default 60 seconds)
    - Store baseline measurements with experiment metadata
    - Abort experiment if baseline collection fails
    - _Requirements: 2.1, 2.3, 2.4, 2.5_
  
  - [ ] 10.3 Implement runtime metrics collection
    - Collect metrics at configurable intervals during experiment (default 10 seconds)
    - Collect post-recovery metrics after recovery window
    - _Requirements: 8.2, 8.6_
  
  - [ ] 10.4 Create SteadyStateAnalyzer for deviation detection
    - Compare post-recovery metrics to baseline
    - Calculate recovery time from fault removal to steady state
    - _Requirements: 9.3, 19.3_

- [ ] 11. Implement success criteria evaluation
  - [ ] 11.1 Create SuccessCriteriaEvaluator in chaos/core/evaluation
    - Implement MAX_ERROR_RATE criterion evaluation
    - Implement MAX_LATENCY_P99 criterion evaluation
    - Implement MIN_SUCCESS_RATE criterion evaluation
    - Implement MAX_RECOVERY_TIME criterion evaluation
    - Implement ZERO_DATA_LOSS criterion evaluation
    - _Requirements: 9.2_
  
  - [ ] 11.2 Implement evaluation logic
    - Compare collected metrics against configured thresholds
    - Mark experiment as PASSED if all criteria pass
    - Mark experiment as FAILED if any criterion fails
    - Record which criteria failed with details
    - _Requirements: 9.1, 9.4, 9.5_
  
  - [ ] 11.3 Generate experiment reports
    - Include baseline, during-fault, and post-recovery metrics
    - Include success criteria results
    - Include metric timeseries and pod events
    - _Requirements: 9.6, 19.2_

- [ ] 12. Implement session recovery and event sourcing validation
  - [ ] 12.1 Create session recovery validator
    - Track session events written to MongoDB during faults
    - Verify event sequence numbers are monotonically increasing
    - Verify no gaps in event sequences
    - Compare pre-failure and post-recovery session state
    - _Requirements: 10.4, 11.2, 11.3_
  
  - [ ] 12.2 Implement event replay validation
    - Verify all events can be replayed to reconstruct session state
    - Validate idempotence property (replay produces identical state)
    - Mark experiment as FAILED if inconsistencies detected
    - _Requirements: 11.3, 11.5, 11.6_
  
  - [ ] 12.3 Implement round-trip consistency check
    - Validate no committed events are lost during recovery
    - Verify session resumes from last committed event
    - _Requirements: 10.5, 10.3_

- [ ] 13. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 14. Implement experiment scheduling and automation
  - [ ] 14.1 Create ExperimentScheduler in chaos/core/engine
    - Parse cron schedule expressions
    - Support interval-based scheduling (every N minutes/hours)
    - Support one-time execution
    - Execute experiments at scheduled times
    - _Requirements: 1.5, 13.1, 13.2_
  
  - [ ] 14.2 Implement scheduling safety mechanisms
    - Prevent overlapping experiment executions on same target
    - Maintain experiment execution history
    - Support pausing and resuming scheduled experiments
    - _Requirements: 13.3, 13.5, 13.6_
  
  - [ ] 14.3 Implement failure notifications
    - Send notifications to Slack webhook on experiment failure
    - Send email notifications to configured recipients
    - Support webhook notifications for custom integrations
    - _Requirements: 13.4_

- [ ] 15. Implement blast radius control and safety mechanisms
  - [ ] 15.1 Create BlastRadiusEnforcer
    - Calculate affected pods before fault injection
    - Enforce maximum percentage of pods per scope
    - Validate SINGLE_POD affects exactly one pod
    - Validate SERVICE affects only specified service pods
    - _Requirements: 14.2, 14.3, 14.4_
  
  - [ ] 15.2 Implement production safety checks
    - Enforce stricter blast radius limits for production namespaces
    - Require explicit approval flags for production experiments
    - Validate allowed namespaces per environment
    - Abort experiment if blast radius would be exceeded
    - _Requirements: 14.5, 14.6_
  
  - [ ] 15.3 Implement emergency stop mechanism
    - Create API endpoint POST /chaos/experiments/{id}/stop
    - Support emergency stop via Kubernetes annotation
    - Immediately remove all active faults on emergency stop
    - Mark experiment as ABORTED when stopped early
    - Restore target components within 30 seconds
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_
  
  - [ ] 15.4 Implement emergency stop failure handling
    - Escalate to manual intervention if fault removal fails
    - Send high-priority alerts (log critical events)
    - Provide remediation steps in logs
    - _Requirements: 15.6_

- [ ] 16. Implement observability integration
  - [ ] 16.1 Implement Kubernetes event emission
    - Emit events for experiment start, fault injection, fault removal, completion
    - Include experiment metadata in events
    - Annotate target pods with experiment labels
    - _Requirements: 16.1, 16.2_
  
  - [ ] 16.2 Implement Prometheus metrics export
    - Expose /metrics endpoint with experiment metrics
    - Track chaos_experiments_total by status
    - Track chaos_experiments_duration_seconds
    - Track chaos_experiments_recovery_time_seconds
    - Track chaos_faults_injected_total and chaos_faults_active
    - Track chaos_success_criteria_passed/failed_total
    - _Requirements: 16.3_
  
  - [ ] 16.3 Implement structured logging
    - Log experiment lifecycle events with structured fields
    - Include experiment_id, experiment_name, target_service, fault_type, blast_radius_scope
    - Log at appropriate levels (INFO for lifecycle, WARN for issues, ERROR for failures)
    - _Requirements: 16.4_
  
  - [ ] 16.4 Implement trace correlation
    - Propagate trace IDs through experiment execution
    - Correlate experiment events with application traces
    - _Requirements: 16.6_

- [ ] 17. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 18. Implement configuration management
  - [ ] 18.1 Create JSON configuration parser
    - Parse experiment definitions from JSON files
    - Load experiments from configs/chaos/experiments/ directory
    - Support environment-specific configuration directories
    - _Requirements: 1.1, 18.1_
  
  - [ ] 18.2 Create chaos engine configuration
    - Define global settings in configs/infra/chaos-configs.json
    - Support configuration overrides via environment variables
    - Include default blast radius, observation/recovery windows, metrics collection interval
    - Include production safety checks configuration
    - Include notification settings (Slack webhook, email recipients)
    - Include Prometheus URL configuration
    - _Requirements: 18.2, 18.3_
  
  - [ ] 18.3 Implement dry-run mode
    - Validate experiments without injecting faults
    - Show which pods would be affected
    - Display baseline metrics preview
    - Support --dry-run CLI flag
    - _Requirements: 18.5_
  
  - [ ] 18.4 Implement configuration versioning
    - Track experiment configuration changes over time
    - Version experiment definitions
    - _Requirements: 18.6_

- [ ] 19. Implement multi-agent orchestration chaos testing
  - [ ] 19.1 Implement orchestrator fault injection
    - Inject faults into child agent sessions during orchestration
    - Validate orchestrator detects child session failures
    - Verify failure propagation to orchestrator
    - _Requirements: 17.1, 17.2, 17.3_
  
  - [ ] 19.2 Implement orchestration pattern validation
    - Validate SEQUENTIAL orchestrators stop after child failures
    - Validate PARALLEL orchestrators continue with successful branches
    - Verify orchestrator state consistency after child failures
    - _Requirements: 17.4, 17.5, 17.6_
  
  - [ ]* 19.3 Write integration tests for orchestrator chaos
    - Test sequential orchestrator failure handling
    - Test parallel orchestrator partial failure handling
    - Test orchestrator state consistency
    - _Requirements: 17.4, 17.5, 17.6_

- [ ] 20. Implement tool execution resilience testing
  - [ ] 20.1 Implement tool execution fault injection
    - Inject faults affecting tool dependencies during execution
    - Validate tool timeouts are enforced during network latency
    - Verify structured error responses returned to agent
    - _Requirements: 20.1, 20.2, 20.3_
  
  - [ ] 20.2 Implement tool execution pattern validation
    - Validate PARALLEL tool execution handles partial failures
    - Validate SEQUENTIAL tool execution stops after first failure
    - Verify tool execution state not corrupted after failures
    - _Requirements: 20.4, 20.5, 20.6_
  
  - [ ]* 20.3 Write integration tests for tool execution chaos
    - Test tool timeout enforcement
    - Test parallel tool execution partial failures
    - Test sequential tool execution failure handling
    - _Requirements: 20.4, 20.5, 20.6_

- [ ] 21. Implement graceful degradation validation
  - [ ] 21.1 Create degradation validator
    - Measure success rate during fault injection
    - Verify runtime service continues with reduced capacity
    - Validate appropriate HTTP status codes (503, 504)
    - Verify no crashes or unrecoverable states
    - _Requirements: 12.1, 12.2, 12.3_
  
  - [ ] 21.2 Implement backpressure validation
    - Validate backpressure applied when resource limits reached
    - Verify success rate remains above configurable threshold (default 50%)
    - Measure degradation compared to baseline
    - _Requirements: 12.4, 12.5, 12.6_

- [ ] 22. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 23. Implement reporting and analytics
  - [ ] 23.1 Create experiment report generator
    - Generate reports with baseline, fault, and recovery metrics
    - Include experiment metadata and success criteria results
    - Include metric timeseries, pod events, error logs
    - Calculate and include recovery time
    - _Requirements: 19.1, 19.2, 19.3_
  
  - [ ] 23.2 Implement report export formats
    - Support JSON export format
    - Support HTML export format
    - Support Markdown export format
    - Support export to external systems (S3, Elasticsearch)
    - _Requirements: 19.4, 16.5_
  
  - [ ] 23.3 Implement analytics dashboard data
    - Track experiment success rates over time
    - Identify experiments with degrading success rates
    - Send alerts for degrading experiments
    - _Requirements: 19.5, 19.6_

- [ ] 24. Create deployment configuration and RBAC
  - [ ] 24.1 Create Kubernetes RBAC configuration
    - Define ClusterRole with required permissions (pods get/list/delete, networkpolicies create/delete, events create)
    - Create ServiceAccount for chaos controller
    - Create ClusterRoleBinding
    - _Requirements: 14.1, 15.1_
  
  - [ ] 24.2 Create chaos controller deployment manifest
    - Define Deployment with resource requests/limits
    - Configure service account
    - Set environment variables for configuration
    - _Requirements: 18.2_
  
  - [ ] 24.3 Create example experiment configurations
    - Create runtime-pod-kill-recovery.json example
    - Create network-partition-mongodb.json example
    - Create message-delay-actor.json example
    - Create resource-stress-cpu.json example
    - Include comments explaining each field
    - _Requirements: 1.1, 18.1_

- [ ] 25. Integration and wiring
  - [ ] 25.1 Wire chaos module into main application
    - Add chaos module dependencies to runtime/core services
    - Register ChaosService as Quarkus CDI bean
    - Configure Kubernetes client with service account credentials
    - Configure Prometheus client with metrics endpoint
    - _Requirements: 1.1, 16.3_
  
  - [ ] 25.2 Create REST endpoints for chaos operations
    - POST /chaos/experiments - Execute experiment
    - POST /chaos/experiments/schedule - Schedule experiment
    - POST /chaos/experiments/{id}/stop - Emergency stop
    - GET /chaos/experiments/history - Get experiment history
    - Document endpoints with MicroProfile OpenAPI annotations
    - _Requirements: 15.1_
  
  - [ ]* 25.3 Write end-to-end integration tests
    - Test complete experiment lifecycle using Testcontainers
    - Test pod kill with session recovery validation
    - Test network partition with graceful degradation
    - Test emergency stop mechanism
    - Use Kind cluster for Kubernetes integration
    - _Requirements: 3.1, 6.1, 12.1, 15.1_

- [ ] 26. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at reasonable breaks
- Integration tests use Testcontainers with Kind for Kubernetes, MongoDB, and PostgreSQL
- All enums must include UNKNOWN and valueOfOrDefault parser per project conventions
- Follow project code quality philosophy: clarity over cleverness, minimal surface, maximum cohesion
- Use Java 25 features (records, virtual threads) where appropriate
- Leverage CompletionStage for async operations throughout
