package com.agentengine.engine.tools;

import com.agentengine.engine.api.ToolProvider;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public final class CreatePlanToolProvider implements ToolProvider {
    private final BaseSessionService sessionService;

    @Inject
    public CreatePlanToolProvider(BaseSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public String agentId() {
        return "ALL";
    }

    @Override
    public String toolName() {
        return "create_plan";
    }

    @Override
    public BaseTool create(final Map<String, Object> toolConfig) {
        return FunctionTool.create(new Planning(sessionService), "createPlan");
    }
}
