package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.CreatePlanTool;
import com.google.adk.tools.BaseTool;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreatePlanSimpleToolTestDefinition {

  @Test
  void createPlanToolDoesNotRequireSessionService() {
    CreatePlanTool tool = new CreatePlanTool();
    BaseTool created = tool.create(null, Map.of());
    assertThat(created).isNotNull();
  }
}
