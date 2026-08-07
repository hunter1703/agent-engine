package com.agentengine.util.pekko.actor;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Configures message-level fault injection for a single sharded entity's mailbox. Used by {@link
 * MessageFaultInterceptor} to decide, per incoming message, whether to drop it, delay it, or pass
 * it straight through.
 */
public record ChaosMailboxConfig(
        double dropPercentage, Optional<Duration> delayDuration, double delayPercentage, boolean active) {

    public boolean shouldDrop() {
        return active && dropPercentage > 0 && ThreadLocalRandom.current().nextDouble() < dropPercentage;
    }

    public boolean shouldDelay() {
        return active
                && delayDuration.isPresent()
                && delayPercentage > 0
                && ThreadLocalRandom.current().nextDouble() < delayPercentage;
    }
}
