package com.agentengine.util.agents.tools;

import com.agentengine.util.agents.Constants;
import java.util.Map;

public final class ToolUtils {

  private ToolUtils() {}

  /**
   * Whether a confirmation request raised by {@code toolName} is a wait for a child session to
   * finish rather than a question for a person: only a wait carries the child's session id in its
   * payload.
   */
  public static boolean waitsOnChild(final String toolName, final Object confirmationPayload) {
    return Constants.ToolNames.isAgentRoutingTool(toolName)
        && confirmationPayload instanceof Map<?, ?> payload
        && payload.get(Constants.ToolArgs.CHILD_SESSION_ID) != null;
  }
}
