package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.CorrectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Single;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects corrective prompts for RunState violations into the next request.
 *
 * <p>Responsibilities:
 * - Convert {@link com.agentengine.engine.utils.Violation} instances into correction events.
 * - Append correction content to the request payload.
 * - Clear violations after they are emitted.
 *
 * <p>Ownership: RunState violation remediation and correction prompt emission.
 */
public final class CorrectionProcessor implements RequestProcessor {
  public static final CorrectionProcessor INSTANCE = new CorrectionProcessor();

  private static final Logger LOG = LoggerFactory.getLogger(CorrectionProcessor.class);

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final RunState runState = RunStateUtils.getState(context);
    final List<Violation> violations = runState.violations();

    if (CollectionUtils.isEmpty(violations)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    LOG.info("Gathered {} violation(s) for correction", violations.size());

    for (Violation v : violations) {
        LOG.info("Violation: code={} message={} correction={}", v.getCode(), v.getMessage(), v.getCorrectionMessage());
    }

    final List<Event> correctiveEvents = new ArrayList<>();
    final List<Content> contents = CollectionUtils.nullSafeMutableList(request.contents());

    for (final Violation violation : violations) {
      final String correctionMessage = violation.getCorrectionMessage();
      if (StringUtils.isBlank(correctionMessage)) {
        continue;
      }
      final Event correctiveEvent = CorrectionUtils.buildCorrectiveEvent(context, violation.getCode(), correctionMessage);
      correctiveEvent.content().ifPresent(contents::add);
      correctiveEvents.add(correctiveEvent);
    }

    runState.clearViolations();
    if (correctiveEvents.isEmpty()) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    final LlmRequest updatedRequest = request.toBuilder().contents(contents).build();

    return Single.just(RequestProcessingResult.create(updatedRequest, correctiveEvents));
  }
}
