package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.engine.utils.HitlStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class GuardedToolEscalationTest {

  @Test
  void escalatesHighRiskToolToPausedState() {
    final BaseTool delegate = new BaseTool("danger", "dangerous tool", false) {
      @Override
      public Optional<FunctionDeclaration> declaration() {
        return Optional.empty();
      }

      @Override
      public Single<Map<String, Object>> runAsync(
          final Map<String, Object> args, final ToolContext toolContext) {
        return Single.just(Map.of("status", "ok"));
      }
    };

    final ToolDescriptor descriptor =
        new ToolDescriptor("danger", "dangerous tool", java.util.List.of("ALL"), Map.of(), ToolRiskLevel.HIGH);
    final AgentConfig config = new AgentConfig();
    final GuardedTool guardedTool = new GuardedTool(delegate, descriptor, config);

    final Session session =
        Session.builder("s1")
            .appName("app")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).invocationId("inv-1").build();
    final ToolContext toolContext = ToolContext.builder(context).build();

    final Map<String, Object> result = guardedTool.runAsync(Map.of(), toolContext).blockingGet();

    assertThat(result).containsEntry("status", "paused");
    assertThat(result).containsKey("clarification");
    assertThat(HitlStateUtils.isPaused(context)).isTrue();
  }
}
