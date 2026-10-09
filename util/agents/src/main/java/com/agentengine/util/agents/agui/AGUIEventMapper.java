package com.agentengine.util.agents.agui;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.agui.community.core.event.MetaEvent;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.*;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.ExceptionUtils;
import com.agui.community.core.event.*;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps runtime SessionEvent to AGUI events */
public final class AGUIEventMapper implements EventMapper<SessionEvent, Event> {
  public static final String TOKEN_USAGE_META_TYPE = "token_usage";
  private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);

  private final AGUIMapperState state;
  private final AGUITextMapper textMapper;
  private final AGUIToolCallMapper toolCallMapper;

  public AGUIEventMapper(final String sessionId, final String agentId) {
    this.state = new AGUIMapperState(sessionId, agentId);
    this.textMapper = new AGUITextMapper(state);
    this.toolCallMapper = new AGUIToolCallMapper(state);
  }

  @Override
  public Flowable<Event> map(final SessionEvent event) {
    return mapInternal(event).map(this::withAuthor);
  }

  @Override
  public Flowable<Event> onError(final Throwable throwable) {
    LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
    final RunErrorEvent errorEvent =
        new RunErrorEvent(
            ExceptionUtils.getErrorSummary(throwable), null, state.timestamp(), null);
    LOG.debug("Generated output event in onError - eventType=RunErrorEvent");
    return Flowable.just(errorEvent);
  }

  private Flowable<Event> mapInternal(final SessionEvent event) {
    // Scope all state lookups below to this event's source session before anything else runs
    // — including the early-return branches, so a stale error/live-marker event never reads
    // or mutates the wrong session's in-flight run/step/message state.
    state.recordSourceEvent(event);
    LOG.debug(
        "Mapping SessionEvent - id={}, sessionId={}, runId={}, author={}, turnComplete={}, finishReason={}",
        event.getId(),
        event.getSessionId(),
        event.getRunId(),
        event.getAuthor(),
        event.getTurnComplete(),
        event.getFinishReason());

    if (event.isLiveMarker()) {
      return Flowable.empty();
    }

    if (event.isError()) {
      LOG.debug(
          "Mapping error event for session={}, errorMessage={}",
          event.getSessionId(),
          event.getErrorMessage());
      // RunErrorEvent has no run field, so the failed run — the one open in the failing
      // session, which may be a child's — travels in the raw event.
      final String failedRunId = state.finishRun();
      final Map<String, Object> rawEvent =
          failedRunId == null ? null : new HashMap<>(Map.of("runId", failedRunId));
      final RunErrorEvent errorEvent =
          new RunErrorEvent(event.getErrorMessage(), null, state.timestamp(), rawEvent);
      return textMapper.finalizeOpenContent().concatWith(Flowable.just(errorEvent));
    }

    Flowable<Event> eventFlow = Flowable.empty();
    if (state.hasNewRun(event.getRunId())) {
      eventFlow = eventFlow.concatWith(startRun(event));
    }
    return eventFlow.concatWith(mapEventInternal(event)).concatWith(finishRunIfNeeded(event));
  }

  private Flowable<Event> mapEventInternal(final SessionEvent event) {
    if (SessionEventUtils.isInternal(event)) {
      return Flowable.empty();
    }
    Flowable<Event> flowable = startStepIfNeeded();
    if (SessionEventUtils.isCorrectionEvent(event)) {
      return flowable.concatWith(mapCorrectionEvent(event)).concatWith(finishStepIfNeeded(event));
    }

    final Optional<Content> content = Optional.ofNullable(event.getContent());
    if (content.isPresent()) {
      final boolean partial = Boolean.TRUE.equals(event.isPartial());
      if (partial) {
        state.markPartialContentStreamedForStep();
      }
      for (final Part part : content.get().parts().orElse(List.of())) {
        flowable = flowable.concatWith(mapPart(part, partial));
      }
    }

    // Emit attachment events for any event that carried file metadata, regardless of author.
    // Attachments are stored in event metadata (keyed by SessionEventUtils.ATTACHMENTS) rather
    // than as fileData Parts, so they survive the single-text LLM message constraint.
    final List<AgentFileDetails> attachments =
        CollectionUtils.getValueFromMap(event.getMetadata(), SessionEventUtils.ATTACHMENTS);
    for (final AgentFileDetails fileDetails : CollectionUtils.nullSafeList(attachments)) {
      flowable = flowable.concatWith(textMapper.mapAttachment(fileDetails.toFileDetails()));
    }

    return flowable.concatWith(mapTokenUsage(event)).concatWith(finishStepIfNeeded(event));
  }

  private Flowable<Event> mapPart(final Part part, final boolean partial) {
    if (part.thought().orElse(false)) {
      if (!partial && state.hasStreamedPartialContentForStep()) {
        return Flowable.empty();
      }
      return textMapper.mapThought(part.text().orElse(""), partial);
    }

    Flowable<Event> flowable = Flowable.empty();
    final String text = part.text().orElse(null);

    if (text != null) {
      if (!partial && state.hasStreamedPartialContentForStep()) {
        // Echo of already-streamed text; do nothing.
      } else {
        flowable = flowable.concatWith(textMapper.mapText(text, partial));
      }
    }

    final FunctionCall call = part.functionCall().orElse(null);
    if (call != null) {
      flowable =
          flowable
              .concatWith(textMapper.closeReasoningIfNeeded())
              .concatWith(toolCallMapper.mapToolCall(call));
    }

    final FunctionResponse response = part.functionResponse().orElse(null);
    if (response != null) {
      flowable =
          flowable
              .concatWith(textMapper.closeReasoningIfNeeded())
              .concatWith(toolCallMapper.mapToolResponse(response));
    }
    return flowable;
  }

  private Flowable<Event> startRun(final SessionEvent sourceEvent) {
    state.startRun(sourceEvent.getRunId());
    final RunStartedEvent event =
        new RunStartedEvent(
            state.sessionId(),
            state.currentRunId(),
            sourceEvent.getParentRunId(),
            null,
            state.timestamp(),
            null);
    LOG.debug("Generated output event - eventType=RunStartedEvent, runId={}", event.threadId());
    return Flowable.just(event);
  }

  private Flowable<Event> finishRunIfNeeded(final SessionEvent event) {
    if (event.getFinishReason() == null) {
      return Flowable.empty();
    }
    if (state.hasPendingConfirmations()) {
      // The run is waiting for an answer, from a child or from a person, and goes on when it
      // arrives, so the listener sees one run that waited.
      return Flowable.empty();
    }

    final String finishedRunId = state.finishRun();
    if (finishedRunId == null) {
      // No run was ever started for this event's session — nothing to close out.
      return Flowable.empty();
    }
    final RunFinishedEvent finishedEvent =
        new RunFinishedEvent(
            state.sessionId(), finishedRunId, new SuccessOutcome(), null, state.timestamp(), null);
    LOG.debug(
        "Generated output event - eventType=RunFinishedEvent, runId={}", finishedEvent.threadId());
    return Flowable.just(finishedEvent);
  }

  private Flowable<Event> startStepIfNeeded() {
    if (state.hasStartedStep()) {
      return Flowable.empty();
    }

    final StepStartedEvent stepEvent =
        new StepStartedEvent(state.startNextStep(), state.timestamp(), null);
    LOG.debug(
        "Generated output event - eventType=StepStartedEvent, stepName={}", stepEvent.stepName());
    return Flowable.just(stepEvent);
  }

  private Flowable<Event> finishStepIfNeeded(final SessionEvent event) {
    if (!Boolean.TRUE.equals(event.isTurnComplete()) || !state.hasStartedStep()) {
      return Flowable.empty();
    }
    return finishStep();
  }

  private Flowable<Event> finishStep() {
    final StepFinishedEvent event =
        new StepFinishedEvent(state.finishStep(), state.timestamp(), null);
    LOG.debug(
        "Generated output event - eventType=StepFinishedEvent, stepName={}", event.stepName());
    return textMapper.finalizeOpenContent().concatWith(Flowable.just(event));
  }

  // The tokens a model call used, as a side-band annotation on the call's final event: it
  // describes the run without being part of it, which is what a MetaEvent is for.
  private Flowable<Event> mapTokenUsage(final SessionEvent event) {
    if (Boolean.TRUE.equals(event.isPartial()) || event.getRawEvent().usageMetadata().isEmpty()) {
      return Flowable.empty();
    }
    final GenerateContentResponseUsageMetadata usage = event.getRawEvent().usageMetadata().get();
    final Map<String, Object> payload = new HashMap<>();
    payload.put("eventId", event.getId());
    payload.put("sessionId", event.getSessionId());
    payload.put("inputTokens", usage.promptTokenCount().orElse(0));
    payload.put("outputTokens", usage.candidatesTokenCount().orElse(0));
    payload.put("thinkingTokens", usage.thoughtsTokenCount().orElse(0));
    payload.put("cachedTokens", usage.cachedContentTokenCount().orElse(0));
    payload.put("totalTokens", usage.totalTokenCount().orElse(0));
    return Flowable.just(new MetaEvent(TOKEN_USAGE_META_TYPE, payload, state.timestamp(), null));
  }

  private Flowable<Event> mapCorrectionEvent(final SessionEvent event) {
    final List<Violation> violations =
        Objects.requireNonNull(
            CollectionUtils.getListFromMap(event.getMetadata(), SessionEventUtils.VIOLATION));
    LOG.debug("Generated correction event - correctionMetadataPresent=true");
    return Flowable.just(AGUIUtils.buildCorrectionEvent(violations, state.timestamp()));
  }

  /**
   * Stamps the id of whoever actually generated this event onto every emitted event's {@code
   * rawEvent} — the source event's own author (an agent's name, or {@code "user"}), not the root
   * agent the client invoked — merged alongside whatever that event already carries there rather
   * than overwriting it. A single stage at the end of the pipeline, instead of threading {@code
   * author} through every {@code new XxxEvent(...)} call site across the mapper.
   */
  private Event withAuthor(final Event event) {
    //noinspection unchecked
    final Map<String, Object> rawEvent =
        CollectionUtils.nullSafeMutableMap((Map<String, Object>) event.rawEvent());
    rawEvent.put("author", state.currentAuthor());
    return switch (event) {
      case RunStartedEvent runStartedEvent ->
          new RunStartedEvent(
              runStartedEvent.threadId(),
              runStartedEvent.runId(),
              runStartedEvent.parentRunId(),
              runStartedEvent.input(),
              runStartedEvent.timestamp(),
              rawEvent);
      case RunFinishedEvent runFinishedEvent ->
          new RunFinishedEvent(
              runFinishedEvent.threadId(),
              runFinishedEvent.runId(),
              runFinishedEvent.outcome(),
              runFinishedEvent.result(),
              runFinishedEvent.timestamp(),
              rawEvent);
      case RunErrorEvent runErrorEvent ->
          new RunErrorEvent(
              runErrorEvent.message(), runErrorEvent.code(), runErrorEvent.timestamp(), rawEvent);
      case StepStartedEvent stepStartedEvent ->
          new StepStartedEvent(stepStartedEvent.stepName(), stepStartedEvent.timestamp(), rawEvent);
      case StepFinishedEvent stepFinishedEvent ->
          new StepFinishedEvent(
              stepFinishedEvent.stepName(), stepFinishedEvent.timestamp(), rawEvent);
      case TextMessageStartEvent textMessageStartEvent ->
          new TextMessageStartEvent(
              textMessageStartEvent.messageId(),
              textMessageStartEvent.role(),
              textMessageStartEvent.timestamp(),
              rawEvent);
      case TextMessageEndEvent textMessageEndEvent ->
          new TextMessageEndEvent(
              textMessageEndEvent.messageId(), textMessageEndEvent.timestamp(), rawEvent);
      case TextMessageChunkEvent textMessageChunkEvent ->
          new TextMessageChunkEvent(
              textMessageChunkEvent.messageId(),
              textMessageChunkEvent.role(),
              textMessageChunkEvent.delta(),
              textMessageChunkEvent.timestamp(),
              rawEvent);
      case ToolCallStartEvent toolCallStartEvent ->
          new ToolCallStartEvent(
              toolCallStartEvent.toolCallId(),
              toolCallStartEvent.toolCallName(),
              toolCallStartEvent.parentMessageId(),
              toolCallStartEvent.timestamp(),
              rawEvent);
      case ToolCallArgsEvent toolCallArgsEvent ->
          new ToolCallArgsEvent(
              toolCallArgsEvent.toolCallId(),
              toolCallArgsEvent.delta(),
              toolCallArgsEvent.timestamp(),
              rawEvent);
      case ToolCallEndEvent toolCallEndEvent ->
          new ToolCallEndEvent(
              toolCallEndEvent.toolCallId(), toolCallEndEvent.timestamp(), rawEvent);
      case ToolCallResultEvent toolCallResultEvent ->
          new ToolCallResultEvent(
              toolCallResultEvent.messageId(),
              toolCallResultEvent.toolCallId(),
              toolCallResultEvent.content(),
              toolCallResultEvent.role(),
              toolCallResultEvent.timestamp(),
              rawEvent);
      case ReasoningStartEvent reasoningStartEvent ->
          new ReasoningStartEvent(
              reasoningStartEvent.messageId(), reasoningStartEvent.timestamp(), rawEvent);
      case ReasoningEndEvent reasoningEndEvent ->
          new ReasoningEndEvent(
              reasoningEndEvent.messageId(), reasoningEndEvent.timestamp(), rawEvent);
      case ReasoningMessageStartEvent reasoningMessageStartEvent ->
          new ReasoningMessageStartEvent(
              reasoningMessageStartEvent.messageId(),
              reasoningMessageStartEvent.timestamp(),
              rawEvent);
      case ReasoningMessageEndEvent reasoningMessageEndEvent ->
          new ReasoningMessageEndEvent(
              reasoningMessageEndEvent.messageId(), reasoningMessageEndEvent.timestamp(), rawEvent);
      case ReasoningMessageContentEvent reasoningMessageContentEvent ->
          new ReasoningMessageContentEvent(
              reasoningMessageContentEvent.messageId(),
              reasoningMessageContentEvent.delta(),
              reasoningMessageContentEvent.timestamp(),
              rawEvent);
      case ReasoningMessageChunkEvent reasoningMessageChunkEvent ->
          new ReasoningMessageChunkEvent(
              reasoningMessageChunkEvent.messageId(),
              reasoningMessageChunkEvent.delta(),
              reasoningMessageChunkEvent.timestamp(),
              rawEvent);
      case CustomEvent customEvent ->
          new CustomEvent(
              customEvent.name(), customEvent.value(), customEvent.timestamp(), rawEvent);
      case MetaEvent metaEvent ->
          new MetaEvent(metaEvent.metaType(), metaEvent.payload(), metaEvent.timestamp(), rawEvent);
      // Every other Event subtype is never actually constructed by this mapper (or
      // AGUITextMapper/AGUIToolCallMapper/AGUIUtils) -- see AGUIEventCodec's javadoc for the exact
      // set and why. Keeping the two switches over Event's cases in sync is deliberate: this one
      // would otherwise need to be maintained forever for types that structurally can never reach
      // it.
      default ->
          throw new IllegalArgumentException(
              "Unsupported Event subtype: " + event.getClass().getName());
    };
  }
}
