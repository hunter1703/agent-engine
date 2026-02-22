package com.agentengine.engine.tools.echo;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import io.vertx.json.schema.common.dsl.Schemas;

import java.util.List;
import java.util.Map;

public final class EchoTool implements Tool {
  private static final String TOOL_NAME = "echo";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME, List.of(ALL), configsSchema());
  private final String prefix;

  public EchoTool() {
    this(null);
  }

  public EchoTool(final String prefix) {
    this.prefix = prefix;
  }

  @Schema(name = "echo", description = "Echoes input text with an optional prefix.")
  public Map<String, Object> execute(
      @Schema(name = "text", description = "The text to echo") final String text,
      @Schema(name = "prefix", description = "Optional prefix to prepend", optional = true)
          final String prefix) {
    final String resolvedPrefix = StringUtils.isNotBlank(prefix) ? prefix : this.prefix;
    final String resolvedText = text == null ? "" : text;
    final String combined = (resolvedPrefix == null ? "" : resolvedPrefix) + resolvedText;
    return Map.of("output", combined);
  }

  @Override
  public ToolDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> configsSchema() {
    //noinspection unchecked
    return Schemas.objectSchema()
            .requiredProperty(
                    "prefix",
                    Schemas.stringSchema()
                            .withKeyword("description", "A prefix to add before the echoed message"))
            .toJson()
            .mapTo(Map.class);
  }
}
