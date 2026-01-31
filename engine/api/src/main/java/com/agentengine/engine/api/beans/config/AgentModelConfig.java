package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.utils.StringUtils;
import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType
@BsonDiscriminator(key = "type")
public class AgentModelConfig implements Config {
  private String modelId;
  // unique role of the model within the agent
  private String role;
  private String systemPrompt;
  private String type;
  private ContextManagerConfig contextManagerConfig = new LastNContextManagerConfig();
  private ToolsConfig tools = new ToolsConfig();

  public AgentModelConfig() {
    this.type = AgentType.LANGCHAIN.name().toLowerCase();
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

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public ContextManagerConfig getContextManagerConfig() {
    return contextManagerConfig;
  }

  public void setContextManagerConfig(final ContextManagerConfig contextManagerConfig) {
    this.contextManagerConfig = contextManagerConfig;
  }

  public ToolsConfig getTools() {
    return tools;
  }

  public void setTools(final ToolsConfig tools) {
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
    tools.validate();
  }

  public enum AgentType {
    LANGCHAIN
  }

}
