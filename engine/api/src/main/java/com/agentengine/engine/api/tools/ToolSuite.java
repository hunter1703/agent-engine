package com.agentengine.engine.api.tools;

import java.util.List;

/**
 * Describes a user-facing tool suite that expands to multiple tool names at runtime.
 */
public interface ToolSuite {
  ToolDescriptor descriptor();

  List<String> toolNames();
}
