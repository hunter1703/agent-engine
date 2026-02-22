package com.agentengine.engine.tools.echo;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.shell.ShellCommandTool;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Singleton
public final class EchoToolProvider implements ToolProvider {

  @Override
  public List<ToolDescriptor> tools() {
    return List.of(ShellCommandTool.DESCRIPTOR);
  }

  @Override
  public Tool create(final AgentContext agentContext, final String toolName, final Map<String, Object> toolConfig) {
    final String prefix = CollectionUtils.getStringValueFromMap(toolConfig, "prefix");
    return new EchoTool(prefix);
  }
}
