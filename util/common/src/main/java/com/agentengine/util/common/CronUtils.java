package com.agentengine.util.common;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class CronUtils {

    private static final CronParser PARSER =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
    private static final long MIN_JITTER_MILLIS = 500;

    private CronUtils() {}

    public static Optional<Instant> nextTriggerTime(final String cronExpression, final Instant after) {
        final Cron cron = parse(cronExpression);
        return ExecutionTime.forCron(cron)
                .nextExecution(ZonedDateTime.ofInstant(after, ZoneId.of("UTC")))
                .map(ZonedDateTime::toInstant);
    }

    /**
     * Spreads a fire time either side of its exact schedule, so that jobs sharing a schedule do not
     * all fire at the same instant. The offset is symmetric so the schedule is on time on average,
     * rather than running systematically late.
     *
     * <p>{@code jitterFraction} is the spread as a proportion of the remaining wait, which keeps it
     * sensible at any interval — 0.025 gives roughly ±90s on an hourly schedule and ±1.5s on a
     * minutely one, subject to a floor of {@value #MIN_JITTER_MILLIS}ms. Zero or less means no
     * jitter.
     *
     * <p>The wait is measured from whichever of {@code notBefore} and now is later. A {@code
     * notBefore} already in the past offers no usable room: spreading a trigger back into time that
     * has passed only makes it immediately due, so it earns no extra range. That same instant is the
     * floor the result is held above, so sizing and clamping cannot disagree.
     *
     * <p>The caller must keep the exact time as the anchor for the following computation: anchoring
     * on a jittered time that landed early lets the next lookup return the slot that just ran.
     */
    public static Instant applyJitter(final Instant exact, final Instant notBefore, final double jitterFraction) {
        if (jitterFraction <= 0) {
            return exact;
        }
        final long exactMillis = exact.toEpochMilli();
        final long floorMillis = Math.max(System.currentTimeMillis(), notBefore.toEpochMilli());
        final long range = Math.max((long) ((exactMillis - floorMillis) * jitterFraction), MIN_JITTER_MILLIS);
        final long jittered = exactMillis + ThreadLocalRandom.current().nextLong(-range, range + 1);
        return Instant.ofEpochMilli(Math.max(floorMillis + 1, jittered));
    }

    /** Validates an expression at the point a job is scheduled rather than when it first fires. */
    public static void validate(final String cronExpression) {
        parse(cronExpression);
    }

    private static Cron parse(final String cronExpression) {
        if (StringUtils.isBlank(cronExpression)) {
            throw new IllegalArgumentException("Cron expression is required");
        }
        try {
            final Cron cron = PARSER.parse(cronExpression);
            cron.validate();
            return cron;
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, exception);
        }
    }
}
