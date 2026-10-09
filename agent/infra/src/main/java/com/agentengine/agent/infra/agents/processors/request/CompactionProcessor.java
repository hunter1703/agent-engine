package com.agentengine.agent.infra.agents.processors.request;

import com.agentengine.agent.infra.session.DelegatingSessionService;
import com.agentengine.agent.infra.utils.EventUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.adk.summarizer.EventCompactor;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the agent's {@link EventCompactor} before each model request and returns the compaction
 * event it produces, so the event is stored like any other and is part of the session before ADK's
 * {@code Contents} processor, which runs after this one, replaces the events it covers with its
 * content.
 *
 * <p>A compaction never ends between a call and its result. A compaction that fails is skipped:
 * the request goes out uncompacted and the next one tries again.
 */
public final class CompactionProcessor implements RequestProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(CompactionProcessor.class);

  private final EventCompactor compactor;

  public CompactionProcessor(final EventCompactor compactor) {
    this.compactor = compactor;
  }

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    // The compactor's own append is held back, so the event is added the way every event of the
    // run is: by returning it.
    final List<Event> compactionEvents = new ArrayList<>();
    final BaseSessionService collector =
        new DelegatingSessionService(context.sessionService()) {
          @Override
          public Single<Event> appendEvent(final Session session, final Event event) {
            compactionEvents.add(event);
            return Single.just(event);
          }
        };
    return compactor
        .compact(context.session(), collector)
        .doOnError(
            error -> LOG.warn("Compaction failed for session {}", context.session().id(), error))
        .onErrorComplete()
        .andThen(
            Single.fromCallable(
                () ->
                    RequestProcessingResult.create(
                        request, compactionEventsToStore(context, compactionEvents))));
  }

  private static List<Event> compactionEventsToStore(
      final InvocationContext context, final List<Event> compactionEvents) {
    return compactionEvents.stream()
        .map(event -> EventUtils.keepCallsWithResults(event, context.session().events()))
        .filter(Objects::nonNull)
        .peek(EventUtils::markAsInternal)
        .toList();
  }
}
