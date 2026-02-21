package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public final class CreatePlanToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "ALL";
  }

  @Override
  public String name() {
    return "create_plan";
  }

  @Override
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> toolConfig) {
    return FunctionTool.create(new Planning(), "createPlan");
  }

  @Override
  public boolean isSubTool() {
    return true;
  }
}
