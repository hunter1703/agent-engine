package com.agentengine.agent.infra.utils;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.ResourceUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.scripts.TemplateUtils;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class PromptUtils {
  public static final String VAR_CURRENT_DATETIME = "current_datetime";
  public static final String VAR_AGENT_NAME = "agent_name";
  public static final String VAR_HAS_TOOLS = "has_tools";
  public static final String VAR_IS_RESUMABLE = "is_resumable";

  private static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, HH:mm:ss xxx", Locale.ENGLISH);

  private static final String RESPONSE_FORMAT_PREAMBLE =
      "\n\nExpected response JSON schema (STRICTLY output in json that conforms to following json schema):\n";

  private static final LazyLoader<String> COMMON_INSTRUCTIONS =
      new LazyLoader<>(() -> ResourceUtils.loadResourceAsString("common-agent-instructions.txt"));

  private PromptUtils() {}

  public static String renderSystemPrompt(
      final String template, final String agentName, final Map<String, Object> responseFormat) {
    return renderSystemPrompt(template, agentName, responseFormat, true, true);
  }

  public static String renderSystemPrompt(
      final String template,
      final String agentName,
      final Map<String, Object> responseFormat,
      final boolean hasTools) {
    return renderSystemPrompt(template, agentName, responseFormat, hasTools, hasTools);
  }

  public static String renderSystemPrompt(
      final String template,
      final String agentName,
      final Map<String, Object> responseFormat,
      final boolean hasTools,
      final boolean isResumable) {
    final Map<String, Object> context =
        Map.of(
            VAR_AGENT_NAME,
            agentName != null ? agentName : "",
            VAR_HAS_TOOLS,
            hasTools,
            VAR_IS_RESUMABLE,
            isResumable);
    final StringBuilder builder = new StringBuilder();
    final String commonInstructions = COMMON_INSTRUCTIONS.get();
    if (StringUtils.isNotBlank(commonInstructions)) {
      final String renderedCommon = TemplateUtils.renderTextTemplate(commonInstructions, context);
      if (StringUtils.isNotBlank(renderedCommon)) {
        builder.append(renderedCommon).append("\n\n");
      }
    }
    if (StringUtils.isNotBlank(template)) {
      final String renderedTemplate = TemplateUtils.renderTextTemplate(template, context);
      builder.append(renderedTemplate);
    }
    if (CollectionUtils.isNotEmpty(responseFormat)) {
      builder.append(RESPONSE_FORMAT_PREAMBLE).append(JsonUtils.toJson(responseFormat));
    }
    return builder.toString().trim();
  }
}
