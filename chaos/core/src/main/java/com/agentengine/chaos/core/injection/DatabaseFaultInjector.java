package com.agentengine.chaos.core.injection;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.DatabaseFailureParameters;
import com.agentengine.chaos.api.fault.FaultParameters;
import com.agentengine.chaos.api.fault.QdrantFailureParameters;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Injects {@link FaultType#DATABASE_FAILURE}, {@link FaultType#EVENT_JOURNAL_FAILURE}, {@link
 * FaultType#SNAPSHOT_STORE_FAILURE}, and {@link FaultType#QDRANT_FAILURE} faults as Toxiproxy
 * toxics on the {@code postgresql}, {@code mongodb}, and {@code qdrant} proxies. Each injected
 * toxic is named uniquely so {@link #removeFault} can locate and delete it without the caller
 * needing to remember which proxy it lives on.
 */
public final class DatabaseFaultInjector implements FaultInjector {

  private static final long FULL_BLOCK_TIMEOUT_MILLIS = 0L;
  private static final long SNAPSHOT_STORE_LATENCY_MILLIS = Duration.ofSeconds(3).toMillis();

  private final Map<String, Proxy> proxiesByName;
  private final Map<String, Proxy> proxiesByFaultId = new ConcurrentHashMap<>();

  public DatabaseFaultInjector(final Map<String, Proxy> proxiesByName) {
    this.proxiesByName = Map.copyOf(proxiesByName);
  }

  @Override
  public CompletionStage<String> injectFault(
      final FaultType faultType,
      final TargetSelector target,
      final FaultParameters parameters,
      final BlastRadius blastRadius) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return switch (faultType) {
              case DATABASE_FAILURE ->
                  injectFullBlock(
                      faultType,
                      proxyFor(
                          ((DatabaseFailureParameters) parameters).target().name().toLowerCase()));
              case EVENT_JOURNAL_FAILURE -> injectFullBlock(faultType, proxyFor("postgresql"));
              case SNAPSHOT_STORE_FAILURE ->
                  injectLatency(faultType, proxyFor("postgresql"), SNAPSHOT_STORE_LATENCY_MILLIS);
              case QDRANT_FAILURE ->
                  injectQdrantFault(faultType, (QdrantFailureParameters) parameters);
              default ->
                  throw new IllegalArgumentException(
                      "DatabaseFaultInjector does not support " + faultType);
            };
          } catch (final IOException ex) {
            throw new FaultInjectionException("Failed to inject " + faultType, ex);
          }
        });
  }

  @Override
  public CompletionStage<Void> removeFault(final String faultId) {
    return CompletableFuture.runAsync(
        () -> {
          final Proxy proxy = proxiesByFaultId.remove(faultId);
          if (proxy == null) {
            return;
          }
          try {
            proxy.toxics().get(faultId).remove();
          } catch (final IOException ex) {
            throw new FaultInjectionException("Failed to remove fault " + faultId, ex);
          }
        });
  }

  @Override
  public boolean supports(final FaultType faultType) {
    return switch (faultType) {
      case DATABASE_FAILURE, EVENT_JOURNAL_FAILURE, SNAPSHOT_STORE_FAILURE, QDRANT_FAILURE -> true;
      default -> false;
    };
  }

  private String injectQdrantFault(
      final FaultType faultType, final QdrantFailureParameters parameters) throws IOException {
    final Proxy qdrantProxy = proxyFor("qdrant");
    return parameters
        .latency()
        .map(latency -> injectLatency(faultType, qdrantProxy, latency.toMillis()))
        .orElseGet(() -> injectFullBlock(faultType, qdrantProxy));
  }

  private String injectFullBlock(final FaultType faultType, final Proxy proxy) {
    return addToxic(faultType, proxy, "timeout", FULL_BLOCK_TIMEOUT_MILLIS);
  }

  private String injectLatency(
      final FaultType faultType, final Proxy proxy, final long latencyMillis) {
    return addToxic(faultType, proxy, "latency", latencyMillis);
  }

  private String addToxic(
      final FaultType faultType, final Proxy proxy, final String toxicKind, final long millis) {
    final String faultId = "chaos-" + faultType + "-" + UUID.randomUUID();
    try {
      if ("timeout".equals(toxicKind)) {
        proxy.toxics().timeout(faultId, ToxicDirection.UPSTREAM, millis);
      } else {
        proxy.toxics().latency(faultId, ToxicDirection.DOWNSTREAM, millis);
      }
    } catch (final IOException ex) {
      throw new FaultInjectionException(
          "Failed to add " + toxicKind + " toxic for " + faultType, ex);
    }
    proxiesByFaultId.put(faultId, proxy);
    return faultId;
  }

  private Proxy proxyFor(final String name) {
    final Proxy proxy = proxiesByName.get(name);
    if (proxy == null) {
      throw new IllegalStateException("No managed Toxiproxy proxy named '" + name + "'");
    }
    return proxy;
  }

  /**
   * Wraps checked Toxiproxy client failures so they can propagate through a {@link
   * CompletionStage}.
   */
  public static final class FaultInjectionException extends RuntimeException {
    public FaultInjectionException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
