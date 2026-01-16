package com.localagent.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;

public class SummarizingContextConfig extends ContextConfig {
    @JSONField(name = "trigger_threshold")
    private Double triggerThreshold;
    @JSONField(name = "recency_threshold")
    private Double recencyThreshold;
    @JSONField(name = "summarizer_model")
    private String summarizerModel;

    public SummarizingContextConfig() {
        super(ContextType.SUMMARIZE);
    }
    public Double getTriggerThreshold() {
        return triggerThreshold;
    }

    public void setTriggerThreshold(final Double triggerThreshold) {
        this.triggerThreshold = triggerThreshold;
    }

    public Double getRecencyThreshold() {
        return recencyThreshold;
    }

    public void setRecencyThreshold(final Double recencyThreshold) {
        this.recencyThreshold = recencyThreshold;
    }

    public String getSummarizerModel() {
        return summarizerModel;
    }

    public void setSummarizerModel(final String summarizerModel) {
        this.summarizerModel = summarizerModel;
    }
}
