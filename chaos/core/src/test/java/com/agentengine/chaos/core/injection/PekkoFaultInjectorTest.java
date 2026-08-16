package com.agentengine.chaos.core.injection;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.BlastRadiusScope;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.MessageDelayParameters;
import com.agentengine.chaos.api.fault.MessageDropParameters;
import com.agentengine.util.pekko.actor.ChaosMailboxRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class PekkoFaultInjectorTest {

  private final ChaosMailboxRegistry registry = new ChaosMailboxRegistry();
  private final PekkoFaultInjector injector = new PekkoFaultInjector(registry);
  private final BlastRadius blastRadius = new BlastRadius(BlastRadiusScope.SINGLE_POD, 1, 100.0);

  @Test
  void shouldSupportOnlyMessageFaultTypes() {
    assertThat(injector.supports(FaultType.MESSAGE_DELAY)).isTrue();
    assertThat(injector.supports(FaultType.MESSAGE_DROP)).isTrue();
    assertThat(injector.supports(FaultType.POD_KILL)).isFalse();
  }

  @Test
  void shouldRegisterDelayConfigForTargetedEntity()
      throws ExecutionException, InterruptedException {
    final TargetSelector target =
        new TargetSelector("agent-engine", "agent", Map.of(), Optional.of("session-42"));

    final String faultId =
        injector
            .injectFault(
                FaultType.MESSAGE_DELAY,
                target,
                new MessageDelayParameters(Duration.ofSeconds(2), 0.5),
                blastRadius)
            .toCompletableFuture()
            .get();

    assertThat(faultId).isEqualTo("session-42");
    assertThat(registry.configFor("session-42")).isPresent();
    assertThat(registry.configFor("session-42").get().delayDuration())
        .contains(Duration.ofSeconds(2));
  }

  @Test
  void shouldRegisterDropConfigForWildcardWhenNoEntityIdSpecified()
      throws ExecutionException, InterruptedException {
    final TargetSelector target =
        new TargetSelector("agent-engine", "agent", Map.of(), Optional.empty());

    final String faultId =
        injector
            .injectFault(
                FaultType.MESSAGE_DROP, target, new MessageDropParameters(0.25), blastRadius)
            .toCompletableFuture()
            .get();

    assertThat(faultId).isEqualTo(ChaosMailboxRegistry.wildcardEntityId());
    assertThat(registry.configFor("any-session")).isPresent();
    assertThat(registry.configFor("any-session").get().dropPercentage()).isEqualTo(0.25);
  }

  @Test
  void shouldRemoveFaultFromRegistry() throws ExecutionException, InterruptedException {
    final TargetSelector target =
        new TargetSelector("agent-engine", "agent", Map.of(), Optional.of("session-1"));
    final String faultId =
        injector
            .injectFault(
                FaultType.MESSAGE_DROP, target, new MessageDropParameters(1.0), blastRadius)
            .toCompletableFuture()
            .get();

    injector.removeFault(faultId).toCompletableFuture().get();

    assertThat(registry.configFor("session-1")).isEmpty();
  }
}
