package com.agentengine.chaos.core.injection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.DatabaseFailureParameters;
import com.agentengine.chaos.api.fault.DatabaseTarget;
import com.agentengine.chaos.api.fault.QdrantFailureParameters;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.ToxicList;
import eu.rekawek.toxiproxy.model.toxic.Latency;
import eu.rekawek.toxiproxy.model.toxic.Timeout;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseFaultInjectorTest {

  private static final TargetSelector TARGET =
      new TargetSelector("agent-engine", "runtime", Map.of(), Optional.empty());
  private static final BlastRadius BLAST_RADIUS =
      new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0);

  private Proxy postgresProxy;
  private Proxy mongoProxy;
  private Proxy qdrantProxy;
  private ToxicList postgresToxics;
  private ToxicList mongoToxics;
  private ToxicList qdrantToxics;
  private DatabaseFaultInjector injector;

  @BeforeEach
  void setUp() {
    postgresProxy = mock(Proxy.class);
    mongoProxy = mock(Proxy.class);
    qdrantProxy = mock(Proxy.class);
    postgresToxics = mock(ToxicList.class);
    mongoToxics = mock(ToxicList.class);
    qdrantToxics = mock(ToxicList.class);
    when(postgresProxy.toxics()).thenReturn(postgresToxics);
    when(mongoProxy.toxics()).thenReturn(mongoToxics);
    when(qdrantProxy.toxics()).thenReturn(qdrantToxics);

    injector =
        new DatabaseFaultInjector(
            Map.of("postgresql", postgresProxy, "mongodb", mongoProxy, "qdrant", qdrantProxy));
  }

  @Test
  void shouldSupportOnlyDatabaseFaultTypes() {
    assertThat(injector.supports(FaultType.DATABASE_FAILURE)).isTrue();
    assertThat(injector.supports(FaultType.EVENT_JOURNAL_FAILURE)).isTrue();
    assertThat(injector.supports(FaultType.SNAPSHOT_STORE_FAILURE)).isTrue();
    assertThat(injector.supports(FaultType.QDRANT_FAILURE)).isTrue();
    assertThat(injector.supports(FaultType.POD_KILL)).isFalse();
  }

  @Test
  void shouldAddFullBlockTimeoutToxicOnPostgresqlForDatabaseFailure()
      throws ExecutionException, InterruptedException, IOException {
    final String faultId =
        injector
            .injectFault(
                FaultType.DATABASE_FAILURE,
                TARGET,
                new DatabaseFailureParameters(DatabaseTarget.POSTGRESQL),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    assertThat(faultId).startsWith("chaos-DATABASE_FAILURE-");
    verify(postgresToxics).timeout(faultId, ToxicDirection.UPSTREAM, 0L);
  }

  @Test
  void shouldAddFullBlockTimeoutToxicOnMongodbForDatabaseFailure()
      throws ExecutionException, InterruptedException, IOException {
    final String faultId =
        injector
            .injectFault(
                FaultType.DATABASE_FAILURE,
                TARGET,
                new DatabaseFailureParameters(DatabaseTarget.MONGODB),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    verify(mongoToxics).timeout(faultId, ToxicDirection.UPSTREAM, 0L);
  }

  @Test
  void shouldAddTimeoutToxicOnPostgresqlForEventJournalFailure()
      throws ExecutionException, InterruptedException, IOException {
    final String faultId =
        injector
            .injectFault(
                FaultType.EVENT_JOURNAL_FAILURE,
                TARGET,
                new DatabaseFailureParameters(DatabaseTarget.UNKNOWN),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    verify(postgresToxics).timeout(faultId, ToxicDirection.UPSTREAM, 0L);
  }

  @Test
  void shouldAddLatencyToxicOnPostgresqlForSnapshotStoreFailure()
      throws ExecutionException, InterruptedException, IOException {
    final String faultId =
        injector
            .injectFault(
                FaultType.SNAPSHOT_STORE_FAILURE,
                TARGET,
                new DatabaseFailureParameters(DatabaseTarget.UNKNOWN),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    verify(postgresToxics)
        .latency(faultId, ToxicDirection.DOWNSTREAM, Duration.ofSeconds(3).toMillis());
  }

  @Test
  void shouldAddFullBlockTimeoutToxicOnQdrantWhenLatencyAbsent()
      throws ExecutionException, InterruptedException, IOException {
    final String faultId =
        injector
            .injectFault(
                FaultType.QDRANT_FAILURE,
                TARGET,
                new QdrantFailureParameters(Optional.empty()),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    assertThat(faultId).startsWith("chaos-QDRANT_FAILURE-");
    verify(qdrantToxics).timeout(faultId, ToxicDirection.UPSTREAM, 0L);
  }

  @Test
  void shouldAddLatencyToxicOnQdrantWhenLatencyPresent()
      throws ExecutionException, InterruptedException, IOException {
    final String faultId =
        injector
            .injectFault(
                FaultType.QDRANT_FAILURE,
                TARGET,
                new QdrantFailureParameters(Optional.of(Duration.ofMillis(750))),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    verify(qdrantToxics).latency(faultId, ToxicDirection.DOWNSTREAM, 750L);
  }

  @Test
  void shouldRemoveToxicFromTheProxyItWasAddedTo()
      throws ExecutionException, InterruptedException, IOException {
    final String faultId =
        injector
            .injectFault(
                FaultType.QDRANT_FAILURE,
                TARGET,
                new QdrantFailureParameters(Optional.empty()),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    final Timeout toxic = mock(Timeout.class);
    when(qdrantToxics.get(faultId)).thenReturn(toxic);

    injector.removeFault(faultId).toCompletableFuture().get();

    verify(toxic).remove();
  }

  @Test
  void shouldNotThrowWhenRemovingUnknownFaultId()
      throws ExecutionException, InterruptedException, IOException {
    injector.removeFault("never-injected").toCompletableFuture().get();
    // No exception, and no proxy interaction should have occurred for an untracked ID.
  }

  @Test
  void shouldTrackDistinctToxicsPerFaultIndependently()
      throws ExecutionException, InterruptedException, IOException {
    final String firstFaultId =
        injector
            .injectFault(
                FaultType.QDRANT_FAILURE,
                TARGET,
                new QdrantFailureParameters(Optional.of(Duration.ofSeconds(1))),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();
    final String secondFaultId =
        injector
            .injectFault(
                FaultType.SNAPSHOT_STORE_FAILURE,
                TARGET,
                new DatabaseFailureParameters(DatabaseTarget.UNKNOWN),
                BLAST_RADIUS)
            .toCompletableFuture()
            .get();

    assertThat(firstFaultId).isNotEqualTo(secondFaultId);

    final Latency firstToxic = mock(Latency.class);
    final Latency secondToxic = mock(Latency.class);
    when(qdrantToxics.get(firstFaultId)).thenReturn(firstToxic);
    when(postgresToxics.get(secondFaultId)).thenReturn(secondToxic);

    injector.removeFault(firstFaultId).toCompletableFuture().get();
    injector.removeFault(secondFaultId).toCompletableFuture().get();

    verify(firstToxic).remove();
    verify(secondToxic).remove();
  }

  @Test
  void shouldThrowIllegalArgumentForUnsupportedFaultType() {
    final var future =
        injector
            .injectFault(
                FaultType.POD_KILL,
                TARGET,
                new DatabaseFailureParameters(DatabaseTarget.POSTGRESQL),
                BLAST_RADIUS)
            .toCompletableFuture();

    assertThat(future)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(IllegalArgumentException.class);
  }
}
