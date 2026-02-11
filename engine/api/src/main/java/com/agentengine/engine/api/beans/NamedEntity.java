package com.agentengine.engine.api.beans;

public class NamedEntity extends BaseEntity {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
