package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

import com.agentengine.engine.api.beans.Secure;

public class AgentModelConfig implements Config {
  @NotBlank private String modelId;
  // unique role of the model within the agent
  private String role;
  @Secure private String systemPrompt;
  private ContextManagerConfig contextManagerConfig = new SummarizeContextManagerConfig();
  private List<ToolsConfig> tools = new ArrayList<>();

  public AgentModelConfig() {}

  public AgentModelConfig(final String modelId) {
    this.modelId = modelId;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(final String modelId) {
    this.modelId = modelId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(final String role) {
    this.role = role;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  @JsonIgnore
  @Override
  public String getType() {
    return null;
  }

  public ContextManagerConfig getContextManagerConfig() {
    return contextManagerConfig;
  }

  public void setContextManagerConfig(final ContextManagerConfig contextManagerConfig) {
    this.contextManagerConfig =
        contextManagerConfig == null ? new SummarizeContextManagerConfig() : contextManagerConfig;
  }

  public List<ToolsConfig> getTools() {
    return tools;
  }

  public void setTools(final List<ToolsConfig> tools) {
    this.tools = tools;
  }
}
