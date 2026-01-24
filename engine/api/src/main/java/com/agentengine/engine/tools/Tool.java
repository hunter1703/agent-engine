package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import java.util.Map;

/**
 * Tool that can be executed by an agent.
 */
public interface Tool {
  String name();

  String description();

  String execute(Map<String, Object> args);

  /**
   * Execute the tool with context information. Override this method to access
   * session and agent information.
   */
  default ToolResult executeWithContext(final ToolContext context, final Map<String, Object> args) {
    try {
      final String output = execute(args);
      return ToolResult.ok(output);
    } catch (Exception e) {
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
