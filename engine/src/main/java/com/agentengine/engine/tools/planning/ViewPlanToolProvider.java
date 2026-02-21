package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.tools.planning.Planning;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public final class ViewPlanToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "ALL";
  }

  @Override
  public String toolName() {
    return "view_current_plan";
  }

  @Override
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> config) {
    return FunctionTool.create(new Planning(), "viewCurrentPlan");
  }

  @Override
  public boolean isSubTool() {
    return true;
  }
}
