package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;

public class AgentModelConfig implements Config {
  private String modelId;
  // unique role of the model within the agent
  private String role;
  private String systemPrompt;
  private ContextManagerConfig contextManagerConfig = new LastNContextManagerConfig();
  private List<ToolsConfig> tools = new ArrayList<>();

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

  @Override
  public String getType() {
    return null;
  }

  public ContextManagerConfig getContextManagerConfig() {
    return contextManagerConfig;
  }

  public void setContextManagerConfig(final ContextManagerConfig contextManagerConfig) {
    this.contextManagerConfig = contextManagerConfig;
  }

  public List<ToolsConfig> getTools() {
    return tools;
  }

  public void setTools(final List<ToolsConfig> tools) {
    this.tools = tools;
  }

  @Override
  public void validate() {
    if (StringUtils.isBlank(modelId)) {
      throw new IllegalArgumentException("engine.reasoningModelId is required");
    }
    if (StringUtils.isBlank(systemPrompt)) {
      throw new IllegalArgumentException("systemPrompt is required");
    }
  }
}
