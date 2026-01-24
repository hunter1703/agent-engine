package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.utils.JsonUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserClarificationToolTest {

  @Test
  void executeWithContextReturnsClarificationPayload() {
    UserClarificationTool tool = new UserClarificationTool();

    ToolResult result = tool.executeWithContext(new ToolContext("session"),
        Map.of("question", "Need input", "fields", Map.of("name", "value")));

    assertThat(result.status()).isEqualTo("clarification_required");
    Map<?, ?> payload = JsonUtils.fromJson(result.output(), Map.class);
    assertThat(payload.get("question")).isEqualTo("Need input");
  }
}
