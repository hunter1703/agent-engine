package com.localagent.engine.config;

import com.alibaba.fastjson2.annotation.JSONField;

public final class DebugConfig implements Config {
    @JSONField(name = "llm_kwargs")
    private Boolean llmKwargs;
    @JSONField(name = "prompt_traces")
    private Boolean promptTraces;

    public Boolean getLlmKwargs() {
        return llmKwargs;
    }

    public void setLlmKwargs(Boolean llmKwargs) {
        this.llmKwargs = llmKwargs;
    }

    public Boolean getPromptTraces() {
        return promptTraces;
    }

    public void setPromptTraces(Boolean promptTraces) {
        this.promptTraces = promptTraces;
    }
}
