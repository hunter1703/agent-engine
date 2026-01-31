/*
 * Copyright 2025 Google LLC
 */

package com.agentengine.engine.utils;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.utils.TemplateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * prevents LLM from returning both final answers and tool calls simultaneously.
 * <p>
 * Example usage:
 * <pre>
 * ToolCallAnswerSeparation processor = ToolCallAnswerSeparation.builder()
 *     .convertToThought(true)
 *     .correctionTemplate("Custom message: %s")
 *     .build();
 *
 * BaseLlmFlow flow = new BaseLlmFlow(
 *     ImmutableList.of(processor),
 *     ImmutableList.of(processor)
 * ) {};
 * </pre>
 */
public class FinalAnswerAndToolCorrection extends CorrectionProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinalAnswerAndToolCorrection.class);

    private static final String DEFAULT_CORRECTION_TEMPLATE =
            """
            You attempted to provide a final answer while simultaneously making tool calls. This is not allowed.
            
            What you did:
            - Tool calls: {{ toolCalls }}
            - Answer text: "{{ text }}"
            
            The tool calls have been executed. Please provide either final answer or tool calls but NEVER both.""";

    private final boolean convertToThought;
    private final String correctionTemplate;

    private FinalAnswerAndToolCorrection(Builder builder) {
        this.convertToThought = builder.convertToThought;
        this.correctionTemplate = builder.correctionTemplate;
    }

    @Override
    protected List<Violation> detectViolations(InvocationContext context, LlmResponse response) {
        if (response.content().isEmpty()) {
            return List.of();
        }

        Content content = response.content().get();
        List<String> toolCallNames = new ArrayList<>();

        final String finalAnswer = content.text();
        for (Part part : content.parts().orElse(List.of())) {
            final Optional<FunctionCall> functionCall = part.functionCall();
            if (functionCall != null && functionCall.isPresent()) {
                toolCallNames.add(functionCall.get().name().orElse("unknown"));
            }
        }

        if (StringUtils.isNotBlank(finalAnswer) && CollectionUtils.isNotEmpty(toolCallNames)) {
            Map<String, Object> details = new HashMap<>();
            details.put("text", finalAnswer);
            details.put("toolCalls", toolCallNames);
            details.put("toolCount", toolCallNames.size());
            details.put("textLength", finalAnswer.length());

            Violation violation = Violation.builder(Violation.Codes.ANSWER_WITH_TOOL_CALLS)
                    .message("Model returned both text and tool calls")
                    .details(details)
                    .build();

            return List.of(violation);
        }

        return List.of();
    }

    @Override
    protected LlmResponse fixResponse(InvocationContext context, LlmResponse response, List<Violation> violations) {
        if (violations.isEmpty()) {
            return response;
        }

        Content originalContent = response.content().orElse(null);
        if (originalContent == null) {
            return response;
        }
        ImmutableList.Builder<Part> newParts = ImmutableList.builder();

        final StringBuilder thoughtsBuilder = new StringBuilder();
        for (Part part : originalContent.parts().orElse(List.of())) {
            if (!part.thought().orElse(false)) {
                thoughtsBuilder.append(part.text().orElse("")).append("\n");
            } else {
                newParts.add(part);
            }
        }

        if (convertToThought && !thoughtsBuilder.isEmpty()) {
            newParts.add(Part.builder().text(thoughtsBuilder.toString().trim()).thought(true).build());
        }

        Content newContent = Content.builder()
                .role(originalContent.role().orElse(null))
                .parts(newParts.build())
                .build();

        return response.toBuilder().content(newContent).build();
    }

    @Override
    protected String buildCorrectionPrompt(InvocationContext context, List<Violation> violations) {
        List<String> allToolCalls = new ArrayList<>();
        List<String> allTexts = new ArrayList<>();

        for (Violation violation : violations) {
            List<String> toolCalls = violation.getDetail("toolCalls");
            String text = violation.getDetail("text");

            allToolCalls.addAll(toolCalls);
            allTexts.add(text);
        }

        String toolCallsList = allToolCalls.stream()
                .distinct()
                .collect(Collectors.joining(", "));

        String combinedText = String.join(" | ", allTexts);
        if (combinedText.length() > 200) {
            combinedText = STR."\{combinedText.substring(0, 197)}...";
        }

        return TemplateUtils.renderTextTemplate(correctionTemplate, Map.of(
                "toolCalls", toolCallsList,
                "text", combinedText
        ));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean convertToThought = false;
        private String correctionTemplate = DEFAULT_CORRECTION_TEMPLATE;

        public Builder convertToThought(boolean convertToThought) {
            this.convertToThought = convertToThought;
            return this;
        }

        public Builder correctionTemplate(String template) {
            this.correctionTemplate = template;
            return this;
        }

        public FinalAnswerAndToolCorrection build() {
            return new FinalAnswerAndToolCorrection(this);
        }
    }

    public static FinalAnswerAndToolCorrection create() {
        return builder().build();
    }

    public static FinalAnswerAndToolCorrection createWithThoughts() {
        return builder().convertToThought(true).build();
    }
}
