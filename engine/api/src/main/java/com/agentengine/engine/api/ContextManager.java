package com.agentengine.engine.api;

import com.google.genai.types.Content;
import java.util.List;
import java.util.function.UnaryOperator;

public interface ContextManager {

  UnaryOperator<List<Content>> getPromptBuilder();

  default List<Content> buildPrompt(
      final String agentId, final String sessionId, final List<Content> contents) {
    return getPromptBuilder().apply(contents);
  }
}
