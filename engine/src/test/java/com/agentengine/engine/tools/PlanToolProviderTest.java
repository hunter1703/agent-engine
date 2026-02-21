package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.CreatePlanToolProvider;
import com.google.adk.tools.BaseTool;
import org.junit.jupiter.api.Test;

class PlanToolProviderTest {

  @Test
  void createPlanToolDoesNotRequireSessionService() {
    CreatePlanToolProvider provider = new CreatePlanToolProvider();
    BaseTool created = provider.create(null, null);
    assertThat(created).isNotNull();
  }
}
