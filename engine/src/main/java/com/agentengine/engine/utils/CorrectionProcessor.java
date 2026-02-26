package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * correction processor that gathers all violations from context
 * and injects a consolidated nudge prompt into the LLM request.
 */
public final class CorrectionProcessor implements RequestProcessor {
  public static final CorrectionProcessor INSTANCE = new CorrectionProcessor();

  private static final Logger LOG = LoggerFactory.getLogger(CorrectionProcessor.class);

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final List<Violation> violations = ViolationUtils.getViolations(context);

    if (CollectionUtils.isEmpty(violations)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    LOG.info("Gathered {} violation(s) for correction", violations.size());

    for (Violation v : violations) {
        LOG.info("Violation: code={} message={} correction={}", v.getCode(), v.getMessage(), v.getCorrectionMessage());
    }

    final List<String> messages =
        violations.stream()
            .map(Violation::getCorrectionMessage)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());

    ViolationUtils.clearViolations(context);
    if (messages.isEmpty()) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    final String correctivePrompt = String.join("\n\n", messages);
    final Event correctiveEvent = createCorrectiveEvent(context, correctivePrompt);
    final Content correctionContent = correctiveEvent.content().orElseThrow();

    final List<Content> contents = CollectionUtils.nullSafeMutableList(request.contents());
    contents.add(correctionContent);
    final LlmRequest updatedRequest = request.toBuilder().contents(contents).build();

    return Single.just(
        RequestProcessingResult.create(updatedRequest, List.of(correctiveEvent)));
  }

  private static Event createCorrectiveEvent(final InvocationContext context, final String message) {
    final Content correctiveContent =
        Content.builder()
            .role("user")
            .parts(List.of(Part.builder().text(message).build()))
            .build();

    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(context.invocationId())
        .author("user")
        .branch(context.branch())
        .content(correctiveContent)
        .build();
  }
}
