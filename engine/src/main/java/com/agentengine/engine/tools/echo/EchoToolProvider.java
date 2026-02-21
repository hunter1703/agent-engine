package com.agentengine.engine.tools.echo;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import io.vertx.json.schema.common.dsl.Schemas;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public final class EchoToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "ALL";
  }

  @Override
  public String name() {
    return "echo";
  }

  @Override
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> toolConfig) {
    final String prefix = CollectionUtils.getStringValueFromMap(toolConfig, "prefix");
    return FunctionTool.create(new EchoTool(prefix), "echo");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> configsSchema() {
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
