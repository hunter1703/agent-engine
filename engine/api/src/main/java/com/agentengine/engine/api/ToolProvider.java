package com.agentengine.engine.api;

import com.google.adk.tools.BaseTool;
import com.google.genai.types.Tool;

import java.util.Map;

public interface ToolProvider {
  String agentId();

  String toolName();

  BaseTool create(Map<String, Object> toolConfig);
}
