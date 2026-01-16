package com.localagent.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;

public final class EngineConfig implements Config {
    @JSONField(name = "invocation_limit")
    private Integer invocationLimit;
    @JSONField(name = "tool_retry_limit")
    private Integer toolRetryLimit = 2;
    private String prompt;
    private String reasoning;
    private String tool;
    private String router;
    private String heavy;

    public Integer getInvocationLimit() {
        return invocationLimit;
    }

    public void setInvocationLimit(Integer invocationLimit) {
        this.invocationLimit = invocationLimit;
    }

    public Integer getToolRetryLimit() {
        return toolRetryLimit;
    }

    public void setToolRetryLimit(Integer toolRetryLimit) {
        this.toolRetryLimit = toolRetryLimit;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(final String prompt) {
        this.prompt = prompt;
    }

    @Override
    public void validate() {
        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException("engine.reasoning is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("engine.prompt is required");
        }
        if (router != null && !router.isBlank() && (tool == null || tool.isBlank()) && (heavy == null || heavy.isBlank())) {
            throw new IllegalArgumentException("engine.router requires engine.tool or engine.heavy");
        }
        if (heavy != null && !heavy.isBlank() && (router == null || router.isBlank())) {
            throw new IllegalArgumentException("engine.heavy requires engine.router");
        }
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getRouter() {
        return router;
    }

    public void setRouter(String router) {
        this.router = router;
    }

    public String getHeavy() {
        return heavy;
    }

    public void setHeavy(String heavy) {
        this.heavy = heavy;
    }
}
