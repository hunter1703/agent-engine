package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.*;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.community.core.event.*;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps runtime SessionEvent to AGUI events */
public final class AGUIEventMapper implements EventMapper<SessionEvent, Event> {
    private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);

    public enum Mode {
        LIVE,
        REPLAY
    }

    private final AGUIMapperState state;
    private final AGUITextMapper textMapper;
    private final AGUIToolCallMapper toolCallMapper;

    public AGUIEventMapper(final String sessionId, final String agentId, final Mode mode) {
        this.state = new AGUIMapperState(sessionId, agentId);
        this.textMapper = new AGUITextMapper(state, mode);
        this.toolCallMapper = new AGUIToolCallMapper(state);
    }

    @Override
    public Flowable<Event> map(final SessionEvent event) {
        LOG.info("=== AGUIEventMapper.map() START ===");
        LOG.info(
                "Input SessionEvent - id={}, runId={}, author={}, turnComplete={}, finishReason={}, content={}, terminal={}",
                event.getId(),
                event.getRunId(),
                event.getAuthor(),
                event.getTurnComplete(),
                event.getFinishReason(),
                JsonUtils.toJson(event.getContent()),
                event.isTerminal());
        LOG.info(
                "Current mapper state BEFORE processing - currentRunId={}, hasStartedStep={}",
                state.currentRunId(),
                state.hasStartedStep());

        if (event.isLiveMarker()) {
            textMapper.switchToLiveMode();
            return Flowable.empty();
        }

        if (event.isError()) {
            LOG.info(
                    "Mapping error event for session={}, errorMessage={}",
                    event.getSessionId(),
                    event.getErrorMessage());
            final RunErrorEvent errorEvent = new RunErrorEvent(event.getErrorMessage(), null, state.timestamp(), null);
            return textMapper.finalizeOpenContent().concatWith(Flowable.just(errorEvent));
        }

        state.recordSourceEvent(event);
        Flowable<Event> eventFlow = Flowable.empty();

        final boolean hasNewRun = state.hasNewRun(event.getRunId());
        LOG.info(
                "hasNewRun check - eventRunId={}, currentStateRunId={}, hasNewRun={}",
                event.getRunId(),
                state.currentRunId(),
                hasNewRun);

        if (hasNewRun) {
            LOG.info("Starting new run - runId={}", event.getRunId());
            eventFlow = eventFlow.concatWith(startRun(event.getRunId()));
        }

        final Flowable<Event> result =
                eventFlow.concatWith(mapEventInternal(event)).concatWith(finishRunIfNeeded(event));

        LOG.info("=== AGUIEventMapper.map() END - will emit events ===");
        return result;
    }

    @Override
    public Flowable<Event> onComplete() {
        return Flowable.empty();
    }

    @Override
    public Flowable<Event> onError(final Throwable throwable) {
        LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
        final RunErrorEvent errorEvent =
                new RunErrorEvent(ExceptionUtils.getErrorMessage(throwable), null, state.timestamp(), null);
        LOG.debug("Generated output event in onError - eventType=RunErrorEvent");
        return Flowable.just(errorEvent);
    }

    private Flowable<Event> mapEventInternal(final SessionEvent event) {
        LOG.info("mapEventInternal called - event : {}", JsonUtils.toJson(event));
        final boolean internal = SessionEventUtils.isInternal(event);
        if (internal) {
            return Flowable.empty();
        }
        Flowable<Event> flowable = startStepIfNeeded();
        if (SessionEventUtils.isCorrectionEvent(event)) {
            return flowable.concatWith(mapCorrectionEvent(event)).concatWith(finishStepIfNeeded(event));
        }

        final Optional<Content> content = Optional.ofNullable(event.getContent());
        if (content.isEmpty()) {
            LOG.info("Event has no content - calling finishStepIfNeeded");
            return flowable.concatWith(finishStepIfNeeded(event));
        }

        final boolean partial = Boolean.TRUE.equals(event.isPartial());
        LOG.info(
                "Processing content - partial={}, internal={}, partsCount={}",
                partial,
                false,
                content.get().parts().map(List::size).orElse(0));

        for (final Part part : content.get().parts().orElse(List.of())) {
            flowable = flowable.concatWith(mapPart(part, partial));
        }

        // Emit attachment events for user messages that carried file metadata.
        // Attachments are stored in event metadata (keyed by SessionEventUtils.ATTACHMENTS)
        // rather than as fileData Parts, so they survive the single-text LLM message constraint.
        if (Constants.AUTHOR_USER.equals(event.getAuthor())) {
            final List<FileDetails> attachments =
                    CollectionUtils.getValueFromMap(event.getMetadata(), SessionEventUtils.ATTACHMENTS);
            if (CollectionUtils.isNotEmpty(attachments)) {
                for (final FileDetails fileDetails : attachments) {
                    flowable = flowable.concatWith(textMapper.mapAttachment(fileDetails));
                }
            }
        }

        return flowable.concatWith(finishStepIfNeeded(event));
    }

    private Flowable<Event> mapPart(final Part part, final boolean partial) {
        if (part.thought().orElse(false)) {
            return textMapper.mapThought(part.text().orElse(""), partial);
        }

        Flowable<Event> flowable = Flowable.empty();
        final String text = part.text().orElse(null);

        if (text != null) {
            flowable = flowable.concatWith(textMapper.mapText(text, partial));
        }

        final FunctionCall call = part.functionCall().orElse(null);
        if (call != null) {
            flowable = flowable.concatWith(textMapper.closeReasoningIfNeeded())
                    .concatWith(toolCallMapper.mapToolCall(call));
        }

        final FunctionResponse response = part.functionResponse().orElse(null);
        if (response != null) {
            flowable = flowable.concatWith(textMapper.closeReasoningIfNeeded())
                    .concatWith(toolCallMapper.mapToolResponse(response));
        }
        return flowable;
    }

    private Flowable<Event> startRun(final String runId) {
        LOG.info(">>> EMITTING RUN_STARTED - runId={}", runId);
        state.startRun(runId);
        final RunStartedEvent event =
                new RunStartedEvent(state.sessionId(), state.currentRunId(), null, null, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=RunStartedEvent, runId={}", event.threadId());
        return Flowable.just(event);
    }

    private Flowable<Event> finishRunIfNeeded(final SessionEvent event) {
        LOG.info(
                "finishRunIfNeeded - eventFinishReason={}, currentRunId={}",
                event.getFinishReason(),
                state.currentRunId());

        if (event.getFinishReason() == null) {
            LOG.info("No finishReason - not finishing run");
            return Flowable.empty();
        }

        LOG.info(">>> EMITTING RUN_FINISHED - about to call state.finishRun()");
        final String finishedRunId = state.finishRun();
        LOG.info(
                ">>> RUN_FINISHED - finishedRunId={}, state.currentRunId() is now={}",
                finishedRunId,
                state.currentRunId());
        final RunFinishedEvent finishedEvent = new RunFinishedEvent(
                state.sessionId(), finishedRunId, new SuccessOutcome(), null, state.timestamp(), null);
        LOG.debug("Generated output event - eventType=RunFinishedEvent, runId={}", finishedEvent.threadId());
        return Flowable.just(finishedEvent);
    }

    private Flowable<Event> startStepIfNeeded() {
        if (state.hasStartedStep()) {
            LOG.info("Step already started - skipping StepStartedEvent generation");
            return Flowable.empty();
        }

        final StepStartedEvent stepEvent = new StepStartedEvent(state.startNextStep(), state.timestamp(), null);
        LOG.info("STEP STARTED - Generated StepStartedEvent, stepName={}", stepEvent.stepName());
        return Flowable.just(stepEvent);
    }

    private Flowable<Event> finishStepIfNeeded(final SessionEvent event) {
        LOG.info(
                "finishStepIfNeeded called - sessionId={}, turnComplete={}, hasStartedStep={}, event={}",
                event.getSessionId(),
                event.getTurnComplete(),
                state.hasStartedStep(),
                JsonUtils.toJson(event));

        if (!Boolean.TRUE.equals(event.isTurnComplete()) || !state.hasStartedStep()) {
            LOG.info(
                    "Step NOT finished - conditions not met: turnComplete={}, hasStartedStep={}",
                    event.getTurnComplete(),
                    state.hasStartedStep());
            return Flowable.empty();
        }

        LOG.info(
                "Step WILL finish - all conditions met: turnComplete={}, hasStartedStep={}",
                event.getTurnComplete(),
                true);
        return finishStep();
    }

    private Flowable<Event> finishStep() {
        final StepFinishedEvent event = new StepFinishedEvent(state.finishStep(), state.timestamp(), null);
        LOG.info("STEP FINISHED - Generated StepFinishedEvent, stepName={}", event.stepName());
        return textMapper.finalizeOpenContent().concatWith(Flowable.just(event));
    }

    private Flowable<Event> mapCorrectionEvent(final SessionEvent event) {
        final Violation violation = Objects.requireNonNull(
                CollectionUtils.getValueFromMap(event.getMetadata(), SessionEventUtils.VIOLATION));
        LOG.debug("Generated correction event - correctionMetadataPresent=true");
        return Flowable.just(AGUIUtils.buildCorrectionEvent(violation, state.timestamp()));
    }
}
