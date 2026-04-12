package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiAccess;
import com.agentengine.util.common.builder.annotations.UiAccessLevel;
import com.agentengine.util.common.builder.annotations.UiBoolean;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContentGuardrailRule.class, name = "TEXT_CONTENT"),
    @JsonSubTypes.Type(value = OutputRelevanceGuardrailRule.class, name = "RELEVANCE")
})
@BsonDiscriminator
public abstract class GuardrailRule {
    private static final String DEFAULT_STAGE = GuardrailStage.UNKNOWN.name();
    private static final String DEFAULT_ACTION = GuardrailAction.WARN.name();

    @UiField(label = "Rule ID", order = 10)
    @UiText
    @UiAccess(create = UiAccessLevel.EDITABLE, edit = UiAccessLevel.EDITABLE, view = UiAccessLevel.READ_ONLY)
    @NotBlank
    private String id;

    @UiField(label = "Rule Type", order = 20)
    @UiSelect(enumType = GuardrailRuleType.class)
    @NotBlank
    private String type;

    @UiField(label = "Stage", order = 30, advanced = true)
    @UiSelect(enumType = GuardrailStage.class)
    private String stage = DEFAULT_STAGE;

    @UiField(label = "Enabled", order = 40)
    @UiBoolean
    private boolean enabled = true;

    @UiField(label = "Action", order = 50)
    @UiSelect(enumType = GuardrailAction.class)
    private String action = DEFAULT_ACTION;

    @UiField(label = "Message", order = 60)
    @UiText(multiline = true, rows = 3)
    private String message;

    protected GuardrailRule(final GuardrailRuleType type) {
        this.type = type.name();
    }

    protected GuardrailRule() {}

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(final String stage) {
        this.stage = stage == null || stage.isBlank() ? DEFAULT_STAGE : stage;
    }

    public GuardrailStage stageEnum() {
        return GuardrailStage.valueOfOrDefault(stage);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getAction() {
        return action;
    }

    public void setAction(final String action) {
        this.action = action == null || action.isBlank() ? DEFAULT_ACTION : action;
    }

    public GuardrailAction actionEnum() {
        return GuardrailAction.valueOfOrDefault(action);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }
}
