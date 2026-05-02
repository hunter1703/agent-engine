# Chaos Testing — Plan 1: Core Module

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the chaos testing foundation — Gradle modules, data models, metrics collection,
success criteria evaluation, blast radius enforcement, and the `ChaosEngine` with dual-routing
orchestration — that subsequent plans plug real fault injectors and workflow submitters into.

**Architecture:** Two Gradle modules (`chaos:api`, `chaos:core`). `chaos:api` owns all shared
types (enums, records, sealed `FaultParameters`, `ChaosService`). `chaos:core` owns the engine,
metrics, evaluator, and stub implementations. `ChaosEngine` uses **dual routing**: infrastructure
faults (POD_KILL, NETWORK_*, CPU/MEMORY/DISK, CLUSTER_PARTITION) are submitted as Chaos Mesh
Workflow CRDs via `WorkflowSubmitter`; application faults (DATABASE_*, LLM_PROVIDER_*, MESSAGE_*,
CONNECTOR_*) are injected directly via `FaultInjector`. Both paths produce an `ExperimentResult`
with the same evaluation contract. `NoOpWorkflowSubmitter` and `NoOpFaultInjector` make the full
engine exercisable in unit tests with zero external dependencies.

**Tech Stack:** Java 25 (`--enable-preview`), Jackson 2.18.6 with `JavaTimeModule` (polymorphic
`FaultParameters`), Micrometer `MeterRegistry` (metrics interface), JUnit 5, AssertJ 3.27.0,
Mockito 5.18.0

**This plan does NOT cover:** Chaos Mesh CRD submission (Plan 2), Toxiproxy/WireMock injectors
(Plan 3), real data-loss validators (Plan 4), REST endpoints or scheduling (Plan 5).

---

## File Map

```
settings.gradle                          ← modify: add chaos:api, chaos:core, chaos includes

chaos/build.gradle                       ← new: aggregator (no deps, just groups sub-projects)
chaos/api/build.gradle                   ← new: library, Jackson + slf4j only
chaos/core/build.gradle                  ← new: library, chaos:api + Micrometer + Mockito (test)

chaos/api/src/main/java/com/agentengine/chaos/api/
  FaultType.java                         ← enum: 16 fault types + UNKNOWN
  BlastRadiusScope.java                  ← enum: SINGLE_POD SERVICE NAMESPACE CLUSTER + UNKNOWN
  ExperimentStatus.java                  ← enum: SCHEDULED RUNNING PASSED FAILED ABORTED UNKNOWN
  CriterionType.java                     ← enum: 5 criterion types + UNKNOWN
  WorkflowPhase.java                     ← enum: RUNNING SUCCEEDED FAILED ABORTED UNKNOWN
  FaultParameters.java                   ← sealed interface + @JsonTypeInfo/@JsonSubTypes
  PodKillParameters.java                 ← record: count
  NetworkLatencyParameters.java          ← record: latency (Duration)
  NetworkPartitionParameters.java        ← record: blockedServices (List<String>)
  PacketLossParameters.java              ← record: lossPercentage (double)
  CpuStressParameters.java               ← record: cpuPercentage (int)
  MemoryStressParameters.java            ← record: memoryLimit (String, e.g. "512Mi")
  DiskStressParameters.java              ← record: diskIORate (String)
  DatabaseFailureParameters.java         ← record: target (String, Toxiproxy proxy name)
  LlmProviderLatencyParameters.java      ← record: latency (Duration)
  MessageDelayParameters.java            ← record: delay (Duration), percentage (double)
  MessageDropParameters.java             ← record: dropPercentage (double)
  TargetSelector.java                    ← record: namespace service podLabels entityId(Optional)
  BlastRadius.java                       ← record: scope maxPods maxPercentage + defaultService()
  SuccessCriterion.java                  ← record: type threshold description
  FaultEvent.java                        ← record: faultId faultType targetSelector startTime endTime
  CriterionFailure.java                  ← record: criterion actualValue expectedThreshold
  SteadyStateMetrics.java                ← record: all metric fields + static empty()
  EvaluationResult.java                  ← record: passed failures baseline duringFault postRecovery
  WorkflowResult.java                    ← record: workflowId phase submittedAt completedAt
  ExperimentDefinition.java              ← record: full experiment config
  ExperimentResult.java                  ← record: full result + Optional<String> abortReason
  ChaosService.java                      ← interface: executeExperiment scheduleExperiment etc.

chaos/core/src/main/java/com/agentengine/chaos/core/
  injection/FaultInjector.java           ← interface: injectFault removeFault supports(FaultType)
  injection/NoOpFaultInjector.java       ← no-op: returns valid faultIds, injects nothing
  metrics/MetricsCollector.java          ← interface: collectBaseline collectSnapshot collectDuring
  metrics/SteadyStateAnalyzer.java       ← computes recoveryTime from snapshots vs baseline
  evaluation/DataLossValidator.java      ← interface: hasDataLoss()
  evaluation/NoOpDataLossValidator.java  ← always returns false
  evaluation/SuccessCriteriaEvaluator.java ← evaluates all 5 criterion types
  enforcement/PodResolver.java           ← @FunctionalInterface: countMatchingPods(TargetSelector)
  enforcement/BlastRadiusViolationException.java
  enforcement/BlastRadiusEnforcer.java   ← validates pod count before injection
  engine/WorkflowSubmitter.java          ← interface: submit getPhase cancel supportsWorkflow
  engine/NoOpWorkflowSubmitter.java      ← returns SUCCEEDED immediately; no actual CRD
  engine/ExperimentAbortedException.java
  engine/ChaosEngine.java                ← implements ChaosService; dual-routing orchestration
  reporting/ReportGenerationException.java
  reporting/ExperimentReportGenerator.java ← JSON + Markdown from ExperimentResult

chaos/core/src/test/java/com/agentengine/chaos/core/
  evaluation/SuccessCriteriaEvaluatorTest.java   ← 10 tests
  enforcement/BlastRadiusEnforcerTest.java        ← 4 tests
  metrics/SteadyStateAnalyzerTest.java            ← 3 tests
  engine/ChaosEngineTest.java                     ← 6 tests (infra path + app path + lifecycle)
  reporting/ExperimentReportGeneratorTest.java    ← 5 tests
```

---

### Task 1: Register chaos modules in `settings.gradle`

**Files:**
- Modify: `settings.gradle`

- [ ] **Step 1: Add chaos module includes**

Open `settings.gradle` and add after the last `include` line:

```groovy
include 'chaos:api'
include 'chaos:core'
include 'chaos'
```

- [ ] **Step 2: Verify Gradle resolves the modules**

```bash
./gradlew projects 2>&1 | grep chaos
```

Expected output:
```
+--- Project ':chaos'
     +--- Project ':chaos:api'
     \--- Project ':chaos:core'
```

- [ ] **Step 3: Commit**

```bash
git add settings.gradle
git commit -m "feat(chaos): register chaos:api, chaos:core modules in settings.gradle"
```

---

### Task 2: Create Gradle build files for all three chaos modules

**Files:**
- Create: `chaos/build.gradle`
- Create: `chaos/api/build.gradle`
- Create: `chaos/core/build.gradle`

- [ ] **Step 1: Create the aggregator `chaos/build.gradle`**

```groovy
// chaos/build.gradle — aggregator only; no code lives here
group = 'com.agentengine.chaos'
```

- [ ] **Step 2: Create `chaos/api/build.gradle`**

```groovy
plugins {
    id 'com.agentengine.java-library-conventions'
}

group = 'com.agentengine.chaos'

dependencies {
    implementation libs.jackson.databind
    implementation libs.jackson.datatype.jsr310
    implementation libs.jackson.datatype.jdk8
    implementation libs.slf4j.api
}
```

- [ ] **Step 3: Create `chaos/core/build.gradle`**

```groovy
plugins {
    id 'com.agentengine.java-library-conventions'
}

group = 'com.agentengine.chaos'

dependencies {
    api project(':chaos:api')
    implementation libs.slf4j.api

    // Micrometer for metrics collection (Prometheus backend)
    implementation platform(libs.quarkus.bom)
    implementation libs.quarkus.micrometer.registry.prometheus

    // Jackson for report serialization
    implementation libs.jackson.databind
    implementation libs.jackson.datatype.jsr310
    implementation libs.jackson.datatype.jdk8

    testImplementation libs.assertj.core
    testImplementation libs.mockito.core
    testImplementation libs.mockito.junit.jupiter
}
```

- [ ] **Step 4: Create src directories**

```bash
mkdir -p chaos/api/src/main/java/com/agentengine/chaos/api
mkdir -p chaos/core/src/main/java/com/agentengine/chaos/core/{injection,metrics,evaluation,enforcement,engine,reporting}
mkdir -p chaos/core/src/test/java/com/agentengine/chaos/core/{evaluation,enforcement,metrics,engine,reporting}
```

- [ ] **Step 5: Verify build compiles (empty modules)**

```bash
./gradlew :chaos:api:compileJava :chaos:core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add chaos/
git commit -m "feat(chaos): add chaos:api and chaos:core Gradle build files"
```

---

### Task 3: Implement enums in `chaos:api`

**Files:**
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/FaultType.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/BlastRadiusScope.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/ExperimentStatus.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/CriterionType.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/WorkflowPhase.java`

- [ ] **Step 1: Create `FaultType.java`**

```java
package com.agentengine.chaos.api;

import java.util.Locale;

public enum FaultType {
    // Infrastructure — submitted as Chaos Mesh Workflow CRDs
    POD_KILL,
    NETWORK_PARTITION,
    NETWORK_LATENCY,
    NETWORK_PACKET_LOSS,
    CLUSTER_PARTITION,
    CPU_STRESS,
    MEMORY_STRESS,
    DISK_STRESS,

    // Persistence — injected via Toxiproxy
    DATABASE_FAILURE,
    EVENT_JOURNAL_FAILURE,
    SNAPSHOT_STORE_FAILURE,

    // External dependencies — injected via Toxiproxy / WireMock
    LLM_PROVIDER_UNAVAILABLE,
    LLM_PROVIDER_LATENCY,
    CONNECTOR_FAILURE,

    // Actor-level — injected via Pekko BehaviorInterceptor
    MESSAGE_DELAY,
    MESSAGE_DROP,

    UNKNOWN;

    public static FaultType valueOfOrDefault(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
```

- [ ] **Step 2: Create `BlastRadiusScope.java`**

```java
package com.agentengine.chaos.api;

import java.util.Locale;

public enum BlastRadiusScope {
    SINGLE_POD,
    SERVICE,
    NAMESPACE,
    CLUSTER,
    UNKNOWN;

    public static BlastRadiusScope valueOfOrDefault(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
```

- [ ] **Step 3: Create `ExperimentStatus.java`**

```java
package com.agentengine.chaos.api;

import java.util.Locale;

public enum ExperimentStatus {
    SCHEDULED,
    RUNNING,
    PASSED,
    FAILED,
    ABORTED,
    UNKNOWN;

    public static ExperimentStatus valueOfOrDefault(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
```

- [ ] **Step 4: Create `CriterionType.java`**

```java
package com.agentengine.chaos.api;

import java.util.Locale;

public enum CriterionType {
    MAX_ERROR_RATE,
    MAX_LATENCY_P99,
    MIN_SUCCESS_RATE,
    MAX_RECOVERY_TIME,
    ZERO_DATA_LOSS,
    UNKNOWN;

    public static CriterionType valueOfOrDefault(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
```

- [ ] **Step 5: Create `WorkflowPhase.java`**

```java
package com.agentengine.chaos.api;

import java.util.Locale;

/**
 * Represents the lifecycle phase of a Chaos Mesh Workflow CRD.
 * Maps to the {@code .status.phase} field returned by the Chaos Mesh API.
 */
public enum WorkflowPhase {
    RUNNING,
    SUCCEEDED,
    FAILED,
    ABORTED,
    UNKNOWN;

    public static WorkflowPhase valueOfOrDefault(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew :chaos:api:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add chaos/api/src/
git commit -m "feat(chaos): add FaultType, BlastRadiusScope, ExperimentStatus, CriterionType, WorkflowPhase enums"
```

---

### Task 4: Implement `FaultParameters` sealed interface and parameter records

**Files:**
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/FaultParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/PodKillParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/NetworkLatencyParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/NetworkPartitionParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/PacketLossParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/CpuStressParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/MemoryStressParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/DiskStressParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/DatabaseFailureParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/LlmProviderLatencyParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/MessageDelayParameters.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/MessageDropParameters.java`

- [ ] **Step 1: Create `FaultParameters.java`**

```java
package com.agentengine.chaos.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "parametersType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PodKillParameters.class,           name = "PodKillParameters"),
    @JsonSubTypes.Type(value = NetworkLatencyParameters.class,    name = "NetworkLatencyParameters"),
    @JsonSubTypes.Type(value = NetworkPartitionParameters.class,  name = "NetworkPartitionParameters"),
    @JsonSubTypes.Type(value = PacketLossParameters.class,        name = "PacketLossParameters"),
    @JsonSubTypes.Type(value = CpuStressParameters.class,         name = "CpuStressParameters"),
    @JsonSubTypes.Type(value = MemoryStressParameters.class,      name = "MemoryStressParameters"),
    @JsonSubTypes.Type(value = DiskStressParameters.class,        name = "DiskStressParameters"),
    @JsonSubTypes.Type(value = DatabaseFailureParameters.class,   name = "DatabaseFailureParameters"),
    @JsonSubTypes.Type(value = LlmProviderLatencyParameters.class,name = "LlmProviderLatencyParameters"),
    @JsonSubTypes.Type(value = MessageDelayParameters.class,      name = "MessageDelayParameters"),
    @JsonSubTypes.Type(value = MessageDropParameters.class,       name = "MessageDropParameters")
})
public sealed interface FaultParameters permits
    PodKillParameters,
    NetworkLatencyParameters,
    NetworkPartitionParameters,
    PacketLossParameters,
    CpuStressParameters,
    MemoryStressParameters,
    DiskStressParameters,
    DatabaseFailureParameters,
    LlmProviderLatencyParameters,
    MessageDelayParameters,
    MessageDropParameters {}
```

- [ ] **Step 2: Create all parameter records**

`PodKillParameters.java`:
```java
package com.agentengine.chaos.api;

public record PodKillParameters(int count) implements FaultParameters {}
```

`NetworkLatencyParameters.java`:
```java
package com.agentengine.chaos.api;

import java.time.Duration;

public record NetworkLatencyParameters(Duration latency) implements FaultParameters {}
```

`NetworkPartitionParameters.java`:
```java
package com.agentengine.chaos.api;

import java.util.List;

public record NetworkPartitionParameters(List<String> blockedServices) implements FaultParameters {}
```

`PacketLossParameters.java`:
```java
package com.agentengine.chaos.api;

public record PacketLossParameters(double lossPercentage) implements FaultParameters {}
```

`CpuStressParameters.java`:
```java
package com.agentengine.chaos.api;

public record CpuStressParameters(int cpuPercentage) implements FaultParameters {}
```

`MemoryStressParameters.java`:
```java
package com.agentengine.chaos.api;

public record MemoryStressParameters(String memoryLimit) implements FaultParameters {}
```

`DiskStressParameters.java`:
```java
package com.agentengine.chaos.api;

public record DiskStressParameters(String diskIORate) implements FaultParameters {}
```

`DatabaseFailureParameters.java`:
```java
package com.agentengine.chaos.api;

/** The {@code target} is the Toxiproxy proxy name, e.g. "postgresql" or "mongodb". */
public record DatabaseFailureParameters(String target) implements FaultParameters {}
```

`LlmProviderLatencyParameters.java`:
```java
package com.agentengine.chaos.api;

import java.time.Duration;

public record LlmProviderLatencyParameters(Duration latency) implements FaultParameters {}
```

`MessageDelayParameters.java`:
```java
package com.agentengine.chaos.api;

import java.time.Duration;

public record MessageDelayParameters(Duration delay, double percentage) implements FaultParameters {}
```

`MessageDropParameters.java`:
```java
package com.agentengine.chaos.api;

public record MessageDropParameters(double dropPercentage) implements FaultParameters {}
```

- [ ] **Step 3: Build to confirm no compilation errors**

```bash
./gradlew :chaos:api:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add chaos/api/src/
git commit -m "feat(chaos): add FaultParameters sealed interface and all parameter record types"
```

---

### Task 5: Implement domain records in `chaos:api`

**Files:**
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/TargetSelector.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/BlastRadius.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/SuccessCriterion.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/FaultEvent.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/CriterionFailure.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/SteadyStateMetrics.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/EvaluationResult.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/WorkflowResult.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/ExperimentDefinition.java`
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/ExperimentResult.java`

- [ ] **Step 1: Create `TargetSelector.java`**

```java
package com.agentengine.chaos.api;

import java.util.Map;
import java.util.Optional;

public record TargetSelector(
    String namespace,
    String service,
    Map<String, String> podLabels,
    Optional<String> entityId  // specific SessionActor entity ID for Pekko faults
) {}
```

- [ ] **Step 2: Create `BlastRadius.java`**

```java
package com.agentengine.chaos.api;

public record BlastRadius(
    BlastRadiusScope scope,
    int maxPods,
    double maxPercentage
) {
    public static BlastRadius defaultService() {
        return new BlastRadius(BlastRadiusScope.SERVICE, 2, 25.0);
    }
}
```

- [ ] **Step 3: Create `SuccessCriterion.java`**

```java
package com.agentengine.chaos.api;

public record SuccessCriterion(
    CriterionType type,
    double threshold,
    String description
) {}
```

- [ ] **Step 4: Create `FaultEvent.java`**

```java
package com.agentengine.chaos.api;

import java.time.Instant;

public record FaultEvent(
    String faultId,
    FaultType faultType,
    TargetSelector targetSelector,
    Instant startTime,
    Instant endTime
) {}
```

- [ ] **Step 5: Create `CriterionFailure.java`**

```java
package com.agentengine.chaos.api;

public record CriterionFailure(
    SuccessCriterion criterion,
    double actualValue,
    double expectedThreshold
) {}
```

- [ ] **Step 6: Create `SteadyStateMetrics.java`**

```java
package com.agentengine.chaos.api;

import java.time.Duration;
import java.time.Instant;

public record SteadyStateMetrics(
    double successRate,
    Duration p50Latency,
    Duration p95Latency,
    Duration p99Latency,
    double errorRate,
    int activeSessions,
    Duration eventJournalLag,
    Duration mongoLatency,
    int podRestarts,
    Instant timestamp
) {
    public static SteadyStateMetrics empty() {
        return new SteadyStateMetrics(
            1.0, Duration.ZERO, Duration.ZERO, Duration.ZERO,
            0.0, 0, Duration.ZERO, Duration.ZERO, 0,
            Instant.now()
        );
    }
}
```

- [ ] **Step 7: Create `EvaluationResult.java`**

```java
package com.agentengine.chaos.api;

import java.util.List;

public record EvaluationResult(
    boolean passed,
    List<CriterionFailure> failures,
    SteadyStateMetrics baseline,
    List<SteadyStateMetrics> duringFault,
    SteadyStateMetrics postRecovery
) {}
```

- [ ] **Step 8: Create `WorkflowResult.java`**

```java
package com.agentengine.chaos.api;

import java.time.Instant;

/**
 * Captures the outcome of submitting a Chaos Mesh Workflow CRD.
 * {@code phase} reflects the Workflow's {@code .status.phase} at the time of the call.
 */
public record WorkflowResult(
    String workflowId,
    WorkflowPhase phase,
    Instant submittedAt,
    Instant completedAt
) {}
```

- [ ] **Step 9: Create `ExperimentDefinition.java`**

```java
package com.agentengine.chaos.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ExperimentDefinition(
    String name,
    String description,
    TargetSelector target,
    FaultType faultType,
    FaultParameters parameters,
    Duration duration,
    BlastRadius blastRadius,
    List<SuccessCriterion> successCriteria,
    Duration observationWindow,
    Duration recoveryWindow,
    Optional<String> schedule,
    Map<String, String> labels,
    boolean dryRun,
    boolean approved
) {}
```

- [ ] **Step 10: Create `ExperimentResult.java`**

```java
package com.agentengine.chaos.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ExperimentResult(
    String experimentId,
    String experimentName,
    Instant startTime,
    Instant endTime,
    ExperimentStatus status,
    SteadyStateMetrics baseline,
    List<SteadyStateMetrics> duringFaultMetrics,
    SteadyStateMetrics postRecovery,
    EvaluationResult evaluation,
    List<FaultEvent> faultEvents,
    Duration recoveryTime,
    Optional<String> abortReason
) {}
```

- [ ] **Step 11: Build to confirm no compilation errors**

```bash
./gradlew :chaos:api:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 12: Commit**

```bash
git add chaos/api/src/main/
git commit -m "feat(chaos): add all domain records — TargetSelector, BlastRadius, SteadyStateMetrics, WorkflowResult, ExperimentDefinition, ExperimentResult, etc."
```

---

### Task 6: Implement interfaces and no-op stubs in `chaos:core`

**Files:**
- Create: `chaos/api/src/main/java/com/agentengine/chaos/api/ChaosService.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/injection/FaultInjector.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/injection/NoOpFaultInjector.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/engine/WorkflowSubmitter.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/engine/NoOpWorkflowSubmitter.java`

- [ ] **Step 1: Create `ChaosService.java`**

```java
package com.agentengine.chaos.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ChaosService {

    CompletionStage<ExperimentResult> executeExperiment(ExperimentDefinition experiment);

    CompletionStage<Void> scheduleExperiment(ExperimentDefinition experiment, String cronExpression);

    CompletionStage<Void> emergencyStop(String experimentId);

    List<ExperimentResult> getExperimentHistory(String targetService);
}
```

- [ ] **Step 2: Create `FaultInjector.java`**

```java
package com.agentengine.chaos.core.injection;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.FaultParameters;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import java.util.concurrent.CompletionStage;

/**
 * Injects and removes application-level faults (database, LLM provider, actor message
 * interception). Infrastructure faults (pod kills, network partitions) go through
 * {@link WorkflowSubmitter} instead.
 */
public interface FaultInjector {

    /** Injects the fault and returns a faultId that identifies the active fault for removal. */
    CompletionStage<String> injectFault(
        FaultType faultType,
        TargetSelector target,
        FaultParameters parameters,
        BlastRadius blastRadius
    );

    CompletionStage<Void> removeFault(String faultId);

    boolean supports(FaultType faultType);
}
```

- [ ] **Step 3: Create `NoOpFaultInjector.java`**

```java
package com.agentengine.chaos.core.injection;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.FaultParameters;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** No-op injector used in tests and dry-run mode. Injects nothing but returns valid fault IDs. */
public final class NoOpFaultInjector implements FaultInjector {

    @Override
    public CompletionStage<String> injectFault(
            final FaultType faultType,
            final TargetSelector target,
            final FaultParameters parameters,
            final BlastRadius blastRadius) {
        return CompletableFuture.completedFuture("noop-fault-" + UUID.randomUUID());
    }

    @Override
    public CompletionStage<Void> removeFault(final String faultId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean supports(final FaultType faultType) {
        return true;
    }
}
```

- [ ] **Step 4: Create `WorkflowSubmitter.java`**

```java
package com.agentengine.chaos.core.engine;

import com.agentengine.chaos.api.ExperimentDefinition;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.WorkflowPhase;
import com.agentengine.chaos.api.WorkflowResult;

/**
 * Submits and monitors Chaos Mesh Workflow CRDs for infrastructure fault injection.
 * The real implementation (Plan 2) creates the CRD via the Fabric8 client.
 * The no-op implementation is used in tests and dry-run mode.
 */
public interface WorkflowSubmitter {

    /**
     * Submits a Chaos Mesh Workflow CRD for the given experiment and returns immediately
     * with the initial workflow status. Does not block for completion.
     */
    WorkflowResult submit(ExperimentDefinition experiment);

    /**
     * Polls the current phase of the Chaos Mesh Workflow CRD identified by {@code workflowId}.
     * Returns {@link WorkflowPhase#UNKNOWN} if the workflow cannot be found.
     */
    WorkflowPhase getPhase(String workflowId);

    /** Cancels an in-progress Chaos Mesh Workflow CRD. */
    void cancel(String workflowId);

    /**
     * Returns true if this submitter handles the given fault type.
     * Infrastructure faults (POD_KILL, NETWORK_*, CPU/MEMORY/DISK stress, CLUSTER_PARTITION)
     * return true; application-level faults return false.
     */
    boolean supportsWorkflow(FaultType faultType);
}
```

- [ ] **Step 5: Create `NoOpWorkflowSubmitter.java`**

```java
package com.agentengine.chaos.core.engine;

import com.agentengine.chaos.api.ExperimentDefinition;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.WorkflowPhase;
import com.agentengine.chaos.api.WorkflowResult;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * No-op WorkflowSubmitter for tests and dry-run mode.
 * Returns SUCCEEDED immediately — no Chaos Mesh CRD is ever created.
 */
public final class NoOpWorkflowSubmitter implements WorkflowSubmitter {

    private static final Set<FaultType> INFRASTRUCTURE_FAULTS = Set.of(
        FaultType.POD_KILL,
        FaultType.NETWORK_PARTITION,
        FaultType.NETWORK_LATENCY,
        FaultType.NETWORK_PACKET_LOSS,
        FaultType.CLUSTER_PARTITION,
        FaultType.CPU_STRESS,
        FaultType.MEMORY_STRESS,
        FaultType.DISK_STRESS
    );

    @Override
    public WorkflowResult submit(final ExperimentDefinition experiment) {
        final Instant now = Instant.now();
        return new WorkflowResult("noop-workflow-" + UUID.randomUUID(), WorkflowPhase.SUCCEEDED, now, now);
    }

    @Override
    public WorkflowPhase getPhase(final String workflowId) {
        return WorkflowPhase.SUCCEEDED;
    }

    @Override
    public void cancel(final String workflowId) {
        // no-op
    }

    @Override
    public boolean supportsWorkflow(final FaultType faultType) {
        return INFRASTRUCTURE_FAULTS.contains(faultType);
    }
}
```

- [ ] **Step 6: Build to confirm**

```bash
./gradlew :chaos:api:compileJava :chaos:core:compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add chaos/
git commit -m "feat(chaos): add ChaosService, FaultInjector, WorkflowSubmitter interfaces and no-op stubs"
```

---

### Task 7: Implement `SuccessCriteriaEvaluator` (TDD)

**Files:**
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/evaluation/DataLossValidator.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/evaluation/NoOpDataLossValidator.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/evaluation/SuccessCriteriaEvaluator.java`
- Create: `chaos/core/src/test/java/com/agentengine/chaos/core/evaluation/SuccessCriteriaEvaluatorTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.agentengine.chaos.core.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.chaos.api.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuccessCriteriaEvaluatorTest {

    private DataLossValidator dataLossValidator;
    private SuccessCriteriaEvaluator evaluator;

    private static SteadyStateMetrics metrics(
            final double successRate, final double errorRate, final Duration p99) {
        return new SteadyStateMetrics(
            successRate, Duration.ofMillis(10), Duration.ofMillis(50), p99,
            errorRate, 5, Duration.ZERO, Duration.ZERO, 0, Instant.now()
        );
    }

    @BeforeEach
    void setUp() {
        dataLossValidator = mock(DataLossValidator.class);
        evaluator = new SuccessCriteriaEvaluator(dataLossValidator);
    }

    @Test
    void shouldPassWhenErrorRateBelowThreshold() {
        final var criterion = new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.05, "");
        final EvaluationResult result = evaluator.evaluate(
            List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            metrics(0.99, 0.01, Duration.ofMillis(200)), Duration.ofSeconds(10)
        );

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void shouldFailWhenErrorRateExceedsThreshold() {
        final var criterion = new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.05, "error rate must be ≤5%");
        final EvaluationResult result = evaluator.evaluate(
            List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            metrics(0.85, 0.15, Duration.ofMillis(200)), Duration.ofSeconds(10)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).criterion().type()).isEqualTo(CriterionType.MAX_ERROR_RATE);
        assertThat(result.failures().get(0).actualValue()).isEqualTo(0.15);
        assertThat(result.failures().get(0).expectedThreshold()).isEqualTo(0.05);
    }

    @Test
    void shouldPassWhenP99LatencyBelowThresholdMillis() {
        final var criterion = new SuccessCriterion(CriterionType.MAX_LATENCY_P99, 1000.0, "");
        assertThat(evaluator.evaluate(List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            metrics(1.0, 0.0, Duration.ofMillis(800)), Duration.ofSeconds(5)).passed()).isTrue();
    }

    @Test
    void shouldFailWhenP99LatencyExceedsThresholdMillis() {
        final var criterion = new SuccessCriterion(CriterionType.MAX_LATENCY_P99, 1000.0, "");
        final EvaluationResult result = evaluator.evaluate(List.of(criterion),
            SteadyStateMetrics.empty(), List.of(),
            metrics(1.0, 0.0, Duration.ofMillis(1500)), Duration.ofSeconds(5));

        assertThat(result.passed()).isFalse();
        assertThat(result.failures().get(0).actualValue()).isEqualTo(1500.0);
    }

    @Test
    void shouldPassWhenSuccessRateAboveThreshold() {
        final var criterion = new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.95, "");
        assertThat(evaluator.evaluate(List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            metrics(0.96, 0.04, Duration.ofMillis(100)), Duration.ofSeconds(5)).passed()).isTrue();
    }

    @Test
    void shouldFailWhenSuccessRateBelowThreshold() {
        final var criterion = new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.50, "");
        assertThat(evaluator.evaluate(List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            metrics(0.40, 0.60, Duration.ofMillis(100)), Duration.ofSeconds(5)).passed()).isFalse();
    }

    @Test
    void shouldPassWhenRecoveryTimeWithinThreshold() {
        final var criterion = new SuccessCriterion(CriterionType.MAX_RECOVERY_TIME, 60.0, "");
        assertThat(evaluator.evaluate(List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            SteadyStateMetrics.empty(), Duration.ofSeconds(45)).passed()).isTrue();
    }

    @Test
    void shouldFailWhenRecoveryTimeExceedsThreshold() {
        final var criterion = new SuccessCriterion(CriterionType.MAX_RECOVERY_TIME, 60.0, "");
        final EvaluationResult result = evaluator.evaluate(List.of(criterion),
            SteadyStateMetrics.empty(), List.of(), SteadyStateMetrics.empty(), Duration.ofSeconds(90));

        assertThat(result.passed()).isFalse();
        assertThat(result.failures().get(0).actualValue()).isEqualTo(90.0);
    }

    @Test
    void shouldPassZeroDataLossWhenValidatorReportsNoLoss() {
        when(dataLossValidator.hasDataLoss()).thenReturn(false);
        final var criterion = new SuccessCriterion(CriterionType.ZERO_DATA_LOSS, 0.0, "");
        assertThat(evaluator.evaluate(List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            SteadyStateMetrics.empty(), Duration.ZERO).passed()).isTrue();
    }

    @Test
    void shouldFailZeroDataLossWhenValidatorDetectsLoss() {
        when(dataLossValidator.hasDataLoss()).thenReturn(true);
        final var criterion = new SuccessCriterion(CriterionType.ZERO_DATA_LOSS, 0.0, "");
        assertThat(evaluator.evaluate(List.of(criterion), SteadyStateMetrics.empty(), List.of(),
            SteadyStateMetrics.empty(), Duration.ZERO).passed()).isFalse();
    }

    @Test
    void shouldFailExperimentWhenAnySingleCriterionFails() {
        final List<SuccessCriterion> criteria = List.of(
            new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.20, ""),   // passes (actual 0.15)
            new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.99, "")  // fails (actual 0.85)
        );

        final EvaluationResult result = evaluator.evaluate(
            criteria, SteadyStateMetrics.empty(), List.of(),
            metrics(0.85, 0.15, Duration.ofMillis(100)), Duration.ofSeconds(5)
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).criterion().type()).isEqualTo(CriterionType.MIN_SUCCESS_RATE);
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.evaluation.SuccessCriteriaEvaluatorTest"
```

Expected: FAIL — `DataLossValidator`, `SuccessCriteriaEvaluator` not found.

- [ ] **Step 3: Create `DataLossValidator.java`**

```java
package com.agentengine.chaos.core.evaluation;

/**
 * Checks whether any data loss occurred during a chaos experiment.
 * The no-op implementation always returns false. The real implementation
 * (EventJournalValidator in Plan 4) queries PostgreSQL to verify event replay integrity.
 */
public interface DataLossValidator {
    boolean hasDataLoss();
}
```

- [ ] **Step 4: Create `NoOpDataLossValidator.java`**

```java
package com.agentengine.chaos.core.evaluation;

public final class NoOpDataLossValidator implements DataLossValidator {
    @Override
    public boolean hasDataLoss() {
        return false;
    }
}
```

- [ ] **Step 5: Create `SuccessCriteriaEvaluator.java`**

```java
package com.agentengine.chaos.core.evaluation;

import com.agentengine.chaos.api.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SuccessCriteriaEvaluator {

    private final DataLossValidator dataLossValidator;

    public SuccessCriteriaEvaluator(final DataLossValidator dataLossValidator) {
        this.dataLossValidator = dataLossValidator;
    }

    public EvaluationResult evaluate(
            final List<SuccessCriterion> criteria,
            final SteadyStateMetrics baseline,
            final List<SteadyStateMetrics> duringFault,
            final SteadyStateMetrics postRecovery,
            final Duration recoveryTime) {

        final var failures = new ArrayList<CriterionFailure>();

        for (final var criterion : criteria) {
            evaluate(criterion, postRecovery, recoveryTime).ifPresent(failures::add);
        }

        return new EvaluationResult(failures.isEmpty(), List.copyOf(failures),
                baseline, List.copyOf(duringFault), postRecovery);
    }

    private Optional<CriterionFailure> evaluate(
            final SuccessCriterion criterion,
            final SteadyStateMetrics post,
            final Duration recoveryTime) {

        return switch (criterion.type()) {
            case MAX_ERROR_RATE -> post.errorRate() > criterion.threshold()
                ? Optional.of(new CriterionFailure(criterion, post.errorRate(), criterion.threshold()))
                : Optional.empty();

            case MAX_LATENCY_P99 -> post.p99Latency().toMillis() > criterion.threshold()
                ? Optional.of(new CriterionFailure(criterion,
                    (double) post.p99Latency().toMillis(), criterion.threshold()))
                : Optional.empty();

            case MIN_SUCCESS_RATE -> post.successRate() < criterion.threshold()
                ? Optional.of(new CriterionFailure(criterion, post.successRate(), criterion.threshold()))
                : Optional.empty();

            case MAX_RECOVERY_TIME -> recoveryTime.toSeconds() > criterion.threshold()
                ? Optional.of(new CriterionFailure(criterion,
                    (double) recoveryTime.toSeconds(), criterion.threshold()))
                : Optional.empty();

            case ZERO_DATA_LOSS -> dataLossValidator.hasDataLoss()
                ? Optional.of(new CriterionFailure(criterion, 1.0, 0.0))
                : Optional.empty();

            case UNKNOWN -> Optional.empty();
        };
    }
}
```

- [ ] **Step 6: Run tests — expect all pass**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.evaluation.SuccessCriteriaEvaluatorTest"
```

Expected: `BUILD SUCCESSFUL`, 10 tests passed.

- [ ] **Step 7: Commit**

```bash
git add chaos/core/src/
git commit -m "feat(chaos): implement SuccessCriteriaEvaluator with all 5 criterion types (TDD)"
```

---

### Task 8: Implement `BlastRadiusEnforcer` (TDD)

**Files:**
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/enforcement/PodResolver.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/enforcement/BlastRadiusViolationException.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/enforcement/BlastRadiusEnforcer.java`
- Create: `chaos/core/src/test/java/com/agentengine/chaos/core/enforcement/BlastRadiusEnforcerTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.agentengine.chaos.core.enforcement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.TargetSelector;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlastRadiusEnforcerTest {

    private static final TargetSelector TARGET = new TargetSelector(
        "agent-engine", "runtime", Map.of("app", "runtime"), Optional.empty()
    );

    @Test
    void shouldPassWhenMatchingPodsDoNotExceedMaxPods() {
        // resolver finds 2 pods; maxPods=3 → safe
        final var enforcer = new BlastRadiusEnforcer(selector -> 2);
        final BlastRadius radius = new BlastRadius(BlastRadiusScope.SERVICE, 3, 50.0);

        assertThatCode(() -> enforcer.enforceOrThrow(TARGET, radius))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenMatchingPodsExceedMaxPods() {
        // resolver finds 5 pods; maxPods=2 → violation
        final var enforcer = new BlastRadiusEnforcer(selector -> 5);
        final BlastRadius radius = new BlastRadius(BlastRadiusScope.SERVICE, 2, 100.0);

        assertThatThrownBy(() -> enforcer.enforceOrThrow(TARGET, radius))
            .isInstanceOf(BlastRadiusViolationException.class)
            .hasMessageContaining("would affect 5 pods, exceeding maxPods=2");
    }

    @Test
    void shouldPassWhenSinglePodScopeWithExactlyOneMatchingPod() {
        final var enforcer = new BlastRadiusEnforcer(selector -> 1);
        final BlastRadius radius = new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0);

        assertThatCode(() -> enforcer.enforceOrThrow(TARGET, radius))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenSinglePodScopeMatchesMoreThanOnePod() {
        // resolver finds 3 pods for the selector, but SINGLE_POD allows exactly 1
        final var enforcer = new BlastRadiusEnforcer(selector -> 3);
        final BlastRadius radius = new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0);

        assertThatThrownBy(() -> enforcer.enforceOrThrow(TARGET, radius))
            .isInstanceOf(BlastRadiusViolationException.class)
            .hasMessageContaining("SINGLE_POD scope requires exactly 1 matching pod");
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.enforcement.BlastRadiusEnforcerTest"
```

Expected: FAIL — classes not found.

- [ ] **Step 3: Create `PodResolver.java`**

```java
package com.agentengine.chaos.core.enforcement;

import com.agentengine.chaos.api.TargetSelector;

/**
 * Returns the number of running pods matching the given selector.
 * Injected as a lambda backed by the Kubernetes API in production;
 * replaced with a stub in tests.
 */
@FunctionalInterface
public interface PodResolver {
    int countMatchingPods(TargetSelector selector);
}
```

- [ ] **Step 4: Create `BlastRadiusViolationException.java`**

```java
package com.agentengine.chaos.core.enforcement;

public final class BlastRadiusViolationException extends RuntimeException {
    public BlastRadiusViolationException(final String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Create `BlastRadiusEnforcer.java`**

```java
package com.agentengine.chaos.core.enforcement;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.TargetSelector;

public final class BlastRadiusEnforcer {

    private final PodResolver podResolver;

    public BlastRadiusEnforcer(final PodResolver podResolver) {
        this.podResolver = podResolver;
    }

    public void enforceOrThrow(final TargetSelector target, final BlastRadius blastRadius) {
        final int matchingPods = podResolver.countMatchingPods(target);

        if (blastRadius.scope() == BlastRadiusScope.SINGLE_POD && matchingPods != 1) {
            throw new BlastRadiusViolationException(
                "SINGLE_POD scope requires exactly 1 matching pod, but found " + matchingPods);
        }

        if (matchingPods > blastRadius.maxPods()) {
            throw new BlastRadiusViolationException(
                "Experiment would affect " + matchingPods
                + " pods, exceeding maxPods=" + blastRadius.maxPods());
        }
    }
}
```

- [ ] **Step 6: Run tests — expect all pass**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.enforcement.BlastRadiusEnforcerTest"
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 7: Commit**

```bash
git add chaos/core/src/
git commit -m "feat(chaos): implement BlastRadiusEnforcer with PodResolver interface (TDD)"
```

---

### Task 9: Implement `SteadyStateAnalyzer` and `MetricsCollector` interface

**Files:**
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/metrics/MetricsCollector.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/metrics/SteadyStateAnalyzer.java`
- Create: `chaos/core/src/test/java/com/agentengine/chaos/core/metrics/SteadyStateAnalyzerTest.java`

- [ ] **Step 1: Write failing tests for `SteadyStateAnalyzer`**

```java
package com.agentengine.chaos.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.SteadyStateMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SteadyStateAnalyzerTest {

    private SteadyStateAnalyzer analyzer;

    private static SteadyStateMetrics metricsAt(
            final Instant t, final double successRate, final double errorRate) {
        return new SteadyStateMetrics(
            successRate, Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(200),
            errorRate, 5, Duration.ZERO, Duration.ZERO, 0, t
        );
    }

    @BeforeEach
    void setUp() {
        analyzer = new SteadyStateAnalyzer(0.05);  // 5% deviation threshold
    }

    @Test
    void shouldReturnNearZeroRecoveryTimeWhenPostRecoveryMatchesBaseline() {
        final var baseline = metricsAt(Instant.EPOCH, 1.0, 0.0);
        final var postRecovery = metricsAt(Instant.EPOCH.plusSeconds(90), 1.0, 0.0);

        // No during-fault snapshots; post-recovery already matches → engine falls through
        // to the post-recovery timestamp, yielding faultRemovalTime→postRecovery gap = 60s
        // But with an empty during-fault list, we fall back to the postRecovery timestamp gap.
        final Duration recoveryTime = analyzer.computeRecoveryTime(
            baseline, List.of(), postRecovery, Instant.EPOCH.plusSeconds(30)
        );

        // gap between faultRemovalTime (T+30) and postRecovery.timestamp (T+90) = 60s
        assertThat(recoveryTime).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldDetectRecoveryWhenDuringFaultSnapshotReturnsThroughThreshold() {
        final var baseline = metricsAt(Instant.EPOCH, 1.0, 0.0);
        final var faultRemovalTime = Instant.EPOCH.plusSeconds(30);

        final var during1 = metricsAt(faultRemovalTime.plusSeconds(5),  0.50, 0.50);  // outside threshold
        final var during2 = metricsAt(faultRemovalTime.plusSeconds(15), 0.90, 0.10);  // outside threshold
        final var during3 = metricsAt(faultRemovalTime.plusSeconds(25), 0.97, 0.03);  // within 5% of baseline

        final Duration recoveryTime = analyzer.computeRecoveryTime(
            baseline,
            List.of(during1, during2, during3),
            metricsAt(faultRemovalTime.plusSeconds(60), 0.99, 0.01),
            faultRemovalTime
        );

        // First snapshot within threshold is at +25s → recoveryTime ≈ 25s
        assertThat(recoveryTime).isBetween(Duration.ofSeconds(20), Duration.ofSeconds(30));
    }

    @Test
    void shouldFallBackToPostRecoveryTimestampWhenAllDuringSnapshotsAreOutsideThreshold() {
        final var baseline = metricsAt(Instant.EPOCH, 1.0, 0.0);
        final var faultRemovalTime = Instant.EPOCH.plusSeconds(30);
        final var postRecovery = metricsAt(faultRemovalTime.plusSeconds(60), 0.98, 0.02);

        final Duration recoveryTime = analyzer.computeRecoveryTime(
            baseline,
            List.of(metricsAt(faultRemovalTime.plusSeconds(10), 0.4, 0.6)),  // outside threshold
            postRecovery,
            faultRemovalTime
        );

        assertThat(recoveryTime).isBetween(Duration.ofSeconds(55), Duration.ofSeconds(65));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.metrics.SteadyStateAnalyzerTest"
```

Expected: FAIL — `SteadyStateAnalyzer` not found.

- [ ] **Step 3: Create `MetricsCollector.java`**

```java
package com.agentengine.chaos.core.metrics;

import com.agentengine.chaos.api.SteadyStateMetrics;
import java.time.Duration;
import java.util.List;

public interface MetricsCollector {

    /** Collects metrics over {@code window}, sampling every 10 seconds, and returns the average. */
    SteadyStateMetrics collectBaseline(Duration window);

    /** Takes a single instantaneous snapshot of all metrics. */
    SteadyStateMetrics collectSnapshot();

    /** Polls snapshots at {@code interval} for the given {@code duration}. */
    List<SteadyStateMetrics> collectDuring(Duration duration, Duration interval);
}
```

- [ ] **Step 4: Create `SteadyStateAnalyzer.java`**

```java
package com.agentengine.chaos.core.metrics;

import com.agentengine.chaos.api.SteadyStateMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Determines when the system has returned to steady state after fault removal. */
public final class SteadyStateAnalyzer {

    private final double deviationThreshold;  // e.g. 0.05 = 5%

    public SteadyStateAnalyzer(final double deviationThreshold) {
        this.deviationThreshold = deviationThreshold;
    }

    /**
     * Scans {@code duringFaultSnapshots} in order for the first snapshot within
     * {@link #deviationThreshold} of {@code baseline}. Returns the gap between
     * {@code faultRemovalTime} and that snapshot's timestamp. Falls back to the
     * gap between {@code faultRemovalTime} and {@code postRecovery.timestamp()}
     * when no intermediate snapshot meets the threshold.
     */
    public Duration computeRecoveryTime(
            final SteadyStateMetrics baseline,
            final List<SteadyStateMetrics> duringFaultSnapshots,
            final SteadyStateMetrics postRecovery,
            final Instant faultRemovalTime) {

        for (final var snapshot : duringFaultSnapshots) {
            if (isWithinThreshold(baseline, snapshot)) {
                return Duration.between(faultRemovalTime, snapshot.timestamp());
            }
        }

        return Duration.between(faultRemovalTime, postRecovery.timestamp());
    }

    private boolean isWithinThreshold(final SteadyStateMetrics baseline, final SteadyStateMetrics sample) {
        return Math.abs(baseline.successRate() - sample.successRate()) <= deviationThreshold
            && Math.abs(baseline.errorRate() - sample.errorRate()) <= deviationThreshold;
    }
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.metrics.SteadyStateAnalyzerTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add chaos/core/src/
git commit -m "feat(chaos): implement MetricsCollector interface and SteadyStateAnalyzer (TDD)"
```

---

### Task 10: Implement `ChaosEngine` with dual routing (TDD)

**Files:**
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/engine/ExperimentAbortedException.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/reporting/ExperimentReportGenerator.java` (stub)
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/reporting/ReportGenerationException.java`
- Create: `chaos/core/src/main/java/com/agentengine/chaos/core/engine/ChaosEngine.java`
- Create: `chaos/core/src/test/java/com/agentengine/chaos/core/engine/ChaosEngineTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.agentengine.chaos.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.agentengine.chaos.api.*;
import com.agentengine.chaos.core.enforcement.BlastRadiusEnforcer;
import com.agentengine.chaos.core.enforcement.BlastRadiusViolationException;
import com.agentengine.chaos.core.evaluation.NoOpDataLossValidator;
import com.agentengine.chaos.core.evaluation.SuccessCriteriaEvaluator;
import com.agentengine.chaos.core.injection.FaultInjector;
import com.agentengine.chaos.core.injection.NoOpFaultInjector;
import com.agentengine.chaos.core.metrics.MetricsCollector;
import com.agentengine.chaos.core.metrics.SteadyStateAnalyzer;
import com.agentengine.chaos.core.reporting.ExperimentReportGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChaosEngineTest {

    @Mock private MetricsCollector metricsCollector;
    @Mock private BlastRadiusEnforcer blastRadiusEnforcer;
    @Mock private WorkflowSubmitter workflowSubmitter;
    @Mock private FaultInjector faultInjector;

    private ChaosEngine engine;

    private static final SteadyStateMetrics GOOD_METRICS = new SteadyStateMetrics(
        1.0, Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(200),
        0.0, 5, Duration.ZERO, Duration.ZERO, 0, Instant.now()
    );

    private static ExperimentDefinition experimentWith(
            final FaultType faultType, final FaultParameters parameters,
            final List<SuccessCriterion> criteria) {
        return new ExperimentDefinition(
            "test-experiment", "desc",
            new TargetSelector("ns", "svc", Map.of(), Optional.empty()),
            faultType, parameters,
            Duration.ofMillis(10),
            BlastRadius.defaultService(),
            criteria,
            Duration.ofMillis(10),
            Duration.ofMillis(10),
            Optional.empty(), Map.of(), false, false
        );
    }

    private static ExperimentDefinition podKillExperiment(final List<SuccessCriterion> criteria) {
        return experimentWith(FaultType.POD_KILL, new PodKillParameters(1), criteria);
    }

    private static ExperimentDefinition messageDropExperiment(final List<SuccessCriterion> criteria) {
        return experimentWith(FaultType.MESSAGE_DROP, new MessageDropParameters(0.3), criteria);
    }

    @BeforeEach
    void setUp() {
        when(metricsCollector.collectBaseline(any())).thenReturn(GOOD_METRICS);
        when(metricsCollector.collectSnapshot()).thenReturn(GOOD_METRICS);
        when(metricsCollector.collectDuring(any(), any())).thenReturn(List.of(GOOD_METRICS));

        engine = new ChaosEngine(
            workflowSubmitter,
            faultInjector,
            metricsCollector,
            new SuccessCriteriaEvaluator(new NoOpDataLossValidator()),
            blastRadiusEnforcer,
            new SteadyStateAnalyzer(0.05),
            new ExperimentReportGenerator()
        );
    }

    @Test
    void shouldRouteInfrastructureFaultsThroughWorkflowSubmitter() {
        when(workflowSubmitter.supportsWorkflow(FaultType.POD_KILL)).thenReturn(true);
        when(workflowSubmitter.submit(any())).thenReturn(new WorkflowResult(
            "wf-1", WorkflowPhase.SUCCEEDED, Instant.now(), Instant.now()));

        engine.executeExperiment(podKillExperiment(List.of())).toCompletableFuture().join();

        verify(workflowSubmitter).submit(any());
        verify(faultInjector, never()).injectFault(any(), any(), any(), any());
    }

    @Test
    void shouldRouteApplicationFaultsThroughFaultInjector() {
        when(workflowSubmitter.supportsWorkflow(FaultType.MESSAGE_DROP)).thenReturn(false);
        when(faultInjector.injectFault(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture("fault-1"));
        when(faultInjector.removeFault(anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        engine.executeExperiment(messageDropExperiment(List.of())).toCompletableFuture().join();

        verify(faultInjector).injectFault(any(), any(), any(), any());
        verify(workflowSubmitter, never()).submit(any());
    }

    @Test
    void shouldReturnPassedWhenAllSuccessCriteriaPass() {
        when(workflowSubmitter.supportsWorkflow(FaultType.POD_KILL)).thenReturn(true);
        when(workflowSubmitter.submit(any())).thenReturn(new WorkflowResult(
            "wf-2", WorkflowPhase.SUCCEEDED, Instant.now(), Instant.now()));

        final var criterion = new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.05, "");
        final ExperimentResult result = engine.executeExperiment(
            podKillExperiment(List.of(criterion))).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(ExperimentStatus.PASSED);
        assertThat(result.evaluation().passed()).isTrue();
    }

    @Test
    void shouldReturnFailedWhenSuccessCriterionFails() {
        when(workflowSubmitter.supportsWorkflow(FaultType.MESSAGE_DROP)).thenReturn(false);
        when(faultInjector.injectFault(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture("fault-2"));
        when(faultInjector.removeFault(anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(metricsCollector.collectSnapshot()).thenReturn(new SteadyStateMetrics(
            0.40, Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(200),
            0.60, 5, Duration.ZERO, Duration.ZERO, 0, Instant.now()
        ));

        final var criterion = new SuccessCriterion(CriterionType.MIN_SUCCESS_RATE, 0.95, "");
        final ExperimentResult result = engine.executeExperiment(
            messageDropExperiment(List.of(criterion))).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(ExperimentStatus.FAILED);
        assertThat(result.evaluation().failures()).hasSize(1);
    }

    @Test
    void shouldAbortBeforeInjectionWhenBlastRadiusIsViolated() {
        doThrow(new BlastRadiusViolationException("too many pods"))
            .when(blastRadiusEnforcer).enforceOrThrow(any(), any());

        assertThatThrownBy(() ->
            engine.executeExperiment(podKillExperiment(List.of())).toCompletableFuture().join()
        ).hasCauseInstanceOf(BlastRadiusViolationException.class);

        // Neither path should have been entered
        verify(workflowSubmitter, never()).submit(any());
        verify(faultInjector, never()).injectFault(any(), any(), any(), any());
    }

    @Test
    void shouldPopulateResultWithExperimentNameTimesAndNonBlankId() {
        when(workflowSubmitter.supportsWorkflow(FaultType.POD_KILL)).thenReturn(true);
        when(workflowSubmitter.submit(any())).thenReturn(new WorkflowResult(
            "wf-3", WorkflowPhase.SUCCEEDED, Instant.now(), Instant.now()));

        final ExperimentResult result = engine.executeExperiment(
            podKillExperiment(List.of())).toCompletableFuture().join();

        assertThat(result.experimentName()).isEqualTo("test-experiment");
        assertThat(result.experimentId()).isNotBlank();
        assertThat(result.startTime()).isNotNull();
        assertThat(result.endTime()).isAfterOrEqualTo(result.startTime());
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.engine.ChaosEngineTest"
```

Expected: FAIL — `ChaosEngine`, `ExperimentReportGenerator` not found.

- [ ] **Step 3: Create `ExperimentAbortedException.java`**

```java
package com.agentengine.chaos.core.engine;

public final class ExperimentAbortedException extends RuntimeException {
    public ExperimentAbortedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Create `ReportGenerationException.java` and stub `ExperimentReportGenerator.java`**

`ReportGenerationException.java`:
```java
package com.agentengine.chaos.core.reporting;

public final class ReportGenerationException extends RuntimeException {
    public ReportGenerationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
```

Stub `ExperimentReportGenerator.java` (full implementation comes in Task 11):
```java
package com.agentengine.chaos.core.reporting;

import com.agentengine.chaos.api.ExperimentResult;

public final class ExperimentReportGenerator {

    public String toJson(final ExperimentResult result) {
        return "{}";  // stub — replaced in Task 11
    }

    public String toMarkdown(final ExperimentResult result) {
        return "# " + result.experimentName();  // stub
    }
}
```

- [ ] **Step 5: Create `ChaosEngine.java`**

```java
package com.agentengine.chaos.core.engine;

import com.agentengine.chaos.api.*;
import com.agentengine.chaos.core.enforcement.BlastRadiusEnforcer;
import com.agentengine.chaos.core.evaluation.SuccessCriteriaEvaluator;
import com.agentengine.chaos.core.injection.FaultInjector;
import com.agentengine.chaos.core.metrics.MetricsCollector;
import com.agentengine.chaos.core.metrics.SteadyStateAnalyzer;
import com.agentengine.chaos.core.reporting.ExperimentReportGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChaosEngine implements ChaosService {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosEngine.class);
    private static final Duration WORKFLOW_POLL_INTERVAL = Duration.ofSeconds(10);
    private static final Duration WORKFLOW_TIMEOUT_BUFFER = Duration.ofMinutes(2);

    private final WorkflowSubmitter workflowSubmitter;
    private final FaultInjector faultInjector;
    private final MetricsCollector metricsCollector;
    private final SuccessCriteriaEvaluator evaluator;
    private final BlastRadiusEnforcer blastRadiusEnforcer;
    private final SteadyStateAnalyzer steadyStateAnalyzer;
    private final ExperimentReportGenerator reportGenerator;

    public ChaosEngine(
            final WorkflowSubmitter workflowSubmitter,
            final FaultInjector faultInjector,
            final MetricsCollector metricsCollector,
            final SuccessCriteriaEvaluator evaluator,
            final BlastRadiusEnforcer blastRadiusEnforcer,
            final SteadyStateAnalyzer steadyStateAnalyzer,
            final ExperimentReportGenerator reportGenerator) {
        this.workflowSubmitter = workflowSubmitter;
        this.faultInjector = faultInjector;
        this.metricsCollector = metricsCollector;
        this.evaluator = evaluator;
        this.blastRadiusEnforcer = blastRadiusEnforcer;
        this.steadyStateAnalyzer = steadyStateAnalyzer;
        this.reportGenerator = reportGenerator;
    }

    @Override
    public CompletionStage<ExperimentResult> executeExperiment(final ExperimentDefinition experiment) {
        return CompletableFuture.supplyAsync(() -> runExperiment(experiment));
    }

    private ExperimentResult runExperiment(final ExperimentDefinition experiment) {
        final String experimentId = UUID.randomUUID().toString();
        final Instant startTime = Instant.now();

        LOG.info("Starting experiment id={} name={} fault={}",
            experimentId, experiment.name(), experiment.faultType());

        blastRadiusEnforcer.enforceOrThrow(experiment.target(), experiment.blastRadius());

        final SteadyStateMetrics baseline = metricsCollector.collectBaseline(experiment.observationWindow());

        return workflowSubmitter.supportsWorkflow(experiment.faultType())
            ? runViaWorkflow(experimentId, experiment, startTime, baseline)
            : runViaInjector(experimentId, experiment, startTime, baseline);
    }

    // --- Infrastructure fault path (Chaos Mesh Workflow CRD) ---

    private ExperimentResult runViaWorkflow(
            final String experimentId,
            final ExperimentDefinition experiment,
            final Instant startTime,
            final SteadyStateMetrics baseline) {

        final WorkflowResult workflow = workflowSubmitter.submit(experiment);
        final Instant faultStartedAt = Instant.now();

        LOG.info("Workflow submitted id={} workflow_id={}", experimentId, workflow.workflowId());

        final var duringMetrics = new ArrayList<SteadyStateMetrics>();
        final Instant deadline = faultStartedAt.plus(experiment.duration()).plus(WORKFLOW_TIMEOUT_BUFFER);

        WorkflowPhase phase = workflow.phase();
        while (!isTerminal(phase) && Instant.now().isBefore(deadline)) {
            sleep(WORKFLOW_POLL_INTERVAL);
            duringMetrics.add(metricsCollector.collectSnapshot());
            phase = workflowSubmitter.getPhase(workflow.workflowId());
        }

        final Instant faultEndedAt = Instant.now();

        if (phase == WorkflowPhase.FAILED || phase == WorkflowPhase.ABORTED) {
            LOG.warn("Workflow reached terminal phase={} id={} workflow_id={}",
                phase, experimentId, workflow.workflowId());
            return abortedResult(experimentId, experiment.name(), startTime,
                baseline, List.copyOf(duringMetrics),
                new FaultEvent(workflow.workflowId(), experiment.faultType(),
                    experiment.target(), faultStartedAt, faultEndedAt),
                "Chaos Mesh workflow phase: " + phase);
        }

        sleep(experiment.recoveryWindow());
        final SteadyStateMetrics postRecovery = metricsCollector.collectSnapshot();

        return buildResult(experimentId, experiment, startTime, baseline,
            List.copyOf(duringMetrics), postRecovery, faultStartedAt, faultEndedAt,
            workflow.workflowId());
    }

    // --- Application fault path (direct FaultInjector) ---

    private ExperimentResult runViaInjector(
            final String experimentId,
            final ExperimentDefinition experiment,
            final Instant startTime,
            final SteadyStateMetrics baseline) {

        final String faultId = faultInjector
            .injectFault(experiment.faultType(), experiment.target(),
                experiment.parameters(), experiment.blastRadius())
            .toCompletableFuture().join();
        final Instant faultStartedAt = Instant.now();

        LOG.info("Fault injected id={} fault_id={}", experimentId, faultId);

        final List<SteadyStateMetrics> during = metricsCollector.collectDuring(
            experiment.duration(), WORKFLOW_POLL_INTERVAL);

        faultInjector.removeFault(faultId).toCompletableFuture().join();
        final Instant faultEndedAt = Instant.now();

        sleep(experiment.recoveryWindow());
        final SteadyStateMetrics postRecovery = metricsCollector.collectSnapshot();

        return buildResult(experimentId, experiment, startTime, baseline,
            during, postRecovery, faultStartedAt, faultEndedAt, faultId);
    }

    // --- Shared helpers ---

    private ExperimentResult buildResult(
            final String experimentId,
            final ExperimentDefinition experiment,
            final Instant startTime,
            final SteadyStateMetrics baseline,
            final List<SteadyStateMetrics> during,
            final SteadyStateMetrics postRecovery,
            final Instant faultStartedAt,
            final Instant faultEndedAt,
            final String faultId) {

        final Duration recoveryTime = steadyStateAnalyzer.computeRecoveryTime(
            baseline, during, postRecovery, faultEndedAt);
        final EvaluationResult evaluation = evaluator.evaluate(
            experiment.successCriteria(), baseline, during, postRecovery, recoveryTime);
        final ExperimentStatus status = evaluation.passed()
            ? ExperimentStatus.PASSED
            : ExperimentStatus.FAILED;

        LOG.info("Experiment complete id={} status={}", experimentId, status);

        return new ExperimentResult(
            experimentId, experiment.name(), startTime, Instant.now(),
            status, baseline, during, postRecovery, evaluation,
            List.of(new FaultEvent(faultId, experiment.faultType(),
                experiment.target(), faultStartedAt, faultEndedAt)),
            recoveryTime, Optional.empty()
        );
    }

    private static ExperimentResult abortedResult(
            final String experimentId,
            final String experimentName,
            final Instant startTime,
            final SteadyStateMetrics baseline,
            final List<SteadyStateMetrics> during,
            final FaultEvent faultEvent,
            final String abortReason) {

        final var emptyEval = new EvaluationResult(false, List.of(), baseline, during, SteadyStateMetrics.empty());
        return new ExperimentResult(
            experimentId, experimentName, startTime, Instant.now(),
            ExperimentStatus.ABORTED, baseline, during, SteadyStateMetrics.empty(),
            emptyEval, List.of(faultEvent), Duration.ZERO, Optional.of(abortReason)
        );
    }

    private static boolean isTerminal(final WorkflowPhase phase) {
        return phase == WorkflowPhase.SUCCEEDED
            || phase == WorkflowPhase.FAILED
            || phase == WorkflowPhase.ABORTED;
    }

    private static void sleep(final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public CompletionStage<Void> scheduleExperiment(
            final ExperimentDefinition experiment, final String cronExpression) {
        throw new UnsupportedOperationException("Scheduling is implemented in Plan 5");
    }

    @Override
    public CompletionStage<Void> emergencyStop(final String experimentId) {
        throw new UnsupportedOperationException("Emergency stop is implemented in Plan 5");
    }

    @Override
    public List<ExperimentResult> getExperimentHistory(final String targetService) {
        throw new UnsupportedOperationException("History is implemented in Plan 5");
    }
}
```

- [ ] **Step 6: Run tests — expect all pass**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.engine.ChaosEngineTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 7: Commit**

```bash
git add chaos/core/src/
git commit -m "feat(chaos): implement ChaosEngine with dual routing — Workflow path for infra faults, FaultInjector path for app faults (TDD)"
```

---

### Task 11: Implement `ExperimentReportGenerator` with JSON and Markdown output

**Files:**
- Modify: `chaos/core/src/main/java/com/agentengine/chaos/core/reporting/ExperimentReportGenerator.java`
- Create: `chaos/core/src/test/java/com/agentengine/chaos/core/reporting/ExperimentReportGeneratorTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.agentengine.chaos.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExperimentReportGeneratorTest {

    private ExperimentReportGenerator generator;
    private ExperimentResult sampleResult;

    @BeforeEach
    void setUp() {
        generator = new ExperimentReportGenerator();
        sampleResult = new ExperimentResult(
            "exp-001", "runtime-pod-kill",
            Instant.parse("2026-05-02T10:00:00Z"),
            Instant.parse("2026-05-02T10:05:00Z"),
            ExperimentStatus.PASSED,
            SteadyStateMetrics.empty(),
            List.of(SteadyStateMetrics.empty()),
            SteadyStateMetrics.empty(),
            new EvaluationResult(true, List.of(),
                SteadyStateMetrics.empty(), List.of(), SteadyStateMetrics.empty()),
            List.of(),
            Duration.ofSeconds(42),
            Optional.empty()
        );
    }

    @Test
    void shouldProduceJsonContainingExperimentId() {
        final String json = generator.toJson(sampleResult);
        assertThat(json).contains("\"experimentId\"").contains("exp-001");
    }

    @Test
    void shouldProduceJsonContainingStatus() {
        final String json = generator.toJson(sampleResult);
        assertThat(json).contains("PASSED");
    }

    @Test
    void shouldProduceMarkdownWithExperimentNameAsHeading() {
        final String md = generator.toMarkdown(sampleResult);
        assertThat(md).contains("# runtime-pod-kill");
    }

    @Test
    void shouldProduceMarkdownWithStatusAndRecoveryTime() {
        final String md = generator.toMarkdown(sampleResult);
        assertThat(md).contains("PASSED");
        assertThat(md).contains("42");
    }

    @Test
    void shouldProduceJsonListingCriterionTypeForFailedExperiment() {
        final var failure = new CriterionFailure(
            new SuccessCriterion(CriterionType.MAX_ERROR_RATE, 0.05, "error rate"), 0.15, 0.05
        );
        final var failedResult = new ExperimentResult(
            "exp-002", "network-chaos",
            Instant.now(), Instant.now(),
            ExperimentStatus.FAILED,
            SteadyStateMetrics.empty(), List.of(), SteadyStateMetrics.empty(),
            new EvaluationResult(false, List.of(failure),
                SteadyStateMetrics.empty(), List.of(), SteadyStateMetrics.empty()),
            List.of(), Duration.ofSeconds(90), Optional.empty()
        );

        final String json = generator.toJson(failedResult);
        assertThat(json).contains("FAILED").contains("MAX_ERROR_RATE");
    }
}
```

- [ ] **Step 2: Run tests — expect failures (stub returns `{}`)**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.reporting.ExperimentReportGeneratorTest"
```

Expected: Several FAIL — stub `toJson` returns `{}`, missing `experimentId`, `PASSED`.

- [ ] **Step 3: Replace `ExperimentReportGenerator.java` with the real implementation**

```java
package com.agentengine.chaos.core.reporting;

import com.agentengine.chaos.api.ExperimentResult;
import com.agentengine.chaos.api.SteadyStateMetrics;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class ExperimentReportGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new Jdk8Module())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public String toJson(final ExperimentResult result) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (final Exception e) {
            throw new ReportGenerationException("Failed to serialize experiment result to JSON", e);
        }
    }

    public String toMarkdown(final ExperimentResult result) {
        final var sb = new StringBuilder();
        sb.append("# ").append(result.experimentName()).append("\n\n");
        sb.append("**Experiment ID:** ").append(result.experimentId()).append("  \n");
        sb.append("**Status:** ").append(result.status()).append("  \n");
        sb.append("**Recovery Time:** ").append(result.recoveryTime().toSeconds()).append("s  \n");
        sb.append("**Start:** ").append(result.startTime()).append("  \n");
        sb.append("**End:** ").append(result.endTime()).append("\n\n");

        sb.append("## Success Criteria\n\n");
        if (result.evaluation().passed()) {
            sb.append("All criteria passed.\n\n");
        } else {
            sb.append("**Failed criteria:**\n\n");
            for (final var failure : result.evaluation().failures()) {
                sb.append("- **").append(failure.criterion().type()).append("**: ")
                  .append("actual=").append(failure.actualValue())
                  .append(" threshold=").append(failure.expectedThreshold())
                  .append(" — ").append(failure.criterion().description()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Baseline Metrics\n\n").append(formatMetrics(result.baseline()));
        sb.append("## Post-Recovery Metrics\n\n").append(formatMetrics(result.postRecovery()));
        return sb.toString();
    }

    private static String formatMetrics(final SteadyStateMetrics m) {
        return "| Metric | Value |\n|--------|-------|\n"
            + "| Success Rate | "    + String.format("%.1f%%", m.successRate() * 100) + " |\n"
            + "| Error Rate | "      + String.format("%.1f%%", m.errorRate() * 100) + " |\n"
            + "| P99 Latency | "     + m.p99Latency().toMillis() + "ms |\n"
            + "| Active Sessions | " + m.activeSessions() + " |\n\n";
    }
}
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./gradlew :chaos:core:test --tests "com.agentengine.chaos.core.reporting.ExperimentReportGeneratorTest"
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add chaos/core/src/
git commit -m "feat(chaos): implement ExperimentReportGenerator with JSON and Markdown output"
```

---

### Task 12: Final build verification

**Files:** None — verification only.

- [ ] **Step 1: Full clean build**

```bash
./gradlew :chaos:api:build :chaos:core:build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Verify test count**

```bash
./gradlew :chaos:core:test --info 2>&1 | grep -E "tests|PASS|FAIL"
```

Expected: All 28 tests pass across 5 test classes:
- `SuccessCriteriaEvaluatorTest`: 10 tests
- `BlastRadiusEnforcerTest`: 4 tests
- `SteadyStateAnalyzerTest`: 3 tests
- `ChaosEngineTest`: 6 tests (2 routing + 2 pass/fail + 1 blast radius + 1 result structure)
- `ExperimentReportGeneratorTest`: 5 tests

- [ ] **Step 3: Final commit**

```bash
git add .
git commit -m "feat(chaos): Plan 1 complete — chaos core module with dual-routing ChaosEngine (TDD)"
```

---

## What comes next

| Plan | Builds on this | Delivers |
|------|----------------|---------|
| **Plan 2: Infrastructure Injectors** | Implements `WorkflowSubmitter` via Fabric8 client | Real Chaos Mesh Workflow CRD submission for pod/network/resource faults |
| **Plan 3: Application Injectors** | Implements `FaultInjector` for Pekko, Toxiproxy, WireMock | Actor message faults, DB/LLM provider outages |
| **Plan 4: Validators** | Replaces `NoOpDataLossValidator` with PostgreSQL checks | Actual event journal integrity validation |
| **Plan 5: Operations** | Completes `ChaosService` (scheduling, emergency stop, history, REST) | Production-ready chaos system |

Plan 2's `ChaosMeshWorkflowSubmitter` implements `WorkflowSubmitter` from this plan.
Plan 3's injectors implement `FaultInjector`. Neither plan changes `ChaosEngine`.
