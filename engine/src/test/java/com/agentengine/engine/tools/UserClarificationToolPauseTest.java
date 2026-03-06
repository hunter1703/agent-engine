package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.utils.HitlStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.Session;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class UserClarificationToolPauseTest {

  @Test
  void pausesSessionWhenClarificationIsRequestedWithOptions() {
    final Session session =
        Session.builder("s1")
            .appName("app")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).invocationId("inv-1").build();
    final ToolContext toolContext = ToolContext.builder(context).build();

    final UserClarificationTool tool = new UserClarificationTool();
    final Map<String, Object> result =
        tool.execute(
            toolContext,
            "What environment should I target?",
            List.of("Production", "Staging", "Production", " "));

    assertThat(result).containsEntry("status", "paused");
    assertThat(result).containsEntry("clarification", "What environment should I target?");
    assertThat(result).containsEntry("options", List.of("Production", "Staging"));
    assertThat(HitlStateUtils.isPaused(context)).isTrue();
    assertThat(HitlStateUtils.getQuestion(context)).isEqualTo("What environment should I target?");
    assertThat(HitlStateUtils.getOptions(context)).containsExactly("Production", "Staging");
  }
}
