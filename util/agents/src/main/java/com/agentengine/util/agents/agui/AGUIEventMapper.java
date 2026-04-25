package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.*;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunErrorEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.agui.core.event.StepFinishedEvent;
import com.agui.core.event.StepStartedEvent;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Maps runtime SessionEvent to AGUI events */
public final class AGUIEventMapper implements EventMapper<SessionEvent, BaseEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(AGUIEventMapper.class);

    public enum Mode {
        LIVE,
        REPLAY
    }

    private final AGUIMapperState state;
    private final AGUIEventDecorator decorator;
    private final AGUITextMapper textMapper;
    private final AGUIToolCallMapper toolCallMapper;

    public AGUIEventMapper(final String sessionId, final String agentId, final Mode mode) {
        this.state = new AGUIMapperState(sessionId, agentId);
        this.decorator = new AGUIEventDecorator(state);
        this.textMapper = new AGUITextMapper(state, decorator, mode);
        this.toolCallMapper = new AGUIToolCallMapper(state, decorator);
    }

    @Override
    public Flowable<BaseEvent> map(final SessionEvent event) {
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
            final RunErrorEvent errorEvent = new RunErrorEvent();
            errorEvent.setError(event.getErrorMessage());
            decorator.decorate(errorEvent);
            return textMapper.finalizeOpenContent().concatWith(Flowable.just(errorEvent));
        }

        state.recordSourceEvent(event);
        Flowable<BaseEvent> eventFlow = Flowable.empty();

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

        final Flowable<BaseEvent> result =
                eventFlow.concatWith(mapEventInternal(event)).concatWith(finishRunIfNeeded(event));

        LOG.info("=== AGUIEventMapper.map() END - will emit events ===");
        return result;
    }

    @Override
    public Flowable<BaseEvent> onComplete() {
        return Flowable.empty();
    }

    @Override
    public Flowable<BaseEvent> onError(final Throwable throwable) {
        LOG.debug("Processing error mapping - throwable={}", ExceptionUtils.getErrorMessage(throwable));
        final RunErrorEvent errorEvent = new RunErrorEvent();
        errorEvent.setError(ExceptionUtils.getErrorMessage(throwable));
        errorEvent.setRawEvent(Map.of("exception", ExceptionUtils.getStackstrace(throwable)));
        LOG.debug("Generated output event in onError - eventType=RunErrorEvent");
        return Flowable.just(decorator.decorate(errorEvent));
    }

    private Flowable<BaseEvent> mapEventInternal(final SessionEvent event) {
        LOG.info(
                "mapEventInternal called - event : {}", JsonUtils.toJson(event));
        final boolean internal = SessionEventUtils.isInternal(event);
        if (internal) {
            return Flowable.empty();
        }
        Flowable<BaseEvent> flowable = startStepIfNeeded();
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
        return flowable.concatWith(finishStepIfNeeded(event));
    }

    private Flowable<BaseEvent> mapPart(final Part part, final boolean partial) {
        if (part.thought().orElse(false)) {
            return textMapper.mapThought(part.text().orElse(""), partial);
        }

        Flowable<BaseEvent> flowable = Flowable.empty();
        final String text = part.text().orElse(null);

        final FileData fileData = part.fileData().orElse(null);
        if (fileData != null) {
            String uri = fileData.fileUri().orElse(null);
            final FileDetails fileDetails = Objects.requireNonNull(StringUtils.isNotBlank(uri) ? FileDetails.fromUrl(uri) : JsonUtils.fromJson(part.text().orElse(null), FileDetails.class));
            flowable = flowable.concatWith(textMapper.mapAttachment(fileDetails));
        } else if (text != null) {
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

    private Flowable<BaseEvent> startRun(final String runId) {
        LOG.info(">>> EMITTING RUN_STARTED - runId={}", runId);
        state.startRun(runId);
        final RunStartedEvent event = new RunStartedEvent();
        event.setRunId(state.currentRunId());
        event.setThreadId(state.sessionId());
        decorator.decorate(event);
        LOG.debug("Generated output event - eventType=RunStartedEvent, runId={}", event.getRunId());
        return Flowable.just(event);
    }

    private Flowable<BaseEvent> finishRunIfNeeded(final SessionEvent event) {
        LOG.info(
                "finishRunIfNeeded - eventFinishReason={}, currentRunId={}",
                event.getFinishReason(),
                state.currentRunId());

        if (event.getFinishReason() == null) {
            LOG.info("No finishReason - not finishing run");
            return Flowable.empty();
        }

        LOG.info(">>> EMITTING RUN_FINISHED - about to call state.finishRun()");
        final RunFinishedEvent finishedEvent = new RunFinishedEvent();
        finishedEvent.setThreadId(state.sessionId());
        final String finishedRunId = state.finishRun();
        finishedEvent.setRunId(finishedRunId);
        LOG.info(
                ">>> RUN_FINISHED - finishedRunId={}, state.currentRunId() is now={}",
                finishedRunId,
                state.currentRunId());

        if (state.finalAnswer() != null) {
            finishedEvent.setResult(state.finalAnswer());
        }
        decorator.decorate(finishedEvent);
        LOG.debug("Generated output event - eventType=RunFinishedEvent, runId={}", finishedEvent.getRunId());
        return Flowable.just(finishedEvent);
    }

    private Flowable<BaseEvent> startStepIfNeeded() {
        if (state.hasStartedStep()) {
            LOG.info("Step already started - skipping StepStartedEvent generation");
            return Flowable.empty();
        }

        final StepStartedEvent stepEvent = new StepStartedEvent();
        stepEvent.setStepName(state.startNextStep());
        decorator.decorate(stepEvent);
        LOG.info("STEP STARTED - Generated StepStartedEvent, stepName={}", stepEvent.getStepName());
        return Flowable.just(stepEvent);
    }

    private Flowable<BaseEvent> finishStepIfNeeded(final SessionEvent event) {
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

    private Flowable<BaseEvent> finishStep() {
        final StepFinishedEvent event = new StepFinishedEvent();
        event.setStepName(state.finishStep());
        decorator.decorate(event);
        LOG.info("STEP FINISHED - Generated StepFinishedEvent, stepName={}", event.getStepName());
        return textMapper.finalizeOpenContent().concatWith(Flowable.just(event));
    }

    private Flowable<BaseEvent> mapCorrectionEvent(final SessionEvent event) {
        final CorrectionEvent correctionEvent = new CorrectionEvent(Objects.requireNonNull(CollectionUtils.getValueFromMap(event.getMetadata(), SessionEventUtils.VIOLATION)));
        decorator.decorate(correctionEvent);
        LOG.debug("Generated correction event - correctionMetadataPresent=true");
        return Flowable.just(correctionEvent);
    }
}
