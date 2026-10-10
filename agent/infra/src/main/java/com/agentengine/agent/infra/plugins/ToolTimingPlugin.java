package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.Utils;
import com.google.adk.events.Event;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Maybe;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Tells the model when each tool call completed and how long it took, so it knows time has passed
 * between its steps.
 *
 * <p>A result gains {@code completedAt}, the time it was produced in the zone the user's message
 * was stamped in, {@code timeZone}, the id of that zone, so every result names its zone however far
 * back in the history it is, and {@code timeTaken}, the time from the model's event making the
 * call to the result, pauses included: a call that waited for a child session or a user answer
 * reports the whole wait. A result that already has a field of one of these names keeps it, and an
 * empty result stays empty, since an empty result is how a tool that raises an interrupt answers.
 */
public final class ToolTimingPlugin extends BasePlugin {
  public static final String COMPLETED_AT = "completedAt";
  public static final String TIME_ZONE = "timeZone";
  public static final String TIME_TAKEN = "timeTaken";

  public ToolTimingPlugin() {
    super("tool_timing_plugin");
  }

  @Override
  public Maybe<Map<String, Object>> afterToolCallback(
      final BaseTool tool,
      final Map<String, Object> args,
      final ToolContext toolContext,
      final Map<String, Object> result) {
    if (result == null || result.isEmpty()) {
      return Maybe.empty();
    }
    final Instant completedAt = Instant.now();
    final List<Event> events = toolContext.invocationContext().session().events();
    final Instant calledAt =
        toolContext
            .functionCallId()
            .flatMap(callId -> callEvent(events, callId))
            .map(event -> Instant.ofEpochMilli(event.timestamp()))
            .orElse(completedAt);
    final ZoneId zone =
        Utils.zoneOf(
            CollectionUtils.getStringValueFromMap(toolContext.state(), EventUtils.TIME_ZONE_KEY));

    final Map<String, Object> timed = new LinkedHashMap<>(result);
    timed.putIfAbsent(
        COMPLETED_AT,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            completedAt.truncatedTo(ChronoUnit.SECONDS).atZone(zone)));
    timed.putIfAbsent(TIME_ZONE, zone.getId());
    timed.putIfAbsent(TIME_TAKEN, timeTaken(Duration.between(calledAt, completedAt)));
    return Maybe.just(timed);
  }

  /** {@code duration} as seconds below a minute, e.g. {@code 4.2s}, else as {@code 1m 37s}. */
  private static String timeTaken(final Duration duration) {
    final long millis = Math.max(0, duration.toMillis());
    if (millis < 60_000) {
      return String.format(Locale.ENGLISH, "%.1fs", millis / 100 / 10.0);
    }
    final long totalSeconds = millis / 1000;
    final long hours = totalSeconds / 3600;
    final long minutes = totalSeconds % 3600 / 60;
    final long seconds = totalSeconds % 60;
    return hours > 0
        ? "%dh %dm %ds".formatted(hours, minutes, seconds)
        : "%dm %ds".formatted(minutes, seconds);
  }

  private static Optional<Event> callEvent(final List<Event> events, final String callId) {
    return events.reversed().stream()
        .filter(
            event ->
                event.functionCalls().stream()
                    .anyMatch(call -> call.id().filter(callId::equals).isPresent()))
        .findFirst();
  }
}
