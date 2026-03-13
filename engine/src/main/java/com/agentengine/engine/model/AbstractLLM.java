package com.agentengine.engine.model;

import com.agentengine.engine.agents.processors.Parser;
import com.google.adk.models.BaseLlm;
import java.util.Objects;

public abstract class AbstractLLM extends BaseLlm {
  protected final Parser parser;

  protected AbstractLLM(final String model, final Parser parser) {
    super(model);
    this.parser = Objects.requireNonNull(parser, "parser cannot be null");
  }
}
