package com.agentengine.engine.tools.echo;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolConstructor;
import com.agentengine.util.StringUtils;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import io.vertx.json.schema.common.dsl.Schemas;
import java.util.List;
import java.util.Map;

@AgentTool
public final class EchoTool extends Tool {
  private static final String TOOL_NAME = "echo";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, "Echoes input text with an optional prefix.", List.of(ALL), configsSchema());
  private final String prefix;

  public EchoTool() {
    this(null);
  }

  @ToolConstructor
  public EchoTool(
      @ToolSchema(name = "prefix", description = "Prefix to add to the echoed message", optional = true)
          final String prefix) {
    super(DESCRIPTOR);
    this.prefix = prefix;
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "text", description = "The text to echo") final String text,
      @ToolSchema(name = "prefix", description = "Optional prefix to prepend", optional = true)
          final String prefix) {
    final String resolvedPrefix = StringUtils.isNotBlank(prefix) ? prefix : this.prefix;
    final String resolvedText = text == null ? "" : text;
    final String combined = (resolvedPrefix == null ? "" : resolvedPrefix) + resolvedText;
    return Map.of("output", combined);
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
