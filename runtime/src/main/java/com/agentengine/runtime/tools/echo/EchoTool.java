package com.agentengine.runtime.tools.echo;

import com.agentengine.runtime.api.tools.ToolDescriptor;
import com.agentengine.runtime.plugin.annotations.DiscoverableTool;
import com.agentengine.runtime.plugin.annotations.ToolConstructor;
import com.agentengine.runtime.plugin.annotations.ToolSchema;
import com.agentengine.runtime.plugin.tools.Tool;
import com.agentengine.util.common.StringUtils;
import io.vertx.json.schema.common.dsl.Schemas;
import java.util.Map;

@DiscoverableTool
public final class EchoTool extends Tool {
  private static final String TOOL_NAME = "echo";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME, "Echoes input text with an optional prefix.",
      configsSchema());
  private final String prefix;

  public EchoTool() {
    this(null);
  }

  @ToolConstructor
  public EchoTool(@ToolSchema(name = "prefix", description = "Prefix to add to the echoed message", optional = true) final String prefix) {
    super(DESCRIPTOR);
    this.prefix = prefix;
  }

  public Map<String, Object> execute(@ToolSchema(name = "text", description = "The text to echo") final String text,
      @ToolSchema(name = "prefix", description = "Optional prefix to prepend", optional = true) final String prefix) {
    final String resolvedPrefix = StringUtils.isNotBlank(prefix) ? prefix : this.prefix;
    final String resolvedText = text == null ? "" : text;
    final String combined = (resolvedPrefix == null ? "" : resolvedPrefix) + resolvedText;
    return Map.of("output", combined);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> configsSchema() {
    // noinspection unchecked
    return Schemas.objectSchema()
        .requiredProperty("prefix", Schemas.stringSchema().withKeyword("description", "A prefix to add before the echoed message")).toJson()
        .mapTo(Map.class);
  }
}
