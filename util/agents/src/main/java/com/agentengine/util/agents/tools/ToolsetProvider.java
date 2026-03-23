package com.agentengine.util.agents.tools;

import com.google.adk.tools.BaseToolset;
import java.util.List;
import java.util.Map;

/** Describes a user-facing toolset backed by an ADK {@link BaseToolset}. */
public interface ToolsetProvider {
  ToolDescriptor descriptor();

  List<ToolDescriptor> memberDescriptors();

  BaseToolset create(Map<String, Object> toolConfig);
}
