package com.localagent.engine.context;

import com.localagent.engine.message.Message;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.tools.AgentTool;

import java.util.List;

public final class LastNContextBuilder extends BaseContextBuilder {
    private final int keepLast;

    public LastNContextBuilder(SessionStore sessionStore, Message systemMessage, Message protocolMessage, List<AgentTool> tools, String thoughtTag, int keepLast
    ) {
        super(sessionStore, systemMessage, protocolMessage, tools, thoughtTag);
        this.keepLast = Math.max(1, keepLast);
    }

    @Override
    public List<Message> buildPrompt(String sessionId) {
        final List<Message> messages = sessionStore.getMessages(sessionId);
        if (messages.size() <= keepLast) {
            return super.buildPrompt(messages);
        }
        List<Message> recent = messages.subList(messages.size() - keepLast, messages.size());
        return super.buildPrompt(recent);
    }

}
