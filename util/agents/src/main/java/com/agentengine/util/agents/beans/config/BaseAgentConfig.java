package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.Secure;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.builder.annotations.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        oneOf = {DefaultAgentConfig.class, OrchestratorAgentConfig.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "DEFAULT", schema = DefaultAgentConfig.class),
            @DiscriminatorMapping(value = "ORCHESTRATOR", schema = OrchestratorAgentConfig.class)
        })
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = DefaultAgentConfig.class, name = "DEFAULT"),
    @JsonSubTypes.Type(value = OrchestratorAgentConfig.class, name = "ORCHESTRATOR")
})
@BsonDiscriminator
@UiSteps(
        steps = {
            @UiStep(id = "identity", label = "Identity", order = 0),
            @UiStep(
                    id = "model",
                    label = "Model Configuration",
                    order = 1,
                    sections = {
                        @UiSection(id = "model", label = "Model", order = 0),
                        @UiSection(id = "context", label = "Context", order = 1)
                    }),
            @UiStep(id = "guardrails", label = "Guardrails", order = 2),
            @UiStep(id = "runtime", label = "Runtime", order = 3)
        })
public abstract class BaseAgentConfig extends NamedEntity implements Config {
    public static final String FIELD_DESCRIPTION = "description";

    @UiField(label = "Agent Type", step = "identity", order = 20)
    @UiSelect(enumType = AgentType.class)
    @NotBlank
    private String type;

    @UiField(label = "Description", step = "identity", order = 40)
    @UiText
    @Secure
    private String description;

    @UiField(label = "Avatar", step = "identity", order = 50)
    @UiText
    private String avatar;

    @UiField(label = "Capabilities", step = "identity", order = 55)
    @UiText
    private List<String> capabilities = new ArrayList<>();

    @UiField(label = "Model ID", step = "model", section = "model", order = 10)
    @UiLookup(assetType = AssetClass.MODEL)
    @NotBlank
    private String modelId;

    @UiField(label = "System Prompt", step = "model", section = "model", order = 20)
    @UiText(multiline = true, rows = 6)
    @Secure
    @NotBlank
    private String systemPrompt;

    @UiField(label = "Context Strategy", step = "model", section = "context", order = 30)
    @Valid
    @NotNull
    private ContextStrategyConfig contextStrategy = new CompactionContextStrategyConfig();

    @UiField(label = "Tools", step = "model", section = "model", order = 40)
    private List<ToolsConfig> tools = new ArrayList<>();

    @UiField(label = "Sub Agents", step = "identity", order = 25)
    @UiLookup(assetType = AssetClass.AGENT)
    @UiRule(
            effect = UiRuleEffect.VISIBLE,
            field = "type",
            operator = UiConditionOperator.IN,
            values = {"ORCHESTRATOR"})
    private List<String> subAgentIds = new ArrayList<>();

    @UiField(label = "Guardrails", step = "guardrails", order = 10)
    private GuardrailsConfig guardrails = new GuardrailsConfig();

    @UiField(label = "Runtime", step = "runtime", order = 10)
    private AgentRuntimeConfig runtime = new AgentRuntimeConfig();

    @UiField(label = "Tool Execution Mode", step = "runtime", order = 20)
    @UiSelect(enumType = ToolExecutionMode.class)
    private String toolExecutionMode = ToolExecutionMode.PARALLEL.name();

    private Map<String, Object> responseFormat;

    private KnowledgeSettings knowledgeSettings;

    protected BaseAgentConfig(final AgentType agentType) {
        this.type = agentType.type();
    }

    protected BaseAgentConfig() {
        this(AgentType.DEFAULT);
    }

    @Override
    @UiField(label = "ID", step = "identity", order = 0)
    @UiText
    @UiAccess(create = UiAccessLevel.HIDDEN, edit = UiAccessLevel.READ_ONLY, view = UiAccessLevel.READ_ONLY)
    public String getId() {
        return super.getId();
    }

    @Override
    @UiAccess(create = UiAccessLevel.HIDDEN, edit = UiAccessLevel.READ_ONLY, view = UiAccessLevel.READ_ONLY)
    public void setId(final String id) {
        super.setId(id);
    }

    @Override
    @UiField(label = "Name", step = "identity", order = 10)
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

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(final List<String> capabilities) {
        this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
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
        this.contextStrategy = contextStrategy == null ? new CompactionContextStrategyConfig() : contextStrategy;
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

    public String getToolExecutionMode() {
        return toolExecutionMode;
    }

    public void setToolExecutionMode(final String toolExecutionMode) {
        this.toolExecutionMode = toolExecutionMode == null ? ToolExecutionMode.PARALLEL.name() : toolExecutionMode;
    }

    public Map<String, Object> getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(final Map<String, Object> responseFormat) {
        this.responseFormat = responseFormat;
    }

    public KnowledgeSettings getKnowledgeSettings() {
        return knowledgeSettings;
    }

    public void setKnowledgeSettings(final KnowledgeSettings knowledgeSettings) {
        this.knowledgeSettings = knowledgeSettings;
    }

    public enum AgentType {
        UNKNOWN,
        DEFAULT,
        ORCHESTRATOR;

        public String type() {
            return name();
        }

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
