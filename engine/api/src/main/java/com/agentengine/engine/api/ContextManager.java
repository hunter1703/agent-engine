package com.agentengine.engine.api;

import com.google.genai.types.Content;
import java.util.List;
import java.util.function.UnaryOperator;

public interface ContextManager {

  UnaryOperator<List<Content>> getPromptBuilder();
}
