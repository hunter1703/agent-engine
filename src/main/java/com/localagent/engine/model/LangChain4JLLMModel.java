package com.localagent.engine.model;

import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LangChain4JLLMModel implements LLMModel {
    private final ChatModel model;

    public LangChain4JLLMModel(ChatModel model) {
        this.model = model;
    }

    @Override
    public Message generate(List<Message> messages) {
        List<ChatMessage> prompt = new ArrayList<>();
        for (Message message : messages) {
            prompt.add(toChatMessage(message));
        }
        AiMessage response = model.generate(prompt);
        return Message.assistant(response.text());
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

    private Message maybeStripThoughts(Message response) {
        if (thoughtsEnabled) {
            return response;
        }
        String cleaned = stripThoughtTags(response.getContent());
        if (!cleaned.equals(response.getContent())) {
            return Message.assistant(cleaned);
        }
        return response;
    }

    private String stripThoughtTags(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?is)</?" + Pattern.quote(thoughtTag) + ">", "").trim();
    }

    public String[] splitResponse(String text) {
        if (text == null || text.isBlank()) {
            return new String[] {"", ""};
        }
        String cleaned = text.replaceAll("(?is)<" + Pattern.quote(thoughtTag) + ">.*?</" + Pattern.quote(thoughtTag) + ">", "");
        Matcher finalMatch = FINAL_BLOCK.matcher(cleaned);
        String finalText = finalMatch.find() ? finalMatch.group(1).trim() : cleaned.trim();
        Matcher thoughtMatch = Pattern.compile("(?is)<" + Pattern.quote(thoughtTag) + ">(.*?)</" + Pattern.quote(thoughtTag) + ">")
                .matcher(text);
        String thoughts = thoughtMatch.find() ? thoughtMatch.group(1).trim() : "";
        if (!thoughtsEnabled) {
            return new String[] {"", finalText};
        }
        return new String[] {thoughts, finalText};
    }
}
