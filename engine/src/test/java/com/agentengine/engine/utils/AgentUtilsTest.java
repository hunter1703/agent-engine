package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.PlanItem;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.beans.session.PlanUpdate;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentUtilsTest {

  @Test
  void getRepairMessageFlagsMixedFinalAndPlan() {
    Content response = Content.builder().role("model").parts(
        Part.builder().text("done").build(),
        Part.builder().functionCall(FunctionCall.builder().id("plan-1").name("update_plan")
            .args(Map.of("plan", List.of(Map.of("step", "step", "status", "pending")))).build()).build())
        .build();

    String repairMessage = AgentUtils.getRepairMessageIfInvalid(response);

    assertThat(repairMessage).contains("final answer");
  }

  @Test
  void parsePlanUpdateHandlesMapItems() {
    ToolCall call = new ToolCall("plan-1", "update_plan", Map.of("plan",
        List.of(Map.of("step", "do it", "status", "in_progress"), Map.of("step", "next", "status", "pending"))));

    PlanUpdate update = AgentUtils.parsePlanUpdate(List.of(call));

    assertThat(update.plan()).hasSize(2);
    assertThat(update.plan().getFirst().status()).isEqualTo(PlanStatus.IN_PROGRESS);
    assertThat(update.plan().get(1).step()).isEqualTo("next");
  }

  @Test
  void parsePlanItemsFromTextHandlesMarkdownList() {
    String output = "- first step\n- second step";

    List<PlanItem> items = AgentUtils.parsePlanItemsFromText(output);

    assertThat(items).hasSize(2);
    assertThat(items.getFirst().step()).isEqualTo("first step");
    assertThat(items.getFirst().id()).isNotBlank();
  }
}
