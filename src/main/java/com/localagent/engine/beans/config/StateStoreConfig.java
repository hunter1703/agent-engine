package com.localagent.engine.beans.config;

public abstract class StateStoreConfig implements Config {
    private String type;

    protected StateStoreConfig(StateStoreType stateStoreType) {
        this.type = stateStoreType.name().toLowerCase();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    protected enum StateStoreType {
        MEMORY,
        MONGO
    }
}
