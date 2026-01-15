package com.localagent.engine.config;

import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;

public class ModelConfig implements Config {

    public enum Provider {
        OLLAMA, LLAMA_CPP
    }

    @JSONField(name =  "base_url")
    private String baseUrl;
    private String provider;
    private String model;
    private Double temperature;
    private Double topK;
    private Double topP;
    private Double repeatPenalty;
    private Double numPredict;
    private int maxContextLength;
    private List<String> stopTokens;

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

    public Double getTopK() {
        return topK;
    }

    public void setTopK(final Double topK) {
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

    public Double getNumPredict() {
        return numPredict;
    }

    public void setNumPredict(final Double numPredict) {
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
}
