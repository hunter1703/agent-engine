package com.localagent.cli.beans;

import com.alibaba.fastjson2.annotation.JSONField;

public class InvokeAgentRequest extends Request {
    @JSONField(name = "user_message")
    private String userMessage;

    public InvokeAgentRequest() {
        super(RequestType.INVOKE_AGENT);
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }
}
