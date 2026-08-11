package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.StringUtils;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import java.util.*;

public final class AGUIMapperState {

    private final String sessionId;
    private final String agentId;
    private final StringBuilder textBuffer = new StringBuilder();
    private final Map<String, String> toolCallParentSteps = new HashMap<>();

    private String runId;
    private String currentStepName;
    private String currentTextMessageId;
    private String currentReasoningMessageId;
    private boolean reasoningOpen;
    private boolean reasoningMessageOpen;
    private String finalAnswer;
    private long currentSourceTimestamp;
    private String currentSourceEventId;
    private String currentAuthor;
    private int stepSequence;
    private int textMessageSequence;
    private int reasoningMessageSequence;
    private final Map<String, FunctionCall> requestConfirmationCalls = new HashMap<>();

    public AGUIMapperState(final String sessionId, final String agentId) {
        this.sessionId = sessionId;
        this.agentId = agentId;
    }

    public void recordSourceEvent(final SessionEvent event) {
        currentSourceTimestamp = event.getTimestamp();
        currentSourceEventId = event.getId();
        currentAuthor = event.getAuthor();
        final Content content = event.getContent();
        if (content != null) {
            content.parts()
                    .orElse(List.of())
                    .forEach(part -> part.functionCall()
                            .ifPresent(functionCall -> requestConfirmationCalls.put(
                                    functionCall.id().orElseThrow(), functionCall)));
        }
    }

    public boolean hasNewRun(final String candidateRunId) {
        return candidateRunId != null && !Objects.equals(runId, candidateRunId);
    }

    public void startRun(final String runId) {
        this.runId = runId;
    }

    public String finishRun() {
        final String finishedRunId = runId;
        runId = null;
        return finishedRunId;
    }

    public String currentRunId() {
        return runId;
    }

    public boolean hasStartedStep() {
        return StringUtils.isNotBlank(currentStepName);
    }

    public String startNextStep() {
        // Include runId to ensure uniqueness across runs in the same session
        final String prefix = runId != null ? "step-" + runId + "-" : "step-";
        currentStepName = stableReplayId(prefix, currentSourceEventId, ++stepSequence);
        return currentStepName;
    }

    public String currentStepName() {
        return currentStepName;
    }

    public String finishStep() {
        final String stepName = currentStepName;
        currentStepName = null;
        toolCallParentSteps.clear();
        return stepName;
    }

    public boolean hasOpenTextMessage() {
        return currentTextMessageId != null;
    }

    public String startNextTextMessage() {
        currentTextMessageId = nextTextMessageId(currentSourceEventId);
        return currentTextMessageId;
    }

    public String nextTextMessageId(final String sourceEventId) {
        // Include runId to ensure uniqueness across runs in the same session
        final String prefix = runId != null ? "msg-" + runId + "-" : "msg-";
        return stableReplayId(prefix, sourceEventId, ++textMessageSequence);
    }

    public String currentTextMessageId() {
        return currentTextMessageId;
    }

    public String currentReasoningMessageId() {
        return currentReasoningMessageId;
    }

    public boolean isTextBufferEmpty() {
        return textBuffer.isEmpty();
    }

    public void appendText(final String text) {
        if (StringUtils.isNotEmpty(text)) {
            textBuffer.append(text);
        }
    }

    public String completeTextMessage() {
        finalAnswer = textBuffer.toString();
        return finalAnswer;
    }

    public void resetTextMessage() {
        currentTextMessageId = null;
        textBuffer.setLength(0);
    }

    public String finalAnswer() {
        return finalAnswer;
    }

    public boolean hasOpenReasoning() {
        return reasoningOpen;
    }

    public void startReasoning() {
        reasoningOpen = true;
    }

    public boolean hasOpenReasoningMessage() {
        return reasoningMessageOpen;
    }

    public String startReasoningMessage() {
        reasoningMessageOpen = true;
        currentReasoningMessageId = nextReasoningMessageId(currentSourceEventId);
        return currentReasoningMessageId;
    }

    public String nextReasoningMessageId(final String sourceEventId) {
        // Include runId to ensure uniqueness across runs in the same session
        final String prefix = runId != null ? "think-" + runId + "-" : "think-";
        return stableReplayId(prefix, sourceEventId, ++reasoningMessageSequence);
    }

    public void closeReasoningMessage() {
        reasoningMessageOpen = false;
        currentReasoningMessageId = null;
    }

    public void closeReasoning() {
        reasoningOpen = false;
    }

    public void rememberToolCallParentStep(final String callId) {
        toolCallParentSteps.put(callId, currentStepName);
    }

    public String consumeToolCallParentStep(final String callId) {
        return toolCallParentSteps.remove(callId);
    }

    /** Returns the timestamp to stamp onto the current event, falling back to wall clock. */
    public long timestamp() {
        return currentSourceTimestamp > 0 ? currentSourceTimestamp : System.currentTimeMillis();
    }

    public String sessionId() {
        return sessionId;
    }

    public String agentId() {
        return agentId;
    }

    public String currentAuthor() {
        return currentAuthor != null ? currentAuthor : agentId;
    }

    public FunctionCall getConfirmationRequestedCall(final String confirmationId) {
        return requestConfirmationCalls.get(confirmationId);
    }

    private static String stableReplayId(final String prefix, final String sourceEventId, final int sequence) {
        if (StringUtils.isNotBlank(sourceEventId)) {
            return prefix + sourceEventId + "-" + sequence;
        }
        return prefix + sequence;
    }
}
