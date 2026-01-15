package com.localagent.engine.support;

import com.localagent.engine.message.Message;
import com.localagent.engine.model.LLMModel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class FakeLLMModel implements LLMModel {
    private final Deque<Message> responses = new ArrayDeque<>();

    public FakeLLMModel(List<Message> responses) {
        this.responses.addAll(responses);
    }

    @Override
    public Message generate(List<Message> messages) {
        if (responses.isEmpty()) {
            return Message.assistant("FINAL: no response configured");
        }
        return responses.removeFirst();
    }
}
