package com.agentengine.agent.infra.agents.processors.request;

import com.agentengine.agent.infra.compaction.EventCompactor;
import com.agentengine.agent.infra.utils.EventUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the agent's {@link EventCompactor} before each model request and returns the compaction
 * event it produces, so the event is stored like any other and is part of the session before ADK's
 * {@code Contents} processor, which runs after this one, replaces the events it covers with its
 * content. The event is not shown to the user.
 *
 * <p>A compaction that fails is skipped: the request goes out uncompacted and the next one tries
 * again.
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
    return compactor
        .compact(context, request)
        .doOnError(
            error -> LOG.warn("Compaction failed for session {}", context.session().id(), error))
        .onErrorComplete()
        .map(
            compaction -> {
              EventUtils.markAsInternal(compaction);
              return List.of(compaction);
            })
        .defaultIfEmpty(List.of())
        .map(events -> RequestProcessingResult.create(request, events));
  }
}
