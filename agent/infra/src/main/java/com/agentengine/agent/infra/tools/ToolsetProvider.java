package com.agentengine.agent.infra.tools;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.BaseToolset;
import java.util.Map;

/** Describes a user-facing toolset backed by an ADK {@link BaseToolset}. */
public interface ToolsetProvider {
  ToolDescriptor descriptor();

  BaseToolset create(Map<String, Object> toolConfig);
}
