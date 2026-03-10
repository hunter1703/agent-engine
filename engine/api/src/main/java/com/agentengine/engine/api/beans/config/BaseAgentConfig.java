package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.beans.NamedEntity;
import com.agentengine.util.Secure;
import com.agentengine.util.builder.annotations.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    oneOf = {DefaultAgentConfig.class, OrchestratorAgentConfig.class},
    discriminatorProperty = "type",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "default", schema = DefaultAgentConfig.class),
      @DiscriminatorMapping(value = "orchestrator", schema = OrchestratorAgentConfig.class)
    })
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = DefaultAgentConfig.class, name = "default"),
  @JsonSubTypes.Type(value = OrchestratorAgentConfig.class, name = "orchestrator")
})
@BsonDiscriminator(key = "type")
@UiGroup(step = "identity", section = "identity", order = 0)
public abstract class BaseAgentConfig extends NamedEntity implements Config {

  @UiField(label = "Agent Type", step = "identity", section = "identity", order = 20)
  @UiSelect(enumType = AgentType.class)
  private String type;

  @UiField(label = "Description", step = "identity", section = "identity", order = 40)
  @UiText
  @Secure private String description;

  @UiField(label = "Avatar", step = "identity", section = "identity", order = 50)
  @UiText
  private String avatar;

  @UiField(label = "Model ID", step = "model", section = "model", order = 10)
  @UiLookup(assetType = "model")
  private String modelId;

  @UiField(label = "System Prompt", step = "model", section = "model", order = 20)
  @UiText(multiline = true, rows = 6)
  @Secure private String systemPrompt;

  @UiField(label = "Context Strategy", step = "model", section = "context", order = 30)
  @Valid @NotNull private ContextStrategyConfig contextStrategy = new CompactionContextStrategyConfig();

  @UiField(label = "Tools", step = "model", section = "model", order = 40)
  private List<ToolsConfig> tools = new ArrayList<>();

  @UiField(label = "Sub Agents", step = "identity", section = "identity", order = 60)
  @UiLookup(assetType = "agent")
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "type", operator = UiConditionOperator.IN, values = {"orchestrator"})
  private List<String> subAgentIds = new ArrayList<>();

  @UiField(label = "Session Store", step = "runtime", section = "runtime", order = 20)
  private SessionServiceConfig sessionStore = new MongoSessionServiceConfig();

  @UiField(label = "Guardrails", step = "guardrails", section = "guardrails", order = 10)
  private GuardrailsConfig guardrails = new GuardrailsConfig();

  @UiField(label = "Runtime", step = "runtime", section = "runtime", order = 10)
  private AgentRuntimeConfig runtime = new AgentRuntimeConfig();

  protected BaseAgentConfig(final AgentType agentType) {
    this.type = agentType.name().toLowerCase(Locale.ROOT);
  }

  protected BaseAgentConfig() {
    this(AgentType.DEFAULT);
  }

  @Override
  @UiField(label = "ID", step = "identity", section = "identity", order = 0)
  @UiText
  @UiAccess(
      create = UiAccessLevel.HIDDEN,
      edit = UiAccessLevel.READ_ONLY,
      view = UiAccessLevel.READ_ONLY)
  public String getId() {
    return super.getId();
  }

  @Override
  @UiAccess(
      create = UiAccessLevel.HIDDEN,
      edit = UiAccessLevel.READ_ONLY,
      view = UiAccessLevel.READ_ONLY)
  public void setId(final String id) {
    super.setId(id);
  }

  @Override
  @UiField(label = "Name", step = "identity", section = "identity", order = 10)
  @UiText
  @Secure
  public String getName() {
    return super.getName();
  }

  @Override
  @Secure
  public void setName(final String name) {
    super.setName(name);
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(final String avatar) {
    this.avatar = avatar;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(final String modelId) {
    this.modelId = modelId;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public ContextStrategyConfig getContextStrategy() {
    return contextStrategy;
  }

  public void setContextStrategy(final ContextStrategyConfig contextStrategy) {
    this.contextStrategy =
        contextStrategy == null ? new CompactionContextStrategyConfig() : contextStrategy;
  }

  public List<ToolsConfig> getTools() {
    return tools;
  }

  public void setTools(final List<ToolsConfig> tools) {
    this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
  }

  public List<String> getSubAgentIds() {
    return subAgentIds;
  }

  public void setSubAgentIds(final List<String> subAgentIds) {
    this.subAgentIds = subAgentIds == null ? new ArrayList<>() : new ArrayList<>(subAgentIds);
  }

  public SessionServiceConfig getSessionStore() {
    return sessionStore;
  }

  public void setSessionStore(final SessionServiceConfig sessionStore) {
    this.sessionStore = sessionStore;
  }

  public GuardrailsConfig getGuardrails() {
    return guardrails;
  }

  public void setGuardrails(final GuardrailsConfig guardrails) {
    this.guardrails = guardrails;
  }

  public AgentRuntimeConfig getRuntime() {
    return runtime;
  }

  public void setRuntime(final AgentRuntimeConfig runtime) {
    this.runtime = runtime == null ? new AgentRuntimeConfig() : runtime;
  }

  public enum AgentType {
    UNKNOWN,
    DEFAULT,
    ORCHESTRATOR;

    public static AgentType valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return AgentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}
