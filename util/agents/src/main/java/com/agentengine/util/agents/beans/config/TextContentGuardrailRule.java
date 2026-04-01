package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("TEXT_CONTENT")
@BsonDiscriminator(value = "TEXT_CONTENT")
public class TextContentGuardrailRule extends GuardrailRule {
    @UiField(label = "Max Text Length", order = 10)
    @UiNumber
    private Integer maxTextLength;

    @UiField(label = "Blocked Patterns", order = 20)
    @UiText(multiline = true, rows = 4)
    private List<String> blockedPatterns = new ArrayList<>();

    public TextContentGuardrailRule() {
        super(GuardrailRuleType.TEXT_CONTENT);
        setStage(GuardrailStage.INPUT.name());
    }

    public Integer getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(final Integer maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    public List<String> getBlockedPatterns() {
        return blockedPatterns;
    }

    public void setBlockedPatterns(final List<String> blockedPatterns) {
        this.blockedPatterns = blockedPatterns == null ? new ArrayList<>() : new ArrayList<>(blockedPatterns);
    }
}
