package com.agentengine.runtime.agents.processors.response;

import com.agentengine.runtime.utils.ContentUtils;
import com.agentengine.runtime.utils.ResponseUtils;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;

import java.util.*;

/**
 * Sanitizes tool calls in model responses, enforcing protocol constraints for partial output.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li><b>Partial responses:</b> No tool call or response parts appear in partial responses. (If
 *       present, they are stripped.)
 * </ul>
 */
public final class ToolCallSanitizationResponseProcessor implements ResponseProcessor {
    private final Set<String> availableTools;

    public ToolCallSanitizationResponseProcessor(final Collection<String> availableTools) {
        final Set<String> availableToolsSet = CollectionUtils.nullSafeMutableSet(availableTools);
        // ADK implicitly adds this via request processor
        availableToolsSet.add(Constants.AGENT_TRANSFER_TOOL_NAME);
        this.availableTools = availableToolsSet;
    }

    @Override
    public Single<ResponseProcessingResult> processResponse(
            final InvocationContext context, final LlmResponse response) {
        if (!response.partial().orElse(false)) {
            return ResponseUtils.single(response);
        }
        final Content content = response.content().orElse(null);
        if (content == null) {
            return ResponseUtils.single(response);
        }
        final List<FunctionCall> toolCalls = ToolUtils.extractToolCalls(response);
        if (CollectionUtils.isEmpty(toolCalls)) {
            return ResponseUtils.single(response);
        }
        final Content strippedContent = ContentUtils.stripNonToolParts(content);

        final List<Part> validParts = new ArrayList<>();
        final Set<String> invalidToolsCalls = new HashSet<>();
        for (final Part part : strippedContent.parts().orElse(List.of())) {
            final Optional<FunctionCall> functionCallOpt = part.functionCall();
            if (functionCallOpt.isEmpty()) {
                continue;
            }
            final FunctionCall functionCall = functionCallOpt.get();
            final String functionName = functionCall.name().orElse("");
            if (availableTools.contains(functionName)) {
                validParts.add(part);
            } else {
                invalidToolsCalls.add(functionName);
            }
        }

        RunUtils.getOrInitState(context).addViolation(Violation.builder("invalid_tool_calls").detail("invalidToolNames", invalidToolsCalls).build());

        return ResponseUtils.single(response.toBuilder()
                .content(strippedContent.toBuilder().parts(validParts).build())
                .build());
    }
}
