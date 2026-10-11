package com.agentengine.agent.infra.session;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.util.agents.ContentUtils;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * A session service that tells the model when each user message was sent, in the zone of the user
 * who sent it, by leading the message's content with a {@code <current_time>} section as the
 * message is appended, and that sets the message apart between {@code <user_message>} tags.
 *
 * <p>The sections are made once, and the session the model reads holds them, so a request's start
 * stays the same from one request to the next. The messages stored by the session actor are not
 * touched; replaying them through this service gives the same session again. A message whose zone
 * differs from the one stamped before it says so; that zone is kept, and the session's events are
 * only searched for it until one is.
 */
public final class UserContextSessionService extends DelegatingSessionService {

  private static final DateTimeFormatter SENT_AT_FORMAT =
      DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm:ss", Locale.ENGLISH);

  private volatile ZoneId lastZone;

  public UserContextSessionService(final BaseSessionService delegate) {
    super(delegate);
  }

  @Override
  public Single<Event> appendEvent(final Session session, final Event event) {
    final ZonedDateTime sentAt = EventUtils.sentAtOf(event);
    if (sentAt == null || event.content().isEmpty()) {
      return super.appendEvent(session, event);
    }
    final ZoneId previousZone = lastZone != null ? lastZone : zoneOfLastStamped(session.events());
    lastZone = sentAt.getZone();
    final String currentTime = currentTimeText(sentAt, previousZone);
    return super.appendEvent(
        session, event.toBuilder().content(ContentUtils.withUserContext(event.content().get(), currentTime)).build());
  }

  private static ZoneId zoneOfLastStamped(final List<Event> events) {
    for (final Event event : events.reversed()) {
      final ZonedDateTime sentAt = EventUtils.sentAtOf(event);
      if (sentAt != null) {
        return sentAt.getZone();
      }
    }
    return null;
  }

  /**
   * What the model is told of when a message was sent, e.g. {@code Current date and time: Saturday,
   * 10 October 2026, 20:02:11 (Asia/Kolkata, UTC+05:30)}, followed by a notice when {@code
   * previousZone}, the zone of the message stamped before it, is a different zone.
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
