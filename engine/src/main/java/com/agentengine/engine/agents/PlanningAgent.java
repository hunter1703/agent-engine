package com.agentengine.engine.agents;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.MessageStoreMark;
import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import java.util.Map;

public final class PlanningAgent extends AbstractSingleModelAgent {

  public static final String NAME = "tasks";
  public static final String DEFAULT_SYSTEM_PROMPT = """
      You produce Task List artifacts that track an agent's progress on complex work.
      Write a Markdown list of items covering research, implementation, verification,
      and related action items. Keep it concise, checkbox-based, and suitable as a
      live snapshot of the agent's progress that the user may not need to edit.
      """;

  private static final String DESCRIPTION = """
      Generate a Task List artifact: a concise Markdown checklist of research,
      implementation, verification, and related action items used to track progress.
      Treat it as a live snapshot of work rather than a document the user must edit.
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
