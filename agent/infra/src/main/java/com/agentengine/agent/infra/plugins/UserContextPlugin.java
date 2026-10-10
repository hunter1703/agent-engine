package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.util.agents.ContentUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tells the model when each user message was sent, in the zone of the user who sent it, by leading
 * the message with a {@code <current_time>} section.
 *
 * <p>Every message that was stamped when it was sent gets its own section, not only the latest, so
 * the model reads when each one was sent. The section is made from what was kept with the message's
 * event, which never changes, so the request's start stays the same from one request to the next.
 * A message whose zone differs from the one stamped before it says so.
 *
 * <p>The section is only added in front of a message's own content, so it works on a message
 * another plugin has already led or wrapped.
 */

//TODO: optimize. Since inmemory session service is used to actually drive the session for a given run, why cant we calculate the prefixes once. In the persisted store, driven by session actor, it will have actual non-prefixed events
// By doing this, we need to only prefix new message as the in-memory session service will have the previous messages already prefixed. This will save a lot of time in the beforeModelCallback method.
public final class UserContextPlugin extends BasePlugin {

  private static final DateTimeFormatter SENT_AT_FORMAT =
      DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm:ss", Locale.ENGLISH);

  public UserContextPlugin() {
    super("user_context_plugin");
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {
    final List<Event> events = callbackContext.invocationContext().session().events();
    final List<Content> contents = new ArrayList<>(llmRequestBuilder.build().contents());

    int from = 0;
    ZoneId previousZone = null;
    for (final Event event : events) {
      final ZonedDateTime sentAt = EventUtils.sentAtOf(event);
      if (sentAt == null) {
        continue;
      }
      final String currentTime = currentTimeText(sentAt, previousZone);
      previousZone = sentAt.getZone();
      if (event.content().isEmpty()) {
        continue;
      }
      // A message compacted away is not in the request; the zone it was sent in still tells the
      // next one whether the zone changed.
      final int index = ContentUtils.indexOfMessage(contents, event.content().get(), from);
      if (index >= 0) {
        contents.set(index, withTimePrefix(contents.get(index), currentTime));
        from = index + 1;
      }
    }
    llmRequestBuilder.contents(contents);
    return Maybe.empty();
  }

  private static Content withTimePrefix(final Content content, final String currentTime) {
    final List<Part> parts = new ArrayList<>();
    parts.add(Part.fromText("<current_time>\n" + currentTime + "\n</current_time>\n\n"));
    parts.addAll(content.parts().orElse(List.of()));
    return content.toBuilder().parts(parts).build();
  }

  /**
   * What the model is told of when a message was sent, e.g. {@code Current date and time: Saturday,
   * 10 October 2026, 20:02:11 (Asia/Kolkata, UTC+05:30)}, followed by a notice when {@code
   * previousZone}, the zone of the message before it, is a different zone.
   */
  private static String currentTimeText(final ZonedDateTime sentAt, final ZoneId previousZone) {
    final ZoneId zone = sentAt.getZone();
    final StringBuilder text =
        new StringBuilder("Current date and time: ")
            .append(SENT_AT_FORMAT.format(sentAt))
            .append(" (")
            .append(zoneName(zone, sentAt.toInstant()))
            .append(")");
    if (previousZone != null && !previousZone.equals(zone)) {
      text.append(". The user's time zone is now ")
          .append(zone.getId())
          .append(" (it was ")
          .append(previousZone.getId())
          .append(")");
    }
    return text.toString();
  }

  private static String zoneName(final ZoneId zone, final Instant time) {
    String utcOffset = zone.getRules().getOffset(time).getId(); // Returns "+05:30" or "Z"
    if ("Z".equals(utcOffset)) {
      utcOffset = "+00:00";
    }
    return zone.getId().equals("UTC") ? "UTC" + utcOffset : zone.getId() + ", UTC" + utcOffset;
  }
}
