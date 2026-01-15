package com.localagent.cli.beans;

public class Request {

    private String id;
    private String type;

    protected Request(final RequestType type) {
        this.type = type.name();
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    protected enum RequestType {
        INVOKE_AGENT,
        BUILD_PROMPT,
        BUILD_EVENT,
        STOP_AGENT
    }
}
