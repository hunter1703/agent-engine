package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool that can be executed by an agent.
 */
public interface Tool {
  Logger LOG = LoggerFactory.getLogger(Tool.class);

  String name();

  String description();

  String execute(Map<String, Object> args);

  default Map<String, Object> parametersSchema() {
    return Map.of();
  }

  /**
   * Execute the tool with context information. Override this method to access
   * session and agent information.
   */
  default ToolResult executeWithContext(final ToolContext context, final Map<String, Object> args) {
    try {
      final String output = execute(args);
      return ToolResult.ok(output);
    } catch (Exception e) {
      LOG.warn("Tool execution failed: {}", name(), e);
      return ToolResult.error(e.getMessage());
    }
  }

  default boolean isMutating() {
    return false;
  }

  default boolean supportsParallel() {
    return false;
  }

  default boolean retry(final String status, final String output) {
    return false;
  }
}
