package com.agentengine.engine.agents;

import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.MessageStoreMark;
import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import java.util.Map;

public final class PlanningAgent extends AbstractSingleModelAgent {

  public static final String NAME = "tasks";
  public static final String DEFAULT_SYSTEM_PROMPT = """
      You manage a task plan for complex work.
      Output a Markdown list where each line uses:
      - [status] (id:step-1) task description

      Status must be one of: pending, in_progress, completed.

      Input fields (when provided in tool args):
      - mode: "initial" or "update"
      - current_plan: the existing plan items with status
      - active_step: the step currently being worked on

      Rules:
      - When mode=initial, produce a full ordered plan with stable ids.
      - When mode=update, update statuses, adjust wording, and reorder only non-active items.
      - Keep the active step first; do not remove it unless it is completed.
      - Reuse ids from current_plan for existing items; only mint new ids for new items.
      - If no changes are needed, return an empty response.
      - Keep steps concise, concrete, and tool-executable.
      """;

  private static final String DESCRIPTION = """
      Generate or update a task plan as a Markdown list with explicit statuses.
      """;

  public PlanningAgent(final LLMModel model) {
    super(NAME, DESCRIPTION, model);
  }

  @Override
  public ToolResult executeWithContext(final ToolContext context, final Map<String, Object> args) {
    final String sessionId = context == null ? "tool" : context.sessionId();
    final MessageStoreMark mark = model().getContextManager().mark(sessionId);
    try {
      return super.executeWithContext(context, args);
    } finally {
      model().getContextManager().reset(sessionId, mark);
    }
  }
}
