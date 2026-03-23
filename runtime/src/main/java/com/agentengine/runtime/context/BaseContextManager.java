package com.agentengine.runtime.context;

import com.agentengine.runtime.plugin.ContextManager;
import com.google.genai.types.Content;
import java.util.List;
import java.util.function.UnaryOperator;

public abstract class BaseContextManager implements ContextManager {
  private final UnaryOperator<List<Content>> promptBuilder;

  public BaseContextManager(UnaryOperator<List<Content>> promptBuilder) {
    this.promptBuilder = promptBuilder;
  }

  @Override
  public List<Content> buildPrompt(final String agentId, final String sessionId, final List<Content> contents) {
    return promptBuilder.apply(contents);
  }
}
