package com.agentengine.util.common;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class PromptUtils {

    public static final String AGENT_NAME = "agent_name";
    public static final String CURRENT_DATETIME = "current_datetime";

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy HH:mm 'UTC'");

    private PromptUtils() {}

    public static String renderSystemPrompt(final String template, final String agentName) {
        final Map<String, Object> context = Map.of(
                AGENT_NAME, agentName,
                CURRENT_DATETIME, LocalDateTime.now(ZoneOffset.UTC).format(DATETIME_FORMATTER)
        );
        return TemplateUtils.renderTextTemplate(template, context);
    }
}
