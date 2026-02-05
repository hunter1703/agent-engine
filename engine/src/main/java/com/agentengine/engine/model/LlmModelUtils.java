package com.agentengine.engine.model;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;

final class LlmModelUtils {
  private LlmModelUtils() {
  }

  static LlmResponse markTurnComplete(final LlmResponse response) {
    if (response == null) {
      return null;
    }
    return response.toBuilder().turnComplete(true).build();
  }

  static LlmRequest stripToolsFromModelRequest(final LlmRequest llmRequest, final boolean toolCallingEnabled,
      final boolean parseToolCallsFromText) {
    if (llmRequest == null || CollectionUtils.isEmpty(llmRequest.tools())
        || (toolCallingEnabled && !parseToolCallsFromText)) {
      return llmRequest;
    }
    final LlmRequest.Builder builder = LlmRequest.builder().contents(llmRequest.contents())
        .liveConnectConfig(llmRequest.liveConnectConfig());
    llmRequest.model().ifPresent(builder::model);
    llmRequest.config().ifPresent(builder::config);
    return builder.build();
  }
}
