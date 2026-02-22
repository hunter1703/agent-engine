package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.annotations.SimpleTool;
import com.agentengine.engine.api.utils.StringUtils;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class SimpleToolProvider implements ToolProvider {
  private final Instance<Tool> tools;
  private final Map<String, Class<? extends Tool>> toolTypes;
  private final List<ToolDescriptor> descriptors;

  @Inject
  public SimpleToolProvider(final @Any Instance<Tool> tools) {
    this.tools = tools;
    final Map<String, Class<? extends Tool>> resolvedTools = new HashMap<>();
    final List<ToolDescriptor> resolvedDescriptors = new ArrayList<>();
    for (final Tool tool : tools) {
      try {
        if (!tool.getClass().isAnnotationPresent(SimpleTool.class)) {
          continue;
        }
        final ToolDescriptor descriptor = tool.descriptor();
        if (resolvedTools.putIfAbsent(descriptor.name(), tool.getClass()) == null) {
          resolvedDescriptors.add(descriptor);
        }
      } finally {
        tools.destroy(tool);
      }
    }
    this.toolTypes = Map.copyOf(resolvedTools);
    this.descriptors = List.copyOf(resolvedDescriptors);
  }

  @Override
  public List<ToolDescriptor> tools() {
    return descriptors;
  }

  @Override
  public Tool create(
      final AgentContext agentContext,
      final String toolName,
      final Map<String, Object> toolConfig) {
    if (StringUtils.isBlank(toolName)) {
      return null;
    }
    final Class<? extends Tool> toolType = toolTypes.get(toolName);
    if (toolType == null) {
      return null;
    }
    return tools.select(toolType).get();
  }
}
