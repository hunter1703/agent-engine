package com.agentengine.scheduler.core;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.util.common.CronUtils;
import com.agentengine.util.common.StringUtils;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SchedulerUtils {

    /**
     * Spread applied to a fire time, as a proportion of the wait until it. Roughly ±90s on an hourly
     * schedule, ±1.5s on a minutely one — enough to break up jobs sharing a schedule without moving
     * any of them noticeably.
     */
    public static final double JITTER_FRACTION = 0.025d;

    private SchedulerUtils() {}

    /**
     * Capacity buckets a job draws from: its declared tags plus its class name, which is always a tag
     * in its own right. That implicit tag gives every job type a bucket that can be capped without
     * anyone having to declare one, and leaves a job with no tags at all still individually
     * limitable.
     *
     * <p>Deduplicated and order-preserving, so declaring the class name explicitly is harmless.
     */
    public static List<String> getJobTags(final JobDefinition jobDefinition) {
        if (jobDefinition == null) {
            return List.of();
        }
        final Set<String> tags = new LinkedHashSet<>();
        for (final String tag : jobDefinition.getJobTags() == null ? List.<String>of() : jobDefinition.getJobTags()) {
            if (StringUtils.isNotBlank(tag)) {
                tags.add(tag);
            }
        }
        if (StringUtils.isNotBlank(jobDefinition.getJobClassName())) {
            tags.add(jobDefinition.getJobClassName());
        }
        return List.copyOf(tags);
    }

    /**
     * The exact cron occurrence following the trigger's current one, or empty when its job has no
     * schedule or the schedule is exhausted.
     *
     * <p>Anchors on {@link TriggerDefinition#getScheduledFor()} — the exact occurrence — rather
     * than on {@code dueAt}, the jittered time the run actually fired at. A run that jitter
     * moved early would otherwise resolve back to its own occurrence and fire twice. Taking the later
     * of that and now also keeps a schedule moving forward when a run overran its own interval,
     * collapsing the missed occurrences into one. A trigger not yet scheduled has an occurrence of
     * zero and so resolves from now.
     */
    public static Optional<Instant> nextScheduledTime(final TriggerDefinition trigger) {
        final JobDefinition jobDefinition = trigger == null ? null : trigger.getJobDefinition();
        if (jobDefinition == null || StringUtils.isBlank(jobDefinition.getCronSchedule())) {
            return Optional.empty();
        }
        final Instant after = Instant.ofEpochMilli(Math.max(trigger.getScheduledFor() + 1, System.currentTimeMillis()));
        return CronUtils.nextTriggerTime(jobDefinition.getCronSchedule(), after);
    }
}
