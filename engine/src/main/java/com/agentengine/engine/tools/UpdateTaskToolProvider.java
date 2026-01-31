package com.agentengine.engine.tools;

import com.agentengine.engine.api.ToolProvider;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public final class UpdateTaskToolProvider implements ToolProvider {
    private final BaseSessionService sessionService;

    @Inject
    public UpdateTaskToolProvider(BaseSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public String agentId() {
        return "ALL";
    }

    @Override
    public String toolName() {
        return "update_subtask_state";
    }

    @Override
    public BaseTool create(Map<String, Object> config) {
        return FunctionTool.create(new Planning(sessionService), "updateSubtaskState");
    }
}
