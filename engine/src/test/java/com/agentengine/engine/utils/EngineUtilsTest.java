package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.beans.session.PlanUpdate;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineUtilsTest {

  @Test
  void sanitizeMessageParsesPlanFromJson() {
    String payload = """
        {"toolCalls":[{"id":"plan-1","name":"update_plan","args":{"plan":[{"step":"echo","status":"pending"}]}}]}
        """;
    Message response = new Message(Role.ASSISTANT, payload, null, null);

    Message sanitized = EngineUtils.sanitizeMessage(response, ResponseFormatType.JSON, true, "<think>", "</think>");

    PlanUpdate update = EngineUtils.parsePlanUpdate(sanitized.getToolCalls());
    assertThat(update.plan()).hasSize(1);
    assertThat(update.plan().getFirst().step()).isEqualTo("echo");
  }

  @Test
  void sanitizeMessageParsesPlanFromText() {
    String content = "PLAN:\n- run ls\n- check pwd";
    Message response = new Message(Role.ASSISTANT, content, null, null);

    Message sanitized = EngineUtils.sanitizeMessage(response, ResponseFormatType.TEXT, false, null, null);

    PlanUpdate update = EngineUtils.parsePlanUpdate(sanitized.getToolCalls());
    assertThat(update.plan()).hasSize(2);
    assertThat(update.plan().getFirst().step()).isEqualTo("run ls");
    assertThat(update.plan().getFirst().status()).isEqualTo(PlanStatus.PENDING);
  }

  @Test
  void getRepairMessageFlagsMixedFinalAndPlan() {
    ToolCall planCall = new ToolCall("plan-1", "update_plan", Map.of("plan", List.of("step")));
    Message response = new Message(Role.ASSISTANT, "done", null, List.of(planCall));

    String repairMessage = EngineUtils.getRepairMessageIfInvalid(response);

    assertThat(repairMessage).contains("final answer");
  }

  @Test
  void parsePlanUpdateHandlesMapAndString() {
    ToolCall call = new ToolCall("plan-1", "update_plan", Map.of("plan", List.of(
        Map.of("step", "do it", "status", "in_progress"),
        "next")));

    PlanUpdate update = EngineUtils.parsePlanUpdate(List.of(call));

    assertThat(update.plan()).hasSize(2);
    assertThat(update.plan().getFirst().status()).isEqualTo(PlanStatus.IN_PROGRESS);
    assertThat(update.plan().get(1).step()).isEqualTo("next");
  }
}
