package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import java.util.Map;

public record GuardrailContext(
    String text,
    ToolDescriptor toolDescriptor,
    Map<String, Object> toolArgs,
    InvocationContext invocationContext) {

  public GuardrailContext {
    toolArgs = CollectionUtils.nullSafeMap(toolArgs);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String text;
    private ToolDescriptor toolDescriptor;
    private Map<String, Object> toolArgs;
    private InvocationContext invocationContext;

    public Builder text(final String text) {
      this.text = text;
      return this;
    }

    public Builder toolDescriptor(final ToolDescriptor toolDescriptor) {
      this.toolDescriptor = toolDescriptor;
      return this;
    }

    public Builder toolArgs(final Map<String, Object> toolArgs) {
      this.toolArgs = toolArgs;
      return this;
    }

    public Builder invocationContext(final InvocationContext invocationContext) {
      this.invocationContext = invocationContext;
      return this;
    }

    public GuardrailContext build() {
      return new GuardrailContext(text, toolDescriptor, toolArgs, invocationContext);
    }
  }
}
