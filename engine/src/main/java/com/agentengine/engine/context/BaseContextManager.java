package com.agentengine.engine.context;

import com.agentengine.engine.api.ContextManager;
import com.google.genai.types.Content;
import java.util.List;
import java.util.function.UnaryOperator;

public class BaseContextManager implements ContextManager {
  private final UnaryOperator<List<Content>> promptBuilder;

  public BaseContextManager(UnaryOperator<List<Content>> promptBuilder) {
    this.promptBuilder = promptBuilder;
  }

  @Override
  public UnaryOperator<List<Content>> getPromptBuilder() {
    return promptBuilder;
  }
}
