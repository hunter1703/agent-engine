package com.localagent.engine.config;

public abstract class ContextConfig implements Config {
    private String type;

    protected ContextConfig(ContextType contextType) {
        this.type = contextType.name().toLowerCase();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    protected enum ContextType {
        SUMMARIZE,
        LAST_N
    }
}
