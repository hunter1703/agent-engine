package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.PlanItem;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineUtilsTest {

  @Test
  void sanitizeMessageParsesPlanFromJson() {
    String payload = """
        {"toolCalls":[{"id":"plan-1","name":"plan_items","args":{"items":[{"id":"step-1","description":"echo"}]}}]}
        """;
    Message response = new Message(Role.ASSISTANT, payload, null, null);

    Message sanitized = EngineUtils.sanitizeMessage(response,
        ResponseFormat.builder().type(ResponseFormatType.JSON).build(), true, "<think>", "</think>");

    assertThat(sanitized.getContent()).isEmpty();
    assertThat(sanitized.getToolCalls()).hasSize(1);
    List<PlanItem> planItems = EngineUtils.parsePlanItems(sanitized.getToolCalls());
    assertThat(planItems).hasSize(1);
    assertThat(planItems.getFirst().description()).isEqualTo("echo");
  }

  @Test
  void sanitizeMessageParsesPlanFromText() {
    String content = "PLAN:\n- run ls\n- check pwd";
    Message response = new Message(Role.ASSISTANT, content, null, null);

    Message sanitized = EngineUtils.sanitizeMessage(response,
        ResponseFormat.builder().type(ResponseFormatType.TEXT).build(), false, null, null);

    List<PlanItem> planItems = EngineUtils.parsePlanItems(sanitized.getToolCalls());
    assertThat(planItems).hasSize(2);
    assertThat(planItems.getFirst().description()).isEqualTo("run ls");
  }

  @Test
  void getRepairMessageFlagsMixedFinalAndPlan() {
    ToolCall planCall = new ToolCall("plan-1", "plan_items", Map.of("items", List.of("step")));
    Message response = new Message(Role.ASSISTANT, "done", null, List.of(planCall));

    String repairMessage = EngineUtils.getRepairMessageIfInvalid(response);

    assertThat(repairMessage).contains("final answer");
  }

  @Test
  void parsePlanItemsHandlesMapAndString() {
    ToolCall call = new ToolCall("plan-1", "plan_items", Map.of("items", List.of(
        Map.of("id", "step-1", "description", "do it"),
        "next")));

    List<PlanItem> items = EngineUtils.parsePlanItems(List.of(call));

    assertThat(items).hasSize(2);
    assertThat(items.getFirst().description()).isEqualTo("do it");
    assertThat(items.get(1).description()).isEqualTo("next");
  }
}
