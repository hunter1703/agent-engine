package com.localagent.engine.model;

import com.localagent.engine.beans.config.ModelConfig;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LangChain4JLLMModel implements LLMModel {
    private final ChatModel model;
    private final ResponseFormat responseFormat;
    private final boolean thoughtsEnabled;
    private final String thoughtsStartTag;
    private final String thoughtsEndTag;


    public LangChain4JLLMModel(ChatModel model, ResponseFormat responseFormat, boolean thoughtsEnabled, String thoughtsStartTag, String thoughtsEndTag) {
        this.model = model;
        this.responseFormat = responseFormat;
        this.thoughtsEnabled = thoughtsEnabled;
        this.thoughtsStartTag = thoughtsStartTag;
        this.thoughtsEndTag = thoughtsEndTag;
    }

    @Override
    public Message generate(List<Message> messages) {
        List<ChatMessage> prompt = new ArrayList<>();
        for (Message message : messages) {
            prompt.add(toChatMessage(message));
        }
        ChatResponse response = model.chat(prompt);
        final AiMessage aiMessage = response.aiMessage();
        return Message.assistant(aiMessage.text(), aiMessage.thinking());
    }

    @Override
    public ResponseFormat responseFormat() {
        return responseFormat;
    }

    @Override
    public boolean thoughtsEnabled() {
        return thoughtsEnabled;
    }

    @Override
    public String thoughtsStartTag() {
        return thoughtsStartTag;
    }

    @Override
    public String thoughtsEndTag() {
        return thoughtsEndTag;
    }

    private ChatMessage toChatMessage(Message message) {
        if (message.getRole() == Role.SYSTEM) {
            return SystemMessage.from(message.getContent());
        }
        if (message.getRole() == Role.USER) {
            return UserMessage.from(message.getContent());
        }
        return AiMessage.from(message.getContent());
    }
}
