package com.agentengine.interfaces.rest.models;

import java.util.List;

public class ListAgentsResponse {

    private String object = "list";
    private List<AgentInfo> data;

    public ListAgentsResponse() {
    }

    public ListAgentsResponse(List<AgentInfo> data) {
        this.data = data;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<AgentInfo> getData() {
        return data;
    }

    public void setData(List<AgentInfo> data) {
        this.data = data;
    }
}
