package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs request/session details for diagnostics.
 *
 * <p>Responsibilities:
 * - Emit debug logs of session events and request payloads.
 * - Do not mutate requests or session state.
 *
 * <p>Ownership: request diagnostics and observability.
 */
public final class LoggingRequestProcessor implements RequestProcessor {
  public static final LoggingRequestProcessor INSTANCE = new LoggingRequestProcessor();
  private static final Logger LOG = LoggerFactory.getLogger(LoggingRequestProcessor.class);

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final Session session = context == null ? null : context.session();
    final List<Event> events =
            session == null ? List.of() : CollectionUtils.nullSafeList(session.events());
            
    final boolean fullContentLogging = ConfigProvider.getConfig()
        .getOptionalValue("agentengine.logging.full-content", Boolean.class).orElse(false);
        
    if (fullContentLogging) {
      LOG.debug("Session events - events={} request={}", JsonUtils.toJson(events), JsonUtils.toJson(request));
    } else {
      final List<String> eventIds = events.stream().map(Event::id).toList();
      LOG.debug("Session events - event_count={} event_ids={}", events.size(), eventIds);
    }
    return Single.just(RequestProcessingResult.create(request, List.of()));
  }
}
