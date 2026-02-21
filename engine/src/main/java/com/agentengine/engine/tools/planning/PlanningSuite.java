package com.agentengine.engine.tools.planning;


import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;

import java.util.List;

public class PlanningSuite implements ToolSuite {
    public static final String NAME = "planning";

    @Override
    public String agentId() {
        return "ALL";
    }

    @Override
    public String suiteName() {
        return "planning";
    }

    @Override
    public List<ToolProvider> toolProviders() {
        return List.of(
            new CreatePlanToolProvider(),
            new UpdatePlanInfoToolProvider(),
            new RevisePlanToolProvider(),
            new UpdateTaskToolProvider(),
            new CompletePlanToolProvider(),
            new ViewPlanToolProvider()
        );
    }
}
