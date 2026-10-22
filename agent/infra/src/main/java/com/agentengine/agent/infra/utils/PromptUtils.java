package com.agentengine.agent.infra.utils;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.utils.ResourceUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.scripts.TemplateUtils;
import java.util.Map;

public final class PromptUtils {
  public static final String VAR_AGENT_NAME = "agent_name";
  public static final String VAR_HAS_TOOLS = "has_tools";
  public static final String VAR_IS_RESUMABLE = "is_resumable";

  private static final LazyLoader<String> COMMON_INSTRUCTIONS =
      new LazyLoader<>(() -> ResourceUtils.loadResourceAsString("common-agent-instructions.txt"));

  private PromptUtils() {}

  public static String renderSystemPrompt(
      final String template,
      final String agentName,
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
    return builder.toString().trim();
  }
}
