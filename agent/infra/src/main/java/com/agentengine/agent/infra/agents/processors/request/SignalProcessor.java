package com.agentengine.agent.infra.agents.processors.request;

import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects queued signals — violations to correct, or plain status updates — into the request as
 * user-turn messages.
 *
 * <p>Must be a {@link RequestProcessor}, not a {@link
 * com.google.adk.flows.llmflows.ResponseProcessor}: ADK's own {@code
 * BaseLlmFlow.buildPostprocessingEvents} always places a response processor's events before the
 * model's own response event within the same step, which would interleave a signal into the middle
 * of the model's still-open (possibly still-streaming) response instead of after it. Running here,
 * at the start of the next request, guarantees a signal's event always lands strictly after the
 * response it concerns. The tradeoff — no next request exists if the step that raised the signal
 * ends the turn (PAUSED/TERMINAL) — is covered by {@code RunState.buildFrom} re-queuing any signal
 * that was raised but never actually delivered, read back from persisted event history rather than
 * relying on this processor having run.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li>All queued signals are converted into content and appended to the request.
 *   <li>After signals are processed, the queue is cleared for the next turn.
 *   <li>If no signals are present, the request passes through unchanged.
 *   <li>Signal content is added as user messages (role="user") to the request.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 *   <li>Session state must be initialized in {@code SessionUtils.initSessionState(context)}.
 *   <li>Signals are queued via {@code RunState.addSignal}, typically from a plugin callback
 *       reacting to the model's previous response.
 * </ul>
 */
public final class SignalProcessor implements RequestProcessor {
  public static final SignalProcessor INSTANCE = new SignalProcessor();

  private static final Logger LOG = LoggerFactory.getLogger(SignalProcessor.class);

  private SignalProcessor() {}

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final RunState runState = RunUtils.getRunState(context);
    final List<Event> signalEvents = runState.deliverSignals(context);
    if (CollectionUtils.isEmpty(signalEvents)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }
    LOG.info("Delivering {} signal event(s)", signalEvents.size());

    final List<Content> contents = CollectionUtils.nullSafeMutableList(request.contents());
    for (final Event signalEvent : signalEvents) {
      signalEvent.content().ifPresent(contents::add);
    }
    final LlmRequest updatedRequest = request.toBuilder().contents(contents).build();
    return Single.just(RequestProcessingResult.create(updatedRequest, signalEvents));
  }
}
