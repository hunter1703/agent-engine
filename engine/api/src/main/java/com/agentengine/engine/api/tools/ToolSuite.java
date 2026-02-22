package com.agentengine.engine.api.tools;

import java.util.List;

public interface ToolSuite {
  ToolDescriptor descriptor();

  List<String> toolNames();
}
