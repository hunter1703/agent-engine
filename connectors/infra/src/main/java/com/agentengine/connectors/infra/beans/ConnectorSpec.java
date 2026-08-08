package com.agentengine.connectors.infra.beans;

public class ConnectorSpec {
    public enum Type {
        HTTP, UNKNOWN;

        public static Type valueOfOrUnknown(final String type) {
            try {
                return Type.valueOf(type);
            } catch (final IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    private String type;

    public ConnectorSpec() {}

    public ConnectorSpec(ConnectorSpec.Type type) {
        this.type = type.name();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
