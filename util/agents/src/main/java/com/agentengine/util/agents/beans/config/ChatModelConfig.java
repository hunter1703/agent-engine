package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiBoolean;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiPreset;
import com.agentengine.util.common.builder.annotations.UiRule;
import com.agentengine.util.common.builder.annotations.UiRuleEffect;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("CHAT")
@BsonDiscriminator("com.agentengine.util.agents.beans.config.ChatModelConfig")
@UiPreset(
        id = "balanced",
        label = "Balanced",
        description = "General purpose default profile.",
        isDefault = true,
        preset =
                "{\"inference\":{\"temperature\":0.7,\"topP\":0.95,\"repeatPenalty\":1.0},\"capabilities\":{\"toolCallingEnabled\":false}}")
@UiPreset(
        id = "focused",
        label = "Focused",
        description = "Lower randomness for deterministic answers.",
        preset =
                "{\"inference\":{\"temperature\":0.2,\"topP\":0.8,\"repeatPenalty\":1.1},\"capabilities\":{\"toolCallingEnabled\":true}}")
@UiPreset(
        id = "creative",
        label = "Creative",
        description = "Higher diversity and broader token exploration.",
        preset =
                "{\"inference\":{\"temperature\":1.0,\"topP\":1.0,\"repeatPenalty\":1.0},\"capabilities\":{\"toolCallingEnabled\":false}}")
public class ChatModelConfig extends ModelConfig {

    public ChatModelConfig() {
        super(ModelType.CHAT);
    }

    @UiField(label = "Model Instructions", step = "integration", order = 60)
    @UiText(multiline = true, rows = 4)
    private String instructions;

    @UiField(label = "Response Format", step = "integration", order = 70, advanced = true)
    @UiText
    private String responseFormat;

    @UiField(label = "Enable Tool Calling", step = "integration", order = 80)
    @UiBoolean
    private boolean toolCallingEnabled = false;

    @UiField(label = "Thoughts Enabled", step = "integration", order = 90, advanced = true)
    @UiBoolean
    private boolean thoughtsEnabled = true;

    @UiField(label = "Temperature", step = "sampling", order = 10)
    @UiNumber
    private Double temperature;

    @UiField(label = "Max Tokens to Generate", step = "sampling", order = 20)
    @UiNumber
    private Integer numPredict;

    @UiField(label = "Top-K", step = "sampling", order = 30)
    @UiNumber
    private Integer topK;

    @UiField(label = "Top-P", step = "sampling", order = 40)
    @UiNumber
    private Double topP;

    @UiField(label = "Repeat Penalty", step = "sampling", order = 50)
    @UiNumber
    private Double repeatPenalty;

    @UiField(label = "Max Context Length", step = "sampling", order = 60, advanced = true)
    @UiNumber
    private Integer maxContextLength;

    @UiField(label = "Stop Tokens", step = "sampling", order = 70, advanced = true)
    @UiText
    private List<String> stopTokens;

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(final String instructions) {
        this.instructions = instructions;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(final String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public boolean isToolCallingEnabled() {
        return toolCallingEnabled;
    }

    public void setToolCallingEnabled(final boolean toolCallingEnabled) {
        this.toolCallingEnabled = toolCallingEnabled;
    }

    public boolean isThoughtsEnabled() {
        return thoughtsEnabled;
    }

    public void setThoughtsEnabled(final boolean thoughtsEnabled) {
        this.thoughtsEnabled = thoughtsEnabled;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(final Double temperature) {
        this.temperature = temperature;
    }

    public Integer getNumPredict() {
        return numPredict;
    }

    public void setNumPredict(final Integer numPredict) {
        this.numPredict = numPredict;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(final Integer topK) {
        this.topK = topK;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(final Double topP) {
        this.topP = topP;
    }

    public Double getRepeatPenalty() {
        return repeatPenalty;
    }

    public void setRepeatPenalty(final Double repeatPenalty) {
        this.repeatPenalty = repeatPenalty;
    }

    public Integer getMaxContextLength() {
        return maxContextLength;
    }

    public void setMaxContextLength(final Integer maxContextLength) {
        this.maxContextLength = maxContextLength;
    }

    public List<String> getStopTokens() {
        return stopTokens;
    }

    public void setStopTokens(final List<String> stopTokens) {
        this.stopTokens = stopTokens;
    }
}
