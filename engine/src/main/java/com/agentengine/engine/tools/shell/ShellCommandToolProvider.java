package com.agentengine.engine.tools.shell;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import io.vertx.json.schema.common.dsl.Schemas;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Map;

@Singleton
public class ShellCommandToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "ALL";
  }

  @Override
  public String name() {
    return "run_cmd";
  }

  @Override
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> toolConfig) {
    final Long timeoutSeconds = parseTimeoutSeconds(toolConfig);
    final Duration timeout = Duration.ofSeconds(timeoutSeconds == null ? 30 : timeoutSeconds);
    return FunctionTool.create(new ShellCommandTool(timeout), "command");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> configsSchema() {
    //noinspection unchecked
    return Schemas.objectSchema()
        .property(
            "timeout_seconds",
            Schemas.intSchema()
                .withKeyword(
                    "description",
                    "Optional timeout in seconds for the shell command execution. Defaults to 30 seconds if not provided.")
                .withKeyword("default", 30))
        .requiredProperty(
            "command",
            Schemas.stringSchema().withKeyword("description", "The shell command to execute."))
        .toJson()
        .mapTo(Map.class);
  }

  private static Long parseTimeoutSeconds(final Map<String, Object> toolConfig) {
    if (toolConfig == null || toolConfig.isEmpty()) {
      return null;
    }
    final Object value = toolConfig.get("timeout_seconds");
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Long.parseLong(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
