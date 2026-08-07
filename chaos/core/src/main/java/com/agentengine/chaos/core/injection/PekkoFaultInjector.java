package com.agentengine.chaos.core.injection;

import com.agentengine.chaos.api.BlastRadius;
import com.agentengine.chaos.api.FaultType;
import com.agentengine.chaos.api.TargetSelector;
import com.agentengine.chaos.api.fault.FaultParameters;
import com.agentengine.chaos.api.fault.MessageDelayParameters;
import com.agentengine.chaos.api.fault.MessageDropParameters;
import com.agentengine.util.pekko.actor.ChaosMailboxConfig;
import com.agentengine.util.pekko.actor.ChaosMailboxRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Injects {@link FaultType#MESSAGE_DELAY} / {@link FaultType#MESSAGE_DROP} faults by writing a
 * {@link ChaosMailboxConfig} into the shared {@link ChaosMailboxRegistry} — the same registry
 * {@code MessageFaultInterceptor} consults on every message a {@code SessionActor} receives. No
 * actor restart is required to activate or clear a fault.
 */
public final class PekkoFaultInjector implements FaultInjector {

    private final ChaosMailboxRegistry registry;

    public PekkoFaultInjector(final ChaosMailboxRegistry registry) {
        this.registry = registry;
    }

    @Override
    public CompletionStage<String> injectFault(
            final FaultType faultType,
            final TargetSelector target,
            final FaultParameters parameters,
            final BlastRadius blastRadius) {
        final String entityId = target.entityId().orElse(ChaosMailboxRegistry.wildcardEntityId());
        final ChaosMailboxConfig config = toMailboxConfig(faultType, parameters);
        registry.register(entityId, config);
        return CompletableFuture.completedFuture(entityId);
    }

    @Override
    public CompletionStage<Void> removeFault(final String faultId) {
        registry.remove(faultId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean supports(final FaultType faultType) {
        return faultType == FaultType.MESSAGE_DELAY || faultType == FaultType.MESSAGE_DROP;
    }

    private static ChaosMailboxConfig toMailboxConfig(final FaultType faultType, final FaultParameters parameters) {
        return switch (faultType) {
            case MESSAGE_DELAY -> {
                final MessageDelayParameters delayParameters = (MessageDelayParameters) parameters;
                yield new ChaosMailboxConfig(
                        0.0, Optional.of(delayParameters.delay()), delayParameters.percentage(), true);
            }
            case MESSAGE_DROP -> {
                final MessageDropParameters dropParameters = (MessageDropParameters) parameters;
                yield new ChaosMailboxConfig(dropParameters.dropPercentage(), Optional.empty(), 0.0, true);
            }
            default -> throw new IllegalArgumentException("PekkoFaultInjector does not support " + faultType);
        };
    }
}
