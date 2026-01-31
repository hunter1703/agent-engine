package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.ToolProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public final class RevisePlanToolProvider implements ToolProvider {
    @Override
    public String agentId() {
        return "ALL";
    }

    @Override
    public String toolName() {
        return "revise_current_plan";
    }

  @Override
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> config) {
    return FunctionTool.create(new Planning(), "reviseCurrentPlan");
  }
}
