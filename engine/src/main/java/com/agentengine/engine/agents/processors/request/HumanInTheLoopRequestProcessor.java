package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.SessionPauseReason;
import com.agentengine.engine.utils.SessionStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Handles user-in-the-loop resume semantics.
 *
 * <p>This processor dispatches resume handling by pause reason:
 *
 * <ul>
 *   <li>Tool confirmation: maps user text to the native {@code adk_request_confirmation}
 *       function-response payload.
 *   <li>Clarification or generic pause: appends a resume message that links the answer to the
 *       paused prompt.
 * </ul>
 */
public final class HumanInTheLoopRequestProcessor implements RequestProcessor {
  public static final HumanInTheLoopRequestProcessor INSTANCE = new HumanInTheLoopRequestProcessor();
  private static final String REQUEST_CONFIRMATION_FUNCTION = "adk_request_confirmation";
  private HumanInTheLoopRequestProcessor() {}

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final String userAnswer = extractLatestUserAnswer(request);
    final String pendingConfirmationId = findPendingConfirmationId(context);
    final boolean paused = SessionStateUtils.isPaused(context);

    if (!paused) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    final SessionPauseReason pauseReason =
            SessionPauseReason.valueOfOrDefault(SessionStateUtils.getPauseReason(context));
    if (pauseReason == SessionPauseReason.TOOL_CONFIRMATION
        || StringUtils.isNotBlank(pendingConfirmationId)) {
      if (StringUtils.isBlank(userAnswer)) {
        context.setEndInvocation(true);
        return Single.just(
            RequestProcessingResult.create(
                request,
                List.of(
                    buildPausedEvent(
                        context,
                        "No confirmation answer was provided. Provide your confirmation text to continue."))));
      }
      if (StringUtils.isBlank(pendingConfirmationId)) {
        context.setEndInvocation(true);
        return Single.just(
            RequestProcessingResult.create(
                request,
                List.of(
                    buildPausedEvent(
                        context,
                        "No pending tool confirmation exists. Retry the action that requires confirmation."))));
      }
      SessionStateUtils.resume(context);
      final LlmRequest updated =
          buildConfirmationResumeRequest(request, pendingConfirmationId, userAnswer);
      return Single.just(RequestProcessingResult.create(updated, List.of()));
    }

    final String question = SessionStateUtils.getPausePrompt(context);
    if (StringUtils.isBlank(userAnswer)) {
      final String missingAnswerMessage = buildMissingAnswerMessage(question);
      context.setEndInvocation(true);
      return Single.just(
          RequestProcessingResult.create(request, List.of(buildPausedEvent(context, missingAnswerMessage))));
    }

    SessionStateUtils.resume(context);
    final String resumeMessage = buildResumeMessage(question, userAnswer);
    final List<Content> contents = new ArrayList<>(request.contents() == null ? List.of() : request.contents());
    contents.add(
        Content.builder().role("user").parts(List.of(Part.fromText(resumeMessage))).build());

    final LlmRequest updated = request.toBuilder().contents(contents).build();
    return Single.just(RequestProcessingResult.create(updated, List.of()));
  }

  private static LlmRequest buildConfirmationResumeRequest(
      final LlmRequest request, final String confirmationId, final String userAnswer) {
    final FunctionResponse response =
        FunctionResponse.builder()
            .id(confirmationId)
            .name(REQUEST_CONFIRMATION_FUNCTION)
            .response(
                Map.of("confirmed", true, "payload", Map.of("answer", userAnswer.trim())))
            .build();
    final Content confirmationContent =
        Content.builder()
            .role("user")
            .parts(List.of(Part.builder().functionResponse(response).build()))
            .build();

    final List<Content> contents =
        new ArrayList<>(request.contents() == null ? List.of() : request.contents());
    final int latestUserIndex = findLatestUserContentIndex(contents);
    if (latestUserIndex >= 0) {
      contents.set(latestUserIndex, confirmationContent);
    } else {
      contents.add(confirmationContent);
    }
    return request.toBuilder().contents(contents).build();
  }

  private static int findLatestUserContentIndex(final List<Content> contents) {
    if (contents == null || contents.isEmpty()) {
      return -1;
    }
    for (int i = contents.size() - 1; i >= 0; i--) {
      final Content content = contents.get(i);
      if (content == null) {
        continue;
      }
      final Optional<String> role = content.role();
      if (role.isEmpty() || "user".equalsIgnoreCase(role.get())) {
        return i;
      }
    }
    return -1;
  }

  private static Event buildPausedEvent(final InvocationContext context, final String message) {
    final String author =
        context != null && context.agent() != null && StringUtils.isNotBlank(context.agent().name())
            ? context.agent().name()
            : "system";
    final String invocationId =
        context == null || StringUtils.isBlank(context.invocationId())
            ? java.util.UUID.randomUUID().toString()
            : context.invocationId();
    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(invocationId)
        .author(author)
        .content(
            Optional.of(
                Content.builder().role("model").parts(List.of(Part.fromText(message))).build()))
        .build();
  }

  private static String buildResumeMessage(final String question, final String userAnswer) {
    final StringBuilder builder = new StringBuilder();
    if (StringUtils.isNotBlank(question)) {
      builder
          .append("Resuming after clarification question: '")
          .append(question)
          .append("'.");
    }
    builder.append(" Use this clarification answer: '").append(userAnswer).append("'.");
    builder.append(" Continue the task.");
    return builder.toString();
  }

  private static String buildMissingAnswerMessage(final String question) {
    final StringBuilder builder = new StringBuilder();
    builder.append("No clarification answer was provided.");
    if (StringUtils.isNotBlank(question)) {
      builder.append(" Ask the user to answer this question: '").append(question).append("'.");
    } else {
      builder.append(" Ask the user for the required clarification.");
    }
    builder.append(" Do not continue the task until clarification is provided.");
    return builder.toString();
  }

  private static String extractLatestUserAnswer(final LlmRequest request) {
    if (request == null || request.contents() == null || request.contents().isEmpty()) {
      return "";
    }
    final List<Content> contents = request.contents();
    for (int i = contents.size() - 1; i >= 0; i--) {
      final Content content = contents.get(i);
      if (content == null) {
        continue;
      }
      final Optional<String> role = content.role();
      if (role.isPresent() && !"user".equalsIgnoreCase(role.get())) {
        continue;
      }
      final String text = content.text();
      if (StringUtils.isNotBlank(text)) {
        return text.trim();
      }
    }
    return "";
  }

  private static String findPendingConfirmationId(final InvocationContext context) {
    if (context == null || context.session() == null || CollectionUtils.isEmpty(context.session().events())) {
      return null;
    }
    final List<String> requested = new ArrayList<>();
    final Set<String> responded = new LinkedHashSet<>();
    for (final Event event : CollectionUtils.nullSafeList(context.session().events())) {
      if (event == null) {
        continue;
      }
      event.functionCalls().stream()
          .filter(functionCall -> functionCall.id().isPresent())
          .filter(
              functionCall ->
                  REQUEST_CONFIRMATION_FUNCTION.equals(functionCall.name().orElse(null)))
          .forEach(functionCall -> requested.add(functionCall.id().orElse("")));
      event.functionResponses().stream()
          .filter(functionResponse -> functionResponse.id().isPresent())
          .filter(
              functionResponse ->
                  REQUEST_CONFIRMATION_FUNCTION.equals(functionResponse.name().orElse(null)))
          .forEach(functionResponse -> responded.add(functionResponse.id().orElse("")));
    }
    for (int i = requested.size() - 1; i >= 0; i--) {
      final String id = requested.get(i);
      if (StringUtils.isNotBlank(id) && !responded.contains(id)) {
        return id;
      }
    }
    return null;
  }
}
