package com.localagent.engine.context;

import com.localagent.engine.message.Message;
import com.localagent.engine.message.Role;
import com.localagent.engine.state.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseContextBuilder implements ContextBuilder {
    protected final SessionStore sessionStore;
    private final Message systemMessage;
    private final Message protocolMessage;
    private final Message toolMessage;
    private final String thoughtTag;

    public BaseContextBuilder(SessionStore sessionStore, Message systemMessage, Message protocolMessage, Message toolMessage, String thoughtTag) {
        this.sessionStore = sessionStore;
        this.systemMessage = systemMessage;
        this.protocolMessage = protocolMessage;
        this.toolMessage = toolMessage;
        this.thoughtTag = thoughtTag;
    }

    @Override
    public List<Message> buildPrompt(String sessionId) {
        return buildPrompt(sessionStore.getMessages(sessionId));
    }

    protected List<Message> buildPrompt(List<Message> messages) {
        List<Message> base = new ArrayList<>();
        if (protocolMessage != null) {
            base.add(protocolMessage);
        }
        if (toolMessage != null) {
            base.add(toolMessage);
        }
        base.add(systemMessage);

        List<Message> history = pruneHistory(state.messages());
        if (history.isEmpty()) {
            return base;
        }
        List<Message> assembled = new ArrayList<>();
        List<Message> historyMessages = history.subList(0, Math.max(0, history.size() - 1));
        List<Message> currentMessages = history.subList(Math.max(0, history.size() - 1), history.size());
        if (!historyMessages.isEmpty()) {
            assembled.add(Message.system("Following is the conversation history:"));
            assembled.addAll(historyMessages);
        }
        if (!currentMessages.isEmpty()) {
            assembled.add(Message.system(
                "==========================================\n" +
                "Following is the current message (latest turn):"
            ));
            assembled.addAll(currentMessages);
        }
        List<Message> result = new ArrayList<>(base);
        result.addAll(assembled);
        return result;
    }

    protected List<Message> pruneHistory(List<Message> messages) {
        List<Message> pruned = new ArrayList<>();
        for (Message message : messages) {
            if (message.getRole() == Role.ASSISTANT) {
                pruned.add(Message.assistant(extractFinalOnly(message.getContent(), thoughtTag)));
            } else {
                pruned.add(message);
            }
        }
        return pruned;
    }

    static String extractFinalOnly(String text, String thoughtTag) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = stripThoughtTag(text, thoughtTag);
        if (cleaned.isBlank()) {
            return cleaned;
        }
        if (cleaned.toUpperCase().startsWith("FINAL:")) {
            return cleaned.substring("FINAL:".length()).trim();
        }
        Matcher finalMatch = Pattern.compile("^FINAL:\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
            .matcher(cleaned);
        if (finalMatch.find()) {
            return finalMatch.group(1).trim();
        }
        Matcher thoughtsMatch = Pattern.compile("^THOUGHTS:\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(cleaned);
        if (thoughtsMatch.find()) {
            String thoughtsBody = cleaned.substring(thoughtsMatch.end());
            Matcher stop = Pattern.compile("\\n\\s*\\n(?=[A-Z][A-Z\\s]*:)|\\n\\s*FINAL:\\s*", Pattern.CASE_INSENSITIVE)
                .matcher(thoughtsBody);
            if (stop.find()) {
                return (cleaned.substring(0, thoughtsMatch.start()) + cleaned.substring(thoughtsMatch.end() + stop.start())).trim();
            }
            return cleaned.substring(0, thoughtsMatch.start()).trim();
        }
        return cleaned.trim();
    }

    private static String stripThoughtTag(String text, String tag) {
        if (text == null) {
            return "";
        }
        if (tag != null && !tag.isBlank()) {
            return text.replaceAll("(?is)<" + Pattern.quote(tag) + ">.*?</" + Pattern.quote(tag) + ">", "").trim();
        }
        return text.replaceAll("</?(think|thought)>", "").trim();
    }

    protected String thoughtTag() {
        return thoughtTag;
    }
}
