package com.agentengine.util.common;

import com.hubspot.jinjava.Jinjava;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class PromptUtils {
  public static final String VAR_CURRENT_DATETIME = "current_datetime";
  public static final String VAR_AGENT_NAME = "agent_name";

  private static final Jinjava JINJAVA = new Jinjava();
  private static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, HH:mm:ss xxx", Locale.ENGLISH);

  private static final String RESPONSE_FORMAT_PREAMBLE =
      "\n\nExpected response JSON schema (STRICTLY output in json that conforms to following json schema):\n";

  private PromptUtils() {}

  public static String renderSystemPrompt(
      final String template, final String agentName, final Map<String, Object> responseFormat) {
    final ZonedDateTime now = ZonedDateTime.now();
    final Map<String, Object> context =
        Map.of(
            VAR_CURRENT_DATETIME,
            now.format(DATETIME_FORMATTER),
            VAR_AGENT_NAME,
            agentName != null ? agentName : "");
    final String rendered = template == null ? "" : JINJAVA.render(template, context).trim();
    if (CollectionUtils.isEmpty(responseFormat)) {
      return rendered;
    }
    return rendered + RESPONSE_FORMAT_PREAMBLE + JsonUtils.toJson(responseFormat);
  }
}
