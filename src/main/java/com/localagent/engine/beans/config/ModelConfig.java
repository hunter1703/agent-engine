package com.localagent.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Map;

public class ModelConfig implements Config {

    public enum Provider {
        OLLAMA, LLAMA_CPP, OPEN_AI
    }

    @JSONField(name =  "base_url")
    private String baseUrl;
    private String provider;
    private String model;
    private Double temperature;
    private Integer topK;
    private Double topP;
    private Double repeatPenalty;
    private Integer numPredict;
    private int maxContextLength;
    private List<String> stopTokens;
    private String responseFormat;
    private Map<String, Object> responseJsonSchema;
    private String thoughtsStartTag;
    private String thoughtsEndTag;
    private boolean thoughtsEnabled;
    private ContextConfig contextConfig;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(final String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(final String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(final Double temperature) {
        this.temperature = temperature;
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

    public Integer getNumPredict() {
        return numPredict;
    }

    public void setNumPredict(final Integer numPredict) {
        this.numPredict = numPredict;
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }

    public void setMaxContextLength(final int maxContextLength) {
        this.maxContextLength = maxContextLength;
    }

    public List<String> getStopTokens() {
        return stopTokens;
    }

    public void setStopTokens(final List<String> stopTokens) {
        this.stopTokens = stopTokens;
    }

    public String getThoughtsStartTag() {
        return thoughtsStartTag;
    }

    public void setThoughtsStartTag(final String thoughtsStartTag) {
        this.thoughtsStartTag = thoughtsStartTag;
    }

    public String getThoughtsEndTag() {
        return thoughtsEndTag;
    }

    public void setThoughtsEndTag(final String thoughtsEndTag) {
        this.thoughtsEndTag = thoughtsEndTag;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(final String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Map<String, Object> getResponseJsonSchema() {
        return responseJsonSchema;
    }

    public void setResponseJsonSchema(final Map<String, Object> responseJsonSchema) {
        this.responseJsonSchema = responseJsonSchema;
    }

    public boolean isThoughtsEnabled() {
        return thoughtsEnabled;
    }

    public void setThoughtsEnabled(final boolean thoughtsEnabled) {
        this.thoughtsEnabled = thoughtsEnabled;
    }

    public ContextConfig getContextConfig() {
        return contextConfig;
    }

    public void setContextConfig(final ContextConfig contextConfig) {
        this.contextConfig = contextConfig;
    }
}
