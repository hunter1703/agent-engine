package com.agentengine.engine.api.beans;

public abstract class BaseEntity {
    private String id;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }
}
