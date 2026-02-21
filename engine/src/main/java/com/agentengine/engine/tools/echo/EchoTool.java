package com.agentengine.engine.tools.echo;

import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import java.util.Map;

public final class EchoTool {
  private final String defaultPrefix;

  public EchoTool(final String defaultPrefix) {
    this.defaultPrefix = defaultPrefix;
  }

  @Schema(name = "echo", description = "Echoes input text with an optional prefix.")
  public Map<String, Object> echo(
      @Schema(name = "text", description = "The text to echo") final String text,
      @Schema(name = "prefix", description = "Optional prefix to prepend", optional = true) final String prefix) {
    final String resolvedPrefix = StringUtils.isNotBlank(prefix) ? prefix : defaultPrefix;
    final String resolvedText = text == null ? "" : text;
    final String combined = (resolvedPrefix == null ? "" : resolvedPrefix) + resolvedText;
    return Map.of("output", combined);
  }
}
