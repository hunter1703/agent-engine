package com.agentengine.runtime.agents.processors.response;

import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.runtime.utils.ContentUtils;
import com.agentengine.runtime.utils.ResponseUtils;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sanitizes tool calls in model responses, enforcing protocol constraints for partial output.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 * <li><b>Partial responses:</b> No tool call or response parts appear in partial responses.
 * (If present, they are stripped.)
 * </ul>
 */
public final class ToolCallSanitizationResponseProcessor implements ResponseProcessor {
  public static final ToolCallSanitizationResponseProcessor INSTANCE = new ToolCallSanitizationResponseProcessor();

  private ToolCallSanitizationResponseProcessor() {
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
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
    return ResponseUtils.single(response.toBuilder().content(ContentUtils.stripToolParts(content)).build());
  }
}
