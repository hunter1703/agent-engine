/*
 * Copyright 2025 Google LLC
 */

package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for implementing correction processors.
 * <p>
 * This allows you to:
 * 1. Detect violations in LLM responses (ResponseProcessor phase)
 * 2. Fix the responses (e.g., strip problematic content)
 * 3. Store violation details for later
 * 4. Inject corrective prompts after tool execution (RequestProcessor phase)
 * <p>
 * Subclasses must implement:
 * - detectViolations(): Check if response violates any rules
 * - fixResponse(): Modify the response to remove violations
 * - buildCorrectionPrompt(): Create a corrective message
 */
public abstract class CorrectionProcessor implements RequestProcessor, ResponseProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CorrectionProcessor.class);
    private static final String VIOLATION_KEY_PREFIX = "correction.violations.";

    protected abstract List<Violation> detectViolations(
            InvocationContext context,
            LlmResponse response
    );

    protected abstract LlmResponse fixResponse(
            InvocationContext context,
            LlmResponse response,
            List<Violation> violations
    );

    protected abstract String buildCorrectionPrompt(
            InvocationContext context,
            List<Violation> violations
    );

    protected String getViolationKey() {
        return VIOLATION_KEY_PREFIX + this.getClass().getSimpleName();
    }

    @Override
    public Single<ResponseProcessingResult> processResponse(InvocationContext context, LlmResponse response) {
        List<Violation> violations = detectViolations(context, response);
        if (CollectionUtils.isEmpty(violations)) {
            logger.debug("No violations detected in response");
            return Single.just(ResponseProcessingResult.create(response, ImmutableList.of(), Optional.empty())
            );
        }

        logger.warn("Detected {} violation(s): {}", violations.size(), violations.stream().map(Violation::getCode).toList()
        );

        storeViolations(context, violations);

        LlmResponse fixedResponse = fixResponse(context, response, violations);
        return Single.just(
                ResponseProcessingResult.create(fixedResponse, ImmutableList.of(), Optional.empty())
        );
    }

    @Override
    public Single<RequestProcessingResult> processRequest(InvocationContext context, LlmRequest request) {
        List<Violation> violations = retrieveViolations(context);

        if (CollectionUtils.isEmpty(violations)) {
            logger.debug("No violations to correct");
            return Single.just(
                    RequestProcessingResult.create(request, ImmutableList.of())
            );
        }

        logger.info("Injecting correction prompt for {} violation(s)", violations.size());

        clearViolations(context);

        String correctionPrompt = buildCorrectionPrompt(context, violations);

        Event correctiveEvent = createCorrectiveEvent(context, correctionPrompt);
        Content correctionContent = correctiveEvent.content().map(content -> content.toBuilder().build()).orElse(null);

        final List<Content> contents = CollectionUtils.nullSafeMutableList(request.contents());
        contents.add(correctionContent);
        LlmRequest updatedRequest = request.toBuilder().contents(contents).build();

        return Single.just(RequestProcessingResult.create(updatedRequest, ImmutableList.of(correctiveEvent)));
    }

    private void storeViolations(InvocationContext context, List<Violation> violations) {
        context.session().state().compute(getViolationKey(), (_, oldVal) -> {
            //noinspection unchecked
            final List<Object> violationsList = oldVal == null ? new ArrayList<>() : (List<Object>) oldVal;
            violationsList.addAll(violations);
            return violationsList;
        });
    }

    @SuppressWarnings("unchecked")
    private List<Violation> retrieveViolations(InvocationContext context) {
        Object stored = context.session().state().get(getViolationKey());
        if (stored instanceof List<?>) {
            return (List<Violation>) stored;
        }
        return Collections.emptyList();
    }

    private void clearViolations(InvocationContext context) {
        context.session().state().remove(getViolationKey());
    }

    private static Event createCorrectiveEvent(InvocationContext context, String message) {
        Content correctiveContent = Content.builder()
                .role("user")
                .parts(ImmutableList.of(Part.builder().text(message).build()))
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