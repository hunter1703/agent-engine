package com.localagent.engine.beans.config;

public abstract class ContextConfig implements Config {
    private String type;

    protected ContextConfig(final ContextType contextType) {
        this.type = contextType.name().toLowerCase();
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    protected enum ContextType {
        SUMMARIZE,
        LAST_N
    }
}
